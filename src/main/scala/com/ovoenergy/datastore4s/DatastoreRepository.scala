package com.ovoenergy.datastore4s

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

trait DefaultDatastoreRepository extends DatastoreRepository with DefaultFormats

trait DatastoreRepository {

  def datastoreConfiguration: DatastoreConfiguration

  private lazy implicit val datastoreService: DatastoreService = DatastoreService(datastoreConfiguration)

  @deprecated("Use formatFrom instead", "0.1.5")
  def formatFromFunctions[A, B](constructor: B => A)(extractor: A => B)(implicit existingFormat: ValueFormat[B]): ValueFormat[A] =
    ValueFormat.formatFromFunctions(constructor)(extractor)

  def formatFrom[A, B](constructor: B => A)(extractor: A => B)(implicit format: ValueFormat[B]): ValueFormat[A] =
    ValueFormat.formatFrom(constructor)(extractor)

  @deprecated("Use failableFormatFrom instead", "0.1.5")
  def formatFromFunctionsEither[A, B](
    constructor: B => Either[String, A]
  )(extractor: A => B)(implicit format: ValueFormat[B]): ValueFormat[A] =
    ValueFormat.formatFromFunctionsEither(constructor)(extractor)

  def failableFormatFrom[A, B](constructor: B => Either[String, A])(extractor: A => B)(implicit format: ValueFormat[B]): ValueFormat[A] =
    ValueFormat.failableFormatFrom(constructor)(extractor)

  def toStringAncestor[A](kind: String)(f: A => String): ToAncestor[A] =
    ToAncestor.toStringAncestor(kind)(f)

  def toLongAncestor[A](kind: String)(f: A => Long): ToAncestor[A] =
    ToAncestor.toLongAncestor(kind)(f)

  def toNamedKey[A](f: A => NamedKey): ToNamedKey[A] = new ToNamedKey[A] {
    override def toKey(value: A) = f(value)
  }

  def toIdKey[A](f: A => IdKey): ToIdKey[A] = new ToIdKey[A] {
    override def toKey(value: A) = f(value)
  }

  def put[E, K](entity: E)(implicit format: EntityFormat[E, K], toKey: ToKey[K, _]): DatastoreOperation[Persisted[E]] =
    DatastoreService.put(entity)

  def putAll[E, K](entities: Seq[E])(implicit format: EntityFormat[E, K], toKey: ToKey[K, _]): DatastoreOperation[Seq[Persisted[E]]] =
    DatastoreService.putAll(entities)

  def save[E, K](entity: E)(implicit format: EntityFormat[E, K], toKey: ToKey[K, _]): DatastoreOperation[Persisted[E]] =
    DatastoreService.save(entity)

  def saveAll[E, K](entities: Seq[E])(implicit format: EntityFormat[E, K], toKey: ToKey[K, _]): DatastoreOperation[Seq[Persisted[E]]] =
    DatastoreService.saveAll(entities)

  def delete[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K, _]): DatastoreOperation[K] =
    DatastoreService.delete(key)

  def deleteAll[E, K](keys: Seq[K])(implicit format: EntityFormat[E, K], toKey: ToKey[K, _]): DatastoreOperation[Seq[K]] =
    DatastoreService.deleteAll(keys)

  def list[E]()(implicit format: EntityFormat[E, _]): Query[E] =
    DatastoreService.list

  def findOne[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K, _]): DatastoreOperation[Option[E]] =
    DatastoreService.findOne(key)

  def findOneByName[E, K](name: String)(implicit format: EntityFormat[E, K], toKey: ToNamedKey[K]): DatastoreOperation[Option[E]] =
    DatastoreService.findOneByName(name)

  def findOneById[E, K](id: Long)(implicit format: EntityFormat[E, K], toKey: ToIdKey[K]): DatastoreOperation[Option[E]] =
    DatastoreService.findOneById(id)

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
