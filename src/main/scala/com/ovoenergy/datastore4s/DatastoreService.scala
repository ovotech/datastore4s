package com.ovoenergy.datastore4s

import com.google.cloud.datastore.{Datastore, DatastoreOptions, ReadOption}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

case class DataStoreConfiguration(projectId: String, namespace: String)

case class Persisted[A](inputObject: A, entity: Entity)

case class DatastoreOperation[A](get: () => Either[DatastoreError, A]) {

  def map[B](f: A => B): DatastoreOperation[B] = DatastoreOperation(() => get().map(f))

  def flatMapEither[B](f: A => Either[DatastoreError, B]): DatastoreOperation[B] = DatastoreOperation(() => get().flatMap(f))

  def flatMap[B](f: A => DatastoreOperation[B]): DatastoreOperation[B] = DatastoreOperation(() => get().map(f).flatMap(_.get()))

}

object DatastoreService {

  def createDatastore(dataStoreConfiguration: DataStoreConfiguration): Datastore =
    DatastoreOptions
      .newBuilder()
      .setProjectId(dataStoreConfiguration.projectId)
      .setNamespace(dataStoreConfiguration.namespace)
      .build()
      .getService

  def findOne[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K], datastore: Datastore): DatastoreOperation[Option[E]] =
    DatastoreOperation { () =>
      val keyFactory = KeyFactoryFacade(datastore, format.kind)
      val entityKey = toKey.toKey(key, keyFactory)
      Option(datastore.get(entityKey, Seq.empty[ReadOption]: _*)) match {
        case None         => Right(None)
        case Some(entity) => format.fromEntity(WrappedEntity(entity)).map(Some(_))
      }
    }

  def put[E, K](
    entityObject: E
  )(implicit format: EntityFormat[E, K], toKey: ToKey[K], datastore: Datastore): DatastoreOperation[Persisted[E]] =
    DatastoreOperation { () =>
      val entity = toEntity(entityObject, format) match {
        case WrappedEntity(e: com.google.cloud.datastore.Entity) =>
          e // TODO better way? just make the class and extractor package private I guess?
      }
      Try(datastore.put(entity)) match {
        case Success(entity) => Right(Persisted(entityObject, WrappedEntity(entity)))
        case Failure(f)      => DatastoreError.error(f.getMessage)
      }
    }

  private[datastore4s] def toEntity[E, K](entityObject: E, format: EntityFormat[E, K])(implicit toKey: ToKey[K], datastore: Datastore) = {
    val key = toKey.toKey(format.key(entityObject), new KeyFactoryFacade(datastore.newKeyFactory().setKind(format.kind.name)))
    val builder = WrappedBuilder(key)
    format.toEntity(entityObject, builder)
  }

  private def createKeyFactory[K, E](format: EntityFormat[E, K], datastore: Datastore) =
    new KeyFactoryFacade(datastore.newKeyFactory().setKind(format.kind.name))

  def delete[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K], datastore: Datastore): DatastoreOperation[K] =
    DatastoreOperation { () =>
      val dsKey = toKey.toKey(key, createKeyFactory(format, datastore))
      Try(datastore.delete(dsKey)) match {
        case Success(_) => Right(key)
        case Failure(f) => DatastoreError.error(f.getMessage)
      }
    }

  def list[E](implicit format: EntityFormat[E, _], datastore: Datastore): Query[E] = {
    val kind = format.kind.name
    val queryBuilder =
      com.google.cloud.datastore.Query.newEntityQueryBuilder().setKind(kind)
    new DatastoreQuery(queryBuilder)
  }

  def project[E]()(implicit format: EntityFormat[E, _], datastore: Datastore): Project[E] = Project()

  def run[A](operation: DatastoreOperation[A]): Either[DatastoreError, A] = operation.get()

  def runAsync[A](operation: DatastoreOperation[A])(implicit executionContext: ExecutionContext): Future[Either[DatastoreError, A]] =
    Future(run(operation))

  def runAsyncF[A](operation: DatastoreOperation[A])(implicit executionContext: ExecutionContext): Future[A] = runAsync(operation).flatMap {
    case Right(a)    => Future.successful(a)
    case Left(error) => Future.failed(new RuntimeException(error.toString))
  }

}
