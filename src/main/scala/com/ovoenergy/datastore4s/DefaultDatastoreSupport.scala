package com.ovoenergy.datastore4s

import com.ovoenergy.datastore4s.internal.{DatastoreError, ValueFormat}

import scala.concurrent.{ExecutionContext, Future}

trait DefaultDatastoreSupport {

  def dataStoreConfiguration: DataStoreConfiguration

  private implicit val datastore =
    DatastoreService.createDatastore(dataStoreConfiguration)
  implicit val keyFactorySupplier = () => datastore.newKeyFactory()

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

  def put[E](entity: E)(implicit format: EntityFormat[E, _]): DatastoreOperation[Persisted[E]] =
    DatastoreService.put(entity)

  def delete[E, K](key: K)(implicit evidence: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Unit] =
    DatastoreService.delete(key)

  def list[E]()(implicit format: EntityFormat[E, _]): Query[E] =
    DatastoreService.list

  def findOne[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Either[DatastoreError, Option[E]]] =
    DatastoreService.findOne(key)

  def project[E]()(implicit format: EntityFormat[E, _]): Project[E] =
    DatastoreService.project[E]

  def run[A](operation: DatastoreOperation[A]): A = DatastoreService.run(operation)

  def runAsync[A](operation: DatastoreOperation[A])(implicit executionContext: ExecutionContext): Future[A] =
    DatastoreService.runAsync(operation)

}
