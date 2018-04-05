package com.ovoenergy.datastore4s

import com.google.cloud.datastore._

import scala.util.{Failure, Success, Try}

sealed trait DataStoreConfiguration

final case class ManualDataStoreConfiguration(projectId: String, namespace: String) extends DataStoreConfiguration
case object FromEnvironmentVariables extends DataStoreConfiguration

final case class Persisted[A](inputObject: A, entity: Entity)

object DatastoreService {

  def createDatastoreService(dataStoreConfiguration: DataStoreConfiguration): DatastoreService = dataStoreConfiguration match {
    case ManualDataStoreConfiguration(projectId, namespace) =>
      new WrappedDatastore(
        DatastoreOptions
          .newBuilder()
          .setProjectId(projectId)
          .setNamespace(namespace)
          .build()
          .getService
      )
    case FromEnvironmentVariables => new WrappedDatastore(DatastoreOptions.getDefaultInstance().getService)
  }

  def findOne[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Option[E]] =
    DatastoreOperation { datastoreService =>
      val entityKey = datastoreService.createKey(key, format.kind)
      datastoreService.find(entityKey).flatMap {
        case None         => Right(None)
        case Some(entity) => format.fromEntity(entity).map(Some(_))
      }
    }

  def put[E, K](entityObject: E)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Persisted[E]] =
    persistEntity(entityObject, (datastoreService, entity) => datastoreService.put(entity))

  def save[E, K](entityObject: E)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Persisted[E]] =
    persistEntity(entityObject, (datastoreService, entity) => datastoreService.save(entity))

  private def persistEntity[E, K](
    entityObject: E,
    persistingFunction: (DatastoreService, Entity) => Either[DatastoreError, Entity]
  )(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Persisted[E]] =
    DatastoreOperation { datastoreService =>
      val entity = toEntity(entityObject, format, datastoreService)
      persistingFunction(datastoreService, entity).map(persisted => Persisted(entityObject, persisted))
    }

  private[datastore4s] def toEntity[E, K](entityObject: E, format: EntityFormat[E, K], datastoreService: DatastoreService)(
    implicit toKey: ToKey[K]
  ) = {
    val key = datastoreService.createKey(format.key(entityObject), format.kind)
    format.toEntity(entityObject, new WrappedBuilder(key))
  }

  def delete[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[K] =
    DatastoreOperation { datastoreService =>
      val dsKey = datastoreService.createKey(key, format.kind)
      datastoreService.delete(dsKey).map(_ => key)
    }

  def list[E](implicit format: EntityFormat[E, _]): Query[E] = {
    val queryBuilderSupplier = () => com.google.cloud.datastore.Query.newEntityQueryBuilder().setKind(format.kind.name)
    new DatastoreQuery[E, com.google.cloud.datastore.Entity](queryBuilderSupplier, entityFunction = new WrappedEntity(_))
  }

  def projectInto[E, A](firstMapping: (String, String), remainingMappings: (String, String)*)(implicit format: EntityFormat[E, _],
                                                                                              fromEntity: FromEntity[A]): Query[A] = {
    val queryBuilderSupplier = () =>
      com.google.cloud.datastore.Query
        .newProjectionEntityQueryBuilder()
        .setKind(format.kind.name)
        .setProjection(firstMapping._1, remainingMappings.map(_._1): _*)

    val mappings = (firstMapping.swap +: remainingMappings.map(_.swap)).toMap
    new DatastoreQuery[A, com.google.cloud.datastore.ProjectionEntity](
      queryBuilderSupplier,
      entityFunction = new ProjectionEntity(mappings, _)
    )
  }

}

trait DatastoreService {
  def delete(key: Key): Either[DatastoreError, Unit]

  def find(entityKey: Key): Either[DatastoreError, Option[Entity]]

  def put(entity: Entity): Either[DatastoreError, Entity]

  def save(entity: Entity): Either[DatastoreError, Entity]

  def createKey[K](key: K, kind: Kind)(implicit toKey: ToKey[K]): Key

  def runQuery[D <: BaseEntity[Key]](query: StructuredQuery[D]): Stream[D]

}

private[datastore4s] class WrappedDatastore(private val datastore: Datastore) extends DatastoreService with DatastoreErrors {

  private val noOptions = Seq.empty[ReadOption]
  private type DsEntity = com.google.cloud.datastore.FullEntity[Key]

  override def createKey[K](key: K, kind: Kind)(implicit toKey: ToKey[K]): Key = toKey.toKey(key, newKeyFactory(kind))

  private def newKeyFactory(kind: Kind): KeyFactory = new KeyFactoryFacade(datastore.newKeyFactory().setKind(kind.name))

  override def put(entity: Entity): Either[DatastoreError, Entity] = persist(entity, (ds, e) => ds.put(e))

  override def save(entity: Entity): Either[DatastoreError, Entity] = persist(entity, (ds, e) => ds.add(e))

  private def persist(entity: Entity, persistingFunction: (Datastore, DsEntity) => DsEntity) = entity match {
    case wrapped: WrappedEntity =>
      Try(persistingFunction(datastore, wrapped.entity)) match {
        case Success(persistedEntity) => Right(new WrappedEntity(persistedEntity))
        case Failure(f)               => exception(f)
      }
    case projection: ProjectionEntity =>
      error(s"Projection entity was returned from a mapping instead of WrappedEntity. This should never happen. Projection; $projection")
  }

  override def find(entityKey: Key): Either[DatastoreError, Option[Entity]] = Try(Option(datastore.get(entityKey, noOptions: _*))) match {
    case Success(result) => Right(result.map(new WrappedEntity(_)))
    case Failure(f)      => exception(f)
  }

  override def delete(key: Key): Either[DatastoreError, Unit] = Try(datastore.delete(key)) match {
    case Success(unit) => Right(unit)
    case Failure(f)    => exception(f)
  }

  import scala.collection.JavaConverters._
  override def runQuery[D <: BaseEntity[Key]](query: StructuredQuery[D]): Stream[D] = datastore.run(query, noOptions: _*).asScala.toStream
}
