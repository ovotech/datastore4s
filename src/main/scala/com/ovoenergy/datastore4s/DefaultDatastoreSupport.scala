package com.ovoenergy.datastore4s

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait DefaultDatastoreSupport {

  def dataStoreConfiguration: DataStoreConfiguration

  private implicit val datastore =
    DatastoreService.createDatastore(dataStoreConfiguration)

  def formatFromFunctions[A, B](constructor: B => A)(extractor: A => B)(implicit existingFormat: ValueFormat[B]): ValueFormat[A] =
    ValueFormat.formatFromFunctions(constructor)(extractor)

  def formatFromFunctionsEither[A, B](
    constructor: B => Either[String, A]
  )(extractor: A => B)(implicit format: ValueFormat[B]): ValueFormat[A] =
    ValueFormat.formatFromFunctionsEither(constructor)(extractor)

  def toStringAncestor[A](kind: String)(f: A => String): ToAncestor[A] =
    ToAncestor.toStringAncestor(kind)(f)

  def toLongAncestor[A](kind: String)(f: A => Long): ToAncestor[A] =
    ToAncestor.toLongAncestor(kind)(f)

  def put[E, K](entity: E)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Persisted[E]] =
    DatastoreService.put(entity)

  def delete[E, K](key: K)(implicit evidence: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[K] =
    DatastoreService.delete(key)

  def list[E]()(implicit format: EntityFormat[E, _]): Query[E] =
    DatastoreService.list

  def findOne[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Option[E]] =
    DatastoreService.findOne(key)

  def project[E]()(implicit format: EntityFormat[E, _]): Project[E] =
    DatastoreService.project[E]

  def run[A](operation: DatastoreOperation[A]): Either[DatastoreError, A] = DatastoreService.run(operation)

  def runF[A](operation: DatastoreOperation[A]): Try[A] = DatastoreService.runF(operation)

  def runAsync[A](operation: DatastoreOperation[A])(implicit executionContext: ExecutionContext): Future[Either[DatastoreError, A]] =
    DatastoreService.runAsync(operation)

  def runAsyncF[A](operation: DatastoreOperation[A])(implicit executionContext: ExecutionContext): Future[A] =
    DatastoreService.runAsyncF(operation)

}
