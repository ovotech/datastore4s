package com.ovoenergy.datastore4s

import com.google.cloud.datastore.{Datastore, DatastoreOptions, Entity, ReadOption}
import com.ovoenergy.datastore4s.internal.{DatastoreError, WrappedEntity}

import scala.concurrent.{ExecutionContext, Future}

case class DataStoreConfiguration(projectId: String, namespace: String)

case class Persisted[A](inputObject: A, entity: Entity)

case class DatastoreOperation[A](get: () => Either[DatastoreError, A]) {

  def map[B](f:A =>B): DatastoreOperation[B] = DatastoreOperation(() => get().map(f))

  def flatMapEither[B](f:A => Either[DatastoreError, B]): DatastoreOperation[B] = DatastoreOperation(() => get().flatMap(f))

  def flatMap[B](f:A => DatastoreOperation[B]): DatastoreOperation[B] = DatastoreOperation(() => get().map(f).flatMap(_.get()))

}


object DatastoreService {

  def createDatastore(dataStoreConfiguration: DataStoreConfiguration): Datastore =
    DatastoreOptions
      .newBuilder()
      .setProjectId(dataStoreConfiguration.projectId)
      .setNamespace(dataStoreConfiguration.namespace)
      .build()
      .getService

  def findOne[E, K](
    key: K
  )(implicit format: EntityFormat[E, K], toKey: ToKey[K], datastore: Datastore): DatastoreOperation[Option[E]] =
    DatastoreOperation { () =>
      val keyFactory = KeyFactoryFacade(datastore, format.kind)
      val entityKey = toKey.toKey(key, keyFactory)
      Option(datastore.get(entityKey, Seq.empty[ReadOption]: _*)) match {
        case None         => Right(None)
        case Some(entity) => format.fromEntity(WrappedEntity(entity)).map(Some(_))
      }
    }

  def put[E](entityObject: E)(implicit format: EntityFormat[E, _], datastore: Datastore): DatastoreOperation[Persisted[E]] =
    DatastoreOperation { () =>
      implicit val keyFactorySupplier = () => datastore.newKeyFactory()
      val entity = format.toEntity(entityObject) match {
        case WrappedEntity(e: Entity) => e
      }
      Right(Persisted(entityObject, datastore.put(entity))) // TODO handle datastore errors
    }

  def delete[E, K](key: K)(implicit evidence: EntityFormat[E, K], toKey: ToKey[K], datastore: Datastore): DatastoreOperation[Unit] =
    DatastoreOperation { () =>
      Right(datastore.delete(toKey.toKey(key, new KeyFactoryFacade(datastore.newKeyFactory().setKind(evidence.kind.name)))))
    // TODO can this return a different type??
      // TODO handle datastore errors
    }

  def list[E](implicit format: EntityFormat[E, _], datastore: Datastore): Query[E] = {
    val kind = format.kind.name
    val queryBuilder =
      com.google.cloud.datastore.Query.newEntityQueryBuilder().setKind(kind)
    DatastoreQuery(queryBuilder)
  }

  def project[E]()(implicit format: EntityFormat[E, _], datastore: Datastore): Project[E] = Project()

  def run[A](operation: DatastoreOperation[A]): Either[DatastoreError, A] = operation.get()

  def runAsync[A](operation: DatastoreOperation[A])(implicit executionContext: ExecutionContext): Future[Either[DatastoreError, A]] = Future(run(operation))

  def runAsyncF[A](operation: DatastoreOperation[A])(implicit executionContext: ExecutionContext): Future[A] = runAsync(operation).flatMap{
    case Right(a) => Future.successful(a)
    case Left(error) => Future.failed(new RuntimeException(error.toString))
  }

}
