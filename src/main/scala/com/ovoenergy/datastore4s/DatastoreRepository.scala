package com.ovoenergy.datastore4s

import com.google.cloud.datastore.Key

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait DefaultDatastoreRepository extends DatastoreRepository with DefaultFormats

trait DatastoreRepository {

  def dataStoreConfiguration: DataStoreConfiguration

  private lazy implicit val datastoreService: DatastoreService = DatastoreService(dataStoreConfiguration)

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

  def toKey[A](toKey: (A, KeyFactory) => Key): ToKey[A] = (a, k) => toKey(a, k)

  def put[E, K](entity: E)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Persisted[E]] =
    DatastoreService.put(entity)

  def putAll[E, K](entities: Seq[K])(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Seq[Persisted[E]]] =
    DatastoreService.putAll(entities)

  def save[E, K](entity: E)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Persisted[E]] =
    DatastoreService.save(entity)

  def saveAll[E, K](entities: Seq[K])(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Seq[Persisted[E]]] =
    DatastoreService.saveAll(entities)

  def delete[E, K](key: K)(implicit evidence: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[K] =
    DatastoreService.delete(key)

  def list[E]()(implicit format: EntityFormat[E, _]): Query[E] =
    DatastoreService.list

  def findOne[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Option[E]] =
    DatastoreService.findOne(key)

  def projectInto[E, A](firstMapping: (String, String), remainingMappings: (String, String)*)(implicit format: EntityFormat[E, _],
                                                                                              fromEntity: FromEntity[A]): Query[A] =
    DatastoreService.projectInto(firstMapping, remainingMappings: _*)

  def run[A](operation: DatastoreOperation[A]): Either[DatastoreError, A] = DatastoreOperationInterpreter.run(operation)

  def runF[A](operation: DatastoreOperation[A]): Try[A] = DatastoreOperationInterpreter.runF(operation)

  def runAsync[A](operation: DatastoreOperation[A])(implicit executionContext: ExecutionContext): Future[Either[DatastoreError, A]] =
    DatastoreOperationInterpreter.runAsync(operation)

  def runAsyncF[A](operation: DatastoreOperation[A])(implicit executionContext: ExecutionContext): Future[A] =
    DatastoreOperationInterpreter.runAsyncF(operation)

}
