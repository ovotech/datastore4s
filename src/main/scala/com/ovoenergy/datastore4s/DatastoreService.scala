package com.ovoenergy.datastore4s

import com.google.cloud.datastore.{Datastore, DatastoreOptions, Key, ReadOption}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class DataStoreConfiguration(projectId: String, namespace: String)

case class Persisted[A](inputObject: A, entity: Entity)

case class DatastoreOperation[A](op: Datastore => Either[DatastoreError, A]) {

  def map[B](f: A => B): DatastoreOperation[B] = DatastoreOperation(ds => op(ds).map(f))

  def flatMapEither[B](f: A => Either[DatastoreError, B]): DatastoreOperation[B] = DatastoreOperation(ds => op(ds).flatMap(f))

  def flatMap[B](f: A => DatastoreOperation[B]): DatastoreOperation[B] = DatastoreOperation(ds => op(ds).map(f).flatMap(_.op(ds)))

}

object DatastoreService {

  def createDatastore(dataStoreConfiguration: DataStoreConfiguration): Datastore =
    DatastoreOptions
      .newBuilder()
      .setProjectId(dataStoreConfiguration.projectId)
      .setNamespace(dataStoreConfiguration.namespace)
      .build()
      .getService

  def findOne[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Option[E]] =
    DatastoreOperation { datastore =>
      val keyFactory = KeyFactoryFacade(datastore, format.kind)
      val entityKey = toKey.toKey(key, keyFactory)
      Try(Option(datastore.get(entityKey, Seq.empty[ReadOption]: _*))) match {
        case Success(None)         => Right(None)
        case Success(Some(entity)) => format.fromEntity(new WrappedEntity(entity)).map(Some(_))
        case Failure(f)            => DatastoreError.error(f.getMessage)
      }
    }

  def put[E, K](entityObject: E)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Persisted[E]] =
    persistEntity(entityObject, (datastore, entity) => datastore.put(entity))

  def save[E, K](entityObject: E)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Persisted[E]] =
    persistEntity(entityObject, (datastore, entity) => datastore.add(entity))

  type DsEntity = com.google.cloud.datastore.FullEntity[Key]

  private def persistEntity[E, K](
    entityObject: E,
    persistingFunction: (Datastore, DsEntity) => DsEntity
  )(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Persisted[E]] =
    DatastoreOperation { datastore =>
      toEntity(entityObject, format, datastore) match {
        case wrapped: WrappedEntity =>
          Try(persistingFunction(datastore, wrapped.entity)) match {
            case Success(entity) => Right(Persisted(entityObject, new WrappedEntity(entity)))
            case Failure(f)      => DatastoreError.error(f.getMessage)
          }
        case projection: ProjectionEntity =>
          DatastoreError.error(
            s"Projection entity was returned from a mapping instead of WrappedEntity. This should never happen. Projection; $projection"
          )
      }
    }

  private[datastore4s] def toEntity[E, K](entityObject: E, format: EntityFormat[E, K], datastore: Datastore)(implicit toKey: ToKey[K]) = {
    val key = toKey.toKey(format.key(entityObject), createKeyFactory(format, datastore))
    val builder = new WrappedBuilder(key)
    format.toEntity(entityObject, builder)
  }

  private def createKeyFactory[K, E](format: EntityFormat[E, K], datastore: Datastore) =
    new KeyFactoryFacade(datastore.newKeyFactory().setKind(format.kind.name))

  def delete[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[K] =
    DatastoreOperation { datastore =>
      val dsKey = toKey.toKey(key, createKeyFactory(format, datastore))
      Try(datastore.delete(dsKey)) match {
        case Success(_) => Right(key)
        case Failure(f) => DatastoreError.error(f.getMessage)
      }
    }

  def list[E](implicit format: EntityFormat[E, _]): Query[E] = {
    val kind = format.kind.name
    val queryBuilderSupplier = (_: Datastore) => com.google.cloud.datastore.Query.newEntityQueryBuilder().setKind(kind)
    new DatastoreQuery[E, com.google.cloud.datastore.Entity](queryBuilderSupplier, new WrappedEntity(_))
  }

  def projectInto[E, A](firstMapping: (String, String), remainingMappings: (String, String)*)(implicit format: EntityFormat[E, _],
                                                                                              fromEntity: FromEntity[A]): Query[A] = {
    val queryBuilderSupplier = (_: Datastore) =>
      com.google.cloud.datastore.Query
        .newProjectionEntityQueryBuilder()
        .setKind(format.kind.name)
        .setProjection(firstMapping._1, remainingMappings.map(_._1): _*)

    val mappings = (firstMapping.swap +: remainingMappings.map(_.swap)).toMap
    new DatastoreQuery[A, com.google.cloud.datastore.ProjectionEntity](queryBuilderSupplier, new ProjectionEntity(mappings, _))
  }

  def run[A](operation: DatastoreOperation[A])(implicit datastore: Datastore): Either[DatastoreError, A] = operation.op(datastore)

  def runF[A](operation: DatastoreOperation[A])(implicit datastore: Datastore): Try[A] = run(operation) match {
    case Right(a)    => Success(a)
    case Left(error) => Failure(DatastoreError.asException(error))
  }

  def runAsync[A](operation: DatastoreOperation[A])(implicit executionContext: ExecutionContext,
                                                    datastore: Datastore): Future[Either[DatastoreError, A]] =
    Future(run(operation))

  def runAsyncF[A](operation: DatastoreOperation[A])(implicit executionContext: ExecutionContext, datastore: Datastore): Future[A] =
    runAsync(operation).flatMap {
      case Right(a)    => Future.successful(a)
      case Left(error) => Future.failed(DatastoreError.asException(error))
    }

}
