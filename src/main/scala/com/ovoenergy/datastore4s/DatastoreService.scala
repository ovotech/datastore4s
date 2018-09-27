package com.ovoenergy.datastore4s

import scala.collection.JavaConverters._
import com.google.cloud.{NoCredentials, ServiceOptions}
import com.google.cloud.datastore.{DatastoreOptions, Entity => DsEntity, ProjectionEntity => DsProjectionEntity, _}
import com.google.cloud.datastore.Query.{newEntityQueryBuilder, newProjectionEntityQueryBuilder}
import com.ovoenergy.datastore4s.DatastoreError.SuppressedStackTrace

import scala.util.{Failure, Success, Try}

final case class Persisted[A](inputObject: A, entity: Entity)

object DatastoreService extends DatastoreErrors {

  /** Allows overriding of configuration to connect to an emulator for testing purposes */
  private def handleEmulatorHost(builder: DatastoreOptions.Builder)(emulatorHost: String): DatastoreOptions.Builder =
    builder.setHost(emulatorHost).setCredentials(NoCredentials.getInstance()).setRetrySettings(ServiceOptions.getNoRetrySettings)

  def apply(dataStoreConfiguration: DatastoreConfiguration): DatastoreService = dataStoreConfiguration match {
    case ManualDatastoreConfiguration(projectId, namespace) =>
      val withProjectId = DatastoreOptions.newBuilder().setProjectId(projectId)
      val withNamespace = namespace.fold(withProjectId)(ns => withProjectId.setNamespace(ns))
      val withEmulator = emulatorVariable().fold(withNamespace)(handleEmulatorHost(withNamespace))
      new WrappedDatastore(withEmulator.build().getService)
    case EmulatorConfiguration(projectId, host, namespace) =>
      val withProjectId = DatastoreOptions.newBuilder().setProjectId(projectId)
      val withNamespace = namespace.fold(withProjectId)(ns => withProjectId.setNamespace(ns))
      new WrappedDatastore(handleEmulatorHost(withNamespace)(host).build().getService)
    case Options(options) =>
      val serviceOptions = emulatorVariable().fold(options)(handleEmulatorHost(options.toBuilder)(_).build())
      new WrappedDatastore(serviceOptions.getService)
    case FromEnvironmentVariables =>
      val defaultOptionsBuilder = DatastoreOptions.getDefaultInstance.toBuilder
      val withEmulator = emulatorVariable().fold(defaultOptionsBuilder)(handleEmulatorHost(defaultOptionsBuilder))
      val withNamespace = sys.env
        .get("DATASTORE_NAMESPACE")
        .fold(withEmulator)(ns => withEmulator.setNamespace(ns))
      new WrappedDatastore(withNamespace.build().getService)
  }

  private def emulatorVariable() = sys.env.get("DATASTORE_EMULATOR_HOST")

  def findOne[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Option[E]] =
    DatastoreOperation { datastoreService =>
      val entityKey = datastoreService.createKey(key, format.kind)
      datastoreService.find(entityKey) match {
        case Success(None)         => Right(None)
        case Success(Some(entity)) => format.fromEntity(entity).map(Some(_))
        case Failure(error)        => exception(error)
      }
    }

  def put[E, K](entityObject: E)(implicit toEntityComponents: ToEntityComponents[E, K], toKey: ToKey[K]): DatastoreOperation[Persisted[E]] =
    persistEntity(entityObject, _.put(_))

  def putAll[E, K](entities: Seq[E])(implicit toEntityComponents: ToEntityComponents[E, K],
                                     toKey: ToKey[K]): DatastoreOperation[Seq[Persisted[E]]] =
    persistEntities(entities, _.putAll(_))

  def save[E, K](entityObject: E)(implicit toEntityComponents: ToEntityComponents[E, K],
                                  toKey: ToKey[K]): DatastoreOperation[Persisted[E]] =
    persistEntity(entityObject, _.save(_))

  def saveAll[E, K](entities: Seq[E])(implicit toEntityComponents: ToEntityComponents[E, K],
                                      toKey: ToKey[K]): DatastoreOperation[Seq[Persisted[E]]] =
    persistEntities(entities, _.saveAll(_))

  private def persistEntity[E, K](
    entityObject: E,
    persistingFunction: (DatastoreService, Entity) => Try[Entity]
  )(implicit toEntityComponents: ToEntityComponents[E, K], toKey: ToKey[K]): DatastoreOperation[Persisted[E]] =
    DatastoreOperation { datastoreService =>
      val entity = toEntity(entityObject, toEntityComponents, datastoreService)
      persistingFunction(datastoreService, entity) match {
        case Success(persisted) => Right(Persisted(entityObject, persisted))
        case Failure(error)     => exception(error)
      }
    }

  private def persistEntities[E, K](
    entities: Seq[E],
    persistingFunction: (DatastoreService, Seq[Entity]) => Try[Seq[Entity]]
  )(implicit toEntityComponents: ToEntityComponents[E, K], toKey: ToKey[K]): DatastoreOperation[Seq[Persisted[E]]] =
    DatastoreOperation { datastoreService =>
      val mapped = entities.map(entityObject => Persisted(entityObject, toEntity(entityObject, toEntityComponents, datastoreService)))
      persistingFunction(datastoreService, mapped.map(_.entity)) match {
        case Success(_)     => Right(mapped)
        case Failure(error) => exception(error)
      }
    }

  private[datastore4s] def toEntity[E, K](
    entityObject: E,
    toEntityComponents: ToEntityComponents[E, K],
    datastoreService: DatastoreService
  )(implicit toKey: ToKey[K]) = { // TODO this is only package private for tests. Should it be?
    val components = toEntityComponents.toEntityComponents(entityObject)
    val key = datastoreService.createKey(components.key, components.kind)
    components.builderFunction(new WrappedBuilder(key))
  }

  def delete[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[K] =
    DatastoreOperation { datastoreService =>
      val dsKey = datastoreService.createKey(key, format.kind)
      datastoreService.delete(dsKey).map(exception).getOrElse(Right(key))
    }

  /** Like delete but will fail if an entity with the given key does not exist before attempting delete */
  def safeDelete[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[K] = transactionally {
    for {
      entity <- findOne[E, K](key)
      result <- entity.map(_ => delete(key)).getOrElse(DatastoreOperation.failure(s"Could not find entity with key: $key"))
    } yield result
  }

  def deleteEntity[E, K](entity: E)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[K] =
    delete[E, K](format.toEntityComponents(entity).key)

  def safeDeleteEntity[E, K](entity: E)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[K] =
    safeDelete[E, K](format.toEntityComponents(entity).key)

  def deleteAll[E, K](keys: Seq[K])(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Seq[K]] =
    DatastoreOperation { datastoreService =>
      val dsKeys = keys.map(datastoreService.createKey(_, format.kind))
      datastoreService.deleteAll(dsKeys).map(exception).getOrElse(Right(keys))
    }

  def deleteAllEntities[E, K](entities: Seq[E])(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Seq[K]] =
    deleteAll[E, K](entities.map(format.toEntityComponents).map(_.key))

  def list[E](implicit format: EntityFormat[E, _]): Query[E] = {
    val queryBuilderSupplier = () => newEntityQueryBuilder().setKind(format.kind.name)
    new DatastoreQuery[E, DsEntity](queryBuilderSupplier, entityFunction = new WrappedEntity(_))
  }

  def projectInto[E, A](firstMapping: (String, String), remainingMappings: (String, String)*)(implicit format: EntityFormat[E, _],
                                                                                              fromEntity: FromEntity[A]): Query[A] = {
    val queryBuilderSupplier = () =>
      newProjectionEntityQueryBuilder()
        .setKind(format.kind.name)
        .setProjection(firstMapping._1, remainingMappings.map(_._1): _*)

    val mappings = (firstMapping.swap +: remainingMappings.map(_.swap)).toMap
    new DatastoreQuery[A, DsProjectionEntity](queryBuilderSupplier, entityFunction = new ProjectionEntity(mappings, _))
  }

  def transactionally[A](operation: DatastoreOperation[A]): DatastoreOperation[A] = DatastoreOperation { service =>
    val (trans, transactionalService) = service.newTransaction()
    operation.op(transactionalService) match {
      case Right(value) =>
        Try(trans.commit()).fold(failed => exception[A](SuppressedStackTrace("Could not commit transaction", failed)), _ => Right(value))
      case Left(error) =>
        Try(trans.rollback())
          .fold(
            failed => exception[A](SuppressedStackTrace("Could not roll back transaction", failed)),
            _ =>
              exception[A](SuppressedStackTrace("Failure in transaction. Transaction was rolled back.", DatastoreError.asThrowable(error))),
          )
    }
  }

}

trait DatastoreService {
  def delete(key: Key): Option[Throwable]

  def deleteAll(keys: Seq[Key]): Option[Throwable]

  def find(entityKey: Key): Try[Option[Entity]]

  def put(entity: Entity): Try[Entity]

  def putAll(entities: Seq[Entity]): Try[Seq[Entity]]

  def save(entity: Entity): Try[Entity]

  def saveAll(entities: Seq[Entity]): Try[Seq[Entity]]

  def createKey[K](key: K, kind: Kind)(implicit toKey: ToKey[K]): Key

  def runQuery[D <: BaseEntity[Key]](query: StructuredQuery[D]): Stream[D]

  def newTransaction(): (Transaction, DatastoreService)

  def options: DatastoreOptions

}

sealed trait ReaderWriterService extends DatastoreService with DatastoreErrors {
  private type DsEntity = com.google.cloud.datastore.FullEntity[Key]

  def readerWriter(): DatastoreReaderWriter
  def datastore(): Datastore

  override def find(entityKey: Key) = Try(Option(readerWriter().get(entityKey))).map(_.map(new WrappedEntity(_)))

  override def put(entity: Entity) = persist(entity, _.put(_))

  override def putAll(entities: Seq[Entity]) = persistAll(entities, _.put(_: _*).asScala.toSeq)

  override def save(entity: Entity) = persist(entity, _.add(_))

  override def saveAll(entities: Seq[Entity]) = persistAll(entities, _.add(_: _*).asScala.toSeq)

  override def delete(key: Key) = try { readerWriter().delete(key); None } catch { case e: Throwable => Some(e) } // Cannot return anything useful.

  override def deleteAll(keys: Seq[Key]) = try { readerWriter().delete(keys: _*); None } catch { case e: Throwable => Some(e) } // Cannot return anything useful.

  override def runQuery[D <: BaseEntity[Key]](query: StructuredQuery[D]) = readerWriter().run(query).asScala.toStream

  override def createKey[K](key: K, kind: Kind)(implicit toKey: ToKey[K]) = toKey.toKey(key, newKeyFactory(kind))

  private def newKeyFactory(kind: Kind) = new KeyFactoryFacade(datastore().newKeyFactory().setKind(kind.name))

  override def options = datastore().getOptions

  override def newTransaction() = {
    val transaction = datastore().newTransaction()
    (transaction, new TransactionService(datastore(), transaction))
  }

  private def persist(entity: Entity, persistingFunction: (DatastoreReaderWriter, DsEntity) => DsEntity): Try[Entity] = entity match {
    case wrapped: WrappedEntity =>
      Try { persistingFunction(readerWriter(), wrapped.entity); entity }
    case projection: ProjectionEntity => // TODO is it possible to ensure this doesn't happen at compile time?
      Failure(
        new RuntimeException(
          s"Attempted to persist a Projection entity. This should never happen, an EntityFormat somehow returned a projection. Projection; $projection"
        )
      )
  }

  private def persistAll(entities: Seq[Entity],
                         persistingFunction: (DatastoreReaderWriter, Seq[DsEntity]) => Seq[DsEntity]): Try[Seq[Entity]] = {
    val dsEntities = entities map {
      case wrapped: WrappedEntity => Success(wrapped.entity)
      case projection: ProjectionEntity => // TODO is it possible to ensure this doesn't happen at compile time?
        Failure(
          new RuntimeException(
            s"Attempted to persist a Projection entity. This should never happen, an EntityFormat somehow returned a projection. Projection; $projection"
          )
        )
    }
    sequenceTry(dsEntities).map(persistingFunction(readerWriter(), _)).map(_.map(new WrappedEntity(_)))
  }

  private def sequenceTry[T](xs: Seq[Try[T]]): Try[Seq[T]] = xs.foldLeft(Try(Seq[T]())) { (a, b) =>
    a flatMap (c => b map (d => c :+ d))
  }
}

private[datastore4s] class WrappedDatastore(val datastore: Datastore) extends ReaderWriterService {
  override def readerWriter(): DatastoreReaderWriter = datastore
}

private[datastore4s] class TransactionService(val datastore: Datastore, private val transaction: Transaction) extends ReaderWriterService {
  override def readerWriter(): DatastoreReaderWriter = transaction
}
