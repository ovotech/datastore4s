package com.ovoenergy.datastore4s

import java.io.{File, FileInputStream}

import com.google.auth.Credentials
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.datastore._
import com.google.cloud.datastore.Query.{newEntityQueryBuilder, newProjectionEntityQueryBuilder}
import com.google.cloud.datastore.{Entity => DsEntity, ProjectionEntity => DsProjectionEntity}

import scala.util.{Failure, Success, Try}

sealed trait DataStoreConfiguration

final case class ManualDataStoreConfiguration(projectId: String, namespace: Option[String] = None, credentials: Option[Credentials] = None)
    extends DataStoreConfiguration
case object FromEnvironmentVariables extends DataStoreConfiguration

object DataStoreConfiguration {
  def apply(projectId: String): DataStoreConfiguration = ManualDataStoreConfiguration(projectId)
  def apply(projectId: String, namespace: String): DataStoreConfiguration = ManualDataStoreConfiguration(projectId, Some(namespace))
  def apply(projectId: String, credentialsFile: File): Try[DataStoreConfiguration] =
    createCredentials(credentialsFile).map(c => ManualDataStoreConfiguration(projectId, credentials = Some(c)))
  def apply(projectId: String, namespace: String, credentialsFile: File): Try[DataStoreConfiguration] =
    createCredentials(credentialsFile).map(c => ManualDataStoreConfiguration(projectId, Some(namespace), Some(c)))

  private def createCredentials(credentialsFile: File): Try[Credentials] =
    Try(new FileInputStream(credentialsFile)).flatMap { is =>
      for {
        credentials <- Try(GoogleCredentials.fromStream(is))
        _ <- Try(is.close())
      } yield credentials
    }
}

final case class Persisted[A](inputObject: A, entity: Entity)

object DatastoreService extends DatastoreErrors {

  def apply(dataStoreConfiguration: DataStoreConfiguration): DatastoreService = dataStoreConfiguration match {
    case ManualDataStoreConfiguration(projectId, namespace, credentials) =>
      val withProjectId = DatastoreOptions.newBuilder().setProjectId(projectId)
      val withNamespace = namespace.fold(withProjectId)(ns => withProjectId.setNamespace(ns))
      val withCredentials = credentials.fold(withNamespace)(creds => withNamespace.setCredentials(creds))
      new WrappedDatastore(withCredentials.build().getService)
    case FromEnvironmentVariables =>
      val defaultOptions = DatastoreOptions.getDefaultInstance()
      val withNamespace =
        sys.env
          .get("DATASTORE_NAMESPACE")
          .fold(defaultOptions)(ns => defaultOptions.toBuilder.setNamespace(ns).build())
      new WrappedDatastore(withNamespace.getService)
  }

  def findOne[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Option[E]] =
    DatastoreOperation { datastoreService =>
      val entityKey = datastoreService.createKey(key, format.kind)
      datastoreService.find(entityKey) match {
        case Success(None)         => Right(None)
        case Success(Some(entity)) => format.fromEntity(entity).map(Some(_))
        case Failure(error)        => exception(error)
      }
    }

  def put[E, K](entityObject: E)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Persisted[E]] =
    persistEntity(entityObject, (datastoreService, entity) => datastoreService.put(entity))

  def save[E, K](entityObject: E)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Persisted[E]] =
    persistEntity(entityObject, (datastoreService, entity) => datastoreService.save(entity))

  private def persistEntity[E, K](
    entityObject: E,
    persistingFunction: (DatastoreService, Entity) => Try[Entity]
  )(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Persisted[E]] =
    DatastoreOperation { datastoreService =>
      val entity = toEntity(entityObject, format, datastoreService)
      persistingFunction(datastoreService, entity) match {
        case Success(persisted) => Right(Persisted(entityObject, persisted))
        case Failure(error)     => exception(error)
      }
    }

  private[datastore4s] def toEntity[E, K](entityObject: E, format: EntityFormat[E, K], datastoreService: DatastoreService)(
    implicit toKey: ToKey[K]
  ) = { // TODO this is only package private for tests. Should it be?
    val key = datastoreService.createKey(format.key(entityObject), format.kind)
    format.toEntity(entityObject, new WrappedBuilder(key))
  }

  def delete[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[K] =
    DatastoreOperation { datastoreService =>
      val dsKey = datastoreService.createKey(key, format.kind)
      datastoreService.delete(dsKey).map(exception).getOrElse(Right(key))
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
  def delete(key: Key): Option[Throwable]

  def find(entityKey: Key): Try[Option[Entity]]

  def put(entity: Entity): Try[Entity]

  def save(entity: Entity): Try[Entity]

  def createKey[K](key: K, kind: Kind)(implicit toKey: ToKey[K]): Key

  def runQuery[D <: BaseEntity[Key]](query: StructuredQuery[D]): Stream[D]

  def configuration: DataStoreConfiguration

}

private[datastore4s] class WrappedDatastore(private val datastore: Datastore) extends DatastoreService with DatastoreErrors {

  private val noOptions = Seq.empty[ReadOption]
  private type DsEntity = com.google.cloud.datastore.FullEntity[Key]

  override def createKey[K](key: K, kind: Kind)(implicit toKey: ToKey[K]): Key = toKey.toKey(key, newKeyFactory(kind))

  private def newKeyFactory(kind: Kind): KeyFactory = new KeyFactoryFacade(datastore.newKeyFactory().setKind(kind.name))

  override def put(entity: Entity) = persist(entity, (ds, e) => ds.put(e))

  override def save(entity: Entity) = persist(entity, (ds, e) => ds.add(e))

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

  override def find(entityKey: Key) = Try(Option(datastore.get(entityKey, noOptions: _*))).map(_.map(new WrappedEntity(_)))

  override def delete(key: Key) = try { datastore.delete(key); None } catch { case e: Throwable => Some(e) } // Cannot return anything useful.

  import scala.collection.JavaConverters._
  override def runQuery[D <: BaseEntity[Key]](query: StructuredQuery[D]): Stream[D] = datastore.run(query, noOptions: _*).asScala.toStream

  override def configuration = {
    val options = datastore.getOptions
    DataStoreConfiguration(options.getProjectId, options.getNamespace)
  }
}
