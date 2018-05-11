package com.ovoenergy.datastore4s

import com.google.cloud.{NoCredentials, ServiceOptions}
import com.google.cloud.datastore.{DatastoreOptions, Entity => DsEntity, Key => DsKey, ProjectionEntity => DsProjectionEntity, _}
import com.google.cloud.datastore.Query.{newEntityQueryBuilder, newProjectionEntityQueryBuilder}
import com.ovoenergy.datastore4s.ToKey.{LongToKey, StringToKey}

import scala.util.{Failure, Success, Try}

final case class Persisted[A](inputObject: A, entity: Entity)

object DatastoreService extends DatastoreErrors {

  /**
    * When the production code runs in an emulated environment then the host, credentials and retry options are handled internally
    * to allow connecting to the emulator without credentials being verified.
    */
  private def handleEmulatorHost(builder: DatastoreOptions.Builder)(emulatorHost: String): DatastoreOptions.Builder =
    builder.setHost(emulatorHost).setCredentials(NoCredentials.getInstance()).setRetrySettings(ServiceOptions.getNoRetrySettings())

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
    case Options(options) => new WrappedDatastore(options.getService)
    case FromEnvironmentVariables =>
      val defaultOptionsBuilder = DatastoreOptions.getDefaultInstance().toBuilder
      val withEmulator = emulatorVariable().fold(defaultOptionsBuilder)(handleEmulatorHost(defaultOptionsBuilder))
      val withNamespace = sys.env
        .get("DATASTORE_NAMESPACE")
        .fold(withEmulator)(ns => withEmulator.setNamespace(ns))
      new WrappedDatastore(withNamespace.build().getService)
  }

  private def emulatorVariable() = sys.env.get("DATASTORE_EMULATOR_HOST")

  def findOne[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K, _]): DatastoreOperation[Option[E]] =
    DatastoreOperation { datastoreService =>
      val entityKey = datastoreService.createKey(key, format.kind)
      find(entityKey, datastoreService)
    }

  def findOneByName[E, K](name: String)(implicit format: EntityFormat[E, K], toKey: ToNamedKey[K]): DatastoreOperation[Option[E]] =
    DatastoreOperation { datastoreService =>
      val entityKey = datastoreService.createKey(name, format.kind)
      find(entityKey, datastoreService)
    }

  def findOneById[E, K](id: Long)(implicit format: EntityFormat[E, K], toKey: ToIdKey[K]): DatastoreOperation[Option[E]] =
    DatastoreOperation { datastoreService =>
      val entityKey = datastoreService.createKey(Long.box(id), format.kind)
      find(entityKey, datastoreService)
    }

  private def find[E](key: DsKey, datastoreService: DatastoreService)(implicit format: EntityFormat[E, _]) =
    datastoreService.find(key) match {
      case Success(None)         => Right(None)
      case Success(Some(entity)) => format.fromEntity(entity).map(Some(_))
      case Failure(error)        => exception(error)
    }


  def put[E, K](entityObject: E)(implicit format: EntityFormat[E, K], toKey: ToKey[K, _]): DatastoreOperation[Persisted[E]] =
    persistEntity(entityObject, _.put(_))

  def putAll[E, K](entities: Seq[E])(implicit format: EntityFormat[E, K], toKey: ToKey[K, _]): DatastoreOperation[Seq[Persisted[E]]] =
    persistEntities(entities, _.putAll(_))

  def save[E, K](entityObject: E)(implicit format: EntityFormat[E, K], toKey: ToKey[K, _]): DatastoreOperation[Persisted[E]] =
    persistEntity(entityObject, _.save(_))

  def saveAll[E, K](entities: Seq[E])(implicit format: EntityFormat[E, K], toKey: ToKey[K, _]): DatastoreOperation[Seq[Persisted[E]]] =
    persistEntities(entities, _.saveAll(_))

  private def persistEntity[E, K](
    entityObject: E,
    persistingFunction: (DatastoreService, Entity) => Try[Entity]
  )(implicit format: EntityFormat[E, K], toKey: ToKey[K, _]): DatastoreOperation[Persisted[E]] =
    DatastoreOperation { datastoreService =>
      val entity = toEntity(entityObject, format, datastoreService)
      persistingFunction(datastoreService, entity) match {
        case Success(persisted) => Right(Persisted(entityObject, persisted))
        case Failure(error)     => exception(error)
      }
    }

  private def persistEntities[E, K](
    entities: Seq[E],
    persistingFunction: (DatastoreService, Seq[Entity]) => Try[Seq[Entity]]
  )(implicit format: EntityFormat[E, K], toKey: ToKey[K, _]): DatastoreOperation[Seq[Persisted[E]]] =
    DatastoreOperation { datastoreService =>
      val mapped = entities.map(entityObject => Persisted(entityObject, toEntity(entityObject, format, datastoreService)))
      persistingFunction(datastoreService, mapped.map(_.entity)) match {
        case Success(_)     => Right(mapped)
        case Failure(error) => exception(error)
      }
    }

  private[datastore4s] def toEntity[E, K](entityObject: E, format: EntityFormat[E, K], datastoreService: DatastoreService)(
    implicit toKey: ToKey[K, _]
  ) = { // TODO this is only package private for tests. Should it be?
    val key = datastoreService.createKey(format.key(entityObject), format.kind)
    format.toEntity(entityObject, new WrappedBuilder(key))
  }

  def delete[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K, _]): DatastoreOperation[K] =
    DatastoreOperation { datastoreService =>
      val dsKey = datastoreService.createKey(key, format.kind)
      datastoreService.delete(dsKey).map(exception).getOrElse(Right(key))
    }

  def deleteAll[E, K](keys: Seq[K])(implicit format: EntityFormat[E, K], toKey: ToKey[K, _]): DatastoreOperation[Seq[K]] =
    DatastoreOperation { datastoreService =>
      val dsKeys = keys.map(datastoreService.createKey(_, format.kind))
      datastoreService.deleteAll(dsKeys).map(exception).getOrElse(Right(keys))
    }

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

}

trait DatastoreService {
  def delete(key: DsKey): Option[Throwable]

  def deleteAll(keys: Seq[DsKey]): Option[Throwable]

  def find(entityKey: DsKey): Try[Option[Entity]]

  def put(entity: Entity): Try[Entity]

  def putAll(entities: Seq[Entity]): Try[Seq[Entity]]

  def save(entity: Entity): Try[Entity]

  def saveAll(entities: Seq[Entity]): Try[Seq[Entity]]

  def createKey[K](key: K, kind: Kind)(implicit toKey: ToKey[K, _]): DsKey

  def runQuery[D <: BaseEntity[DsKey]](query: StructuredQuery[D]): Stream[D]

  def options: DatastoreOptions

}

private[datastore4s] class WrappedDatastore(private val datastore: Datastore) extends DatastoreService with DatastoreErrors {
  import scala.collection.JavaConverters._

  private val noOptions = Seq.empty[ReadOption]
  private type DsEntity = com.google.cloud.datastore.FullEntity[DsKey]

  override def createKey[K](key: K, kind: Kind)(implicit toKey: ToKey[K, _]): DsKey = {
    val initialKeyFactory = datastore.newKeyFactory().setKind(kind.name)
    toKey.toKey(key) match {
      case NamedKey(name, ancestor) => ancestor.fold(initialKeyFactory.newKey(name))(a => initialKeyFactory.addAncestor(toPath(a)).newKey(name))
      case IdKey(id, ancestor) => ancestor.fold(initialKeyFactory.newKey(id))(a => initialKeyFactory.addAncestor(toPath(a)).newKey(id))
    }
  }

  private def toPath(ancestor: Ancestor) = ancestor match {
    case LongAncestor(kind, id) => PathElement.of(kind.name, id)
    case StringAncestor(kind, name) => PathElement.of(kind.name, name)
  }

  override def put(entity: Entity) = persist(entity, _.put(_))

  override def putAll(entities: Seq[Entity]) = persistAll(entities, _.put(_: _*).asScala.toSeq)

  override def save(entity: Entity) = persist(entity, _.add(_))

  override def saveAll(entities: Seq[Entity]) = persistAll(entities, _.add(_: _*).asScala.toSeq)

  private def persist(entity: Entity, persistingFunction: (Datastore, DsEntity) => DsEntity): Try[Entity] = entity match {
    case wrapped: WrappedEntity =>
      Try { persistingFunction(datastore, wrapped.entity); entity }
    case projection: ProjectionEntity => // TODO is it possible to ensure this doesn't happen at compile time?
      Failure(
        new RuntimeException(
          s"Attempted to persist a Projection entity. This should never happen, an EntityFormat somehow returned a projection. Projection; $projection"
        )
      )
  }

  private def persistAll(entities: Seq[Entity], persistingFunction: (Datastore, Seq[DsEntity]) => Seq[DsEntity]): Try[Seq[Entity]] = {
    val dsEntities = entities map {
      case wrapped: WrappedEntity => Success(wrapped.entity)
      case projection: ProjectionEntity => // TODO is it possible to ensure this doesn't happen at compile time?
        Failure(
          new RuntimeException(
            s"Attempted to persist a Projection entity. This should never happen, an EntityFormat somehow returned a projection. Projection; $projection"
          )
        )
    }
    sequenceTry(dsEntities).map(persistingFunction(datastore, _)).map(_.map(new WrappedEntity(_)))
  }

  def sequenceTry[T](xs: Seq[Try[T]]): Try[Seq[T]] = xs.foldLeft(Try(Seq[T]())) { (a, b) =>
    a flatMap (c => b map (d => c :+ d))
  }

  override def find(entityKey: DsKey) = Try(Option(datastore.get(entityKey, noOptions: _*))).map(_.map(new WrappedEntity(_)))

  override def delete(key: DsKey) = try { datastore.delete(key); None } catch { case e: Throwable => Some(e) } // Cannot return anything useful.

  override def deleteAll(keys: Seq[DsKey]) = try { datastore.delete(keys: _*); None } catch { case e: Throwable => Some(e) } // Cannot return anything useful.

  import scala.collection.JavaConverters._
  override def runQuery[D <: BaseEntity[DsKey]](query: StructuredQuery[D]): Stream[D] = datastore.run(query, noOptions: _*).asScala.toStream

  override def options = datastore.getOptions
}
