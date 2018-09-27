package com.ovoenergy.datastore4s

import com.google.cloud.datastore.Key

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

/** Datastore Repository that imports extra default ValueFormats for types that have multiple serialisation options e.g. BigDecimal */
trait DefaultDatastoreRepository extends DatastoreRepository with DefaultFormats

trait DatastoreRepository {

  /** Configure the connection to datastore. Either use FromEnvironmentVariables, one of the DatastoreConfiguration.apply functions
    * or a DatastoreOptions object.
    */
  def datastoreConfiguration: DatastoreConfiguration

  lazy implicit val datastoreService: DatastoreService = DatastoreService(datastoreConfiguration)

  /** Create a new ValueFormat from an existing one using the given transformation functions */
  def formatFrom[A, B](constructor: B => A)(extractor: A => B)(implicit format: ValueFormat[B]): ValueFormat[A] =
    ValueFormat.formatFrom(constructor)(extractor)

  /** Create a new ValueFormat from an existing one using the given transformation functions where deserialisation can fail */
  def failableFormatFrom[A, B](constructor: B => Either[String, A])(extractor: A => B)(implicit format: ValueFormat[B]): ValueFormat[A] =
    ValueFormat.failableFormatFrom(constructor)(extractor)

  /** Create a new ToAncestor for the type [A] that will create an ancestor with a string name */
  def toStringAncestor[A](kind: String)(f: A => String): ToAncestor[A] =
    ToAncestor.toStringAncestor(kind)(f)

  /** Create a new ToAncestor for the type [A] that will create an ancestor with a long id */
  def toLongAncestor[A](kind: String)(f: A => Long): ToAncestor[A] =
    ToAncestor.toLongAncestor(kind)(f)

  /** Create a ToKey[A] to create datastore keys for entities */
  def toKey[A](toKey: (A, KeyFactory) => Key): ToKey[A] = (a, k) => toKey(a, k)

  /** Upsert Entity */
  def put[E, K](entity: E)(implicit toEntityComponents: ToEntityComponents[E, K], toKey: ToKey[K]): DatastoreOperation[Persisted[E]] =
    DatastoreService.put(entity)

  /** Upsert all Entities */
  def putAll[E, K](entities: Seq[E])(implicit toEntityComponents: ToEntityComponents[E, K],
                                     toKey: ToKey[K]): DatastoreOperation[Seq[Persisted[E]]] =
    DatastoreService.putAll(entities)

  /** Persist entity only if it does not exist, will fail if an entity already exists with the expected key */
  def save[E, K](entity: E)(implicit toEntityComponents: ToEntityComponents[E, K], toKey: ToKey[K]): DatastoreOperation[Persisted[E]] =
    DatastoreService.save(entity)

  /** Persist all entities, will fail if any entity already exists with an expected key */
  def saveAll[E, K](entities: Seq[E])(implicit toEntityComponents: ToEntityComponents[E, K],
                                      toKey: ToKey[K]): DatastoreOperation[Seq[Persisted[E]]] =
    DatastoreService.saveAll(entities)

  /** Delete the entity with the given key, this will pass regardless of whether the entity exists in datastore or not */
  def delete[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[K] =
    DatastoreService.delete[E, K](key)

  /** Deletes an entity with the given key if it exists, fails if the entity does not exist before the delete */
  def safeDelete[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[K] =
    DatastoreService.safeDelete[E, K](key)

  /** Delete the entity, this will pass regardless of whether the entity exists in datastore or not */
  def deleteEntity[E, K](entity: E)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[K] =
    DatastoreService.deleteEntity[E, K](entity)

  /** Deletes an entity if it exists, fails if the entity does not exist before the delete */
  def safeDeleteEntity[E, K](
    entity: E
  )(implicit format: EntityFormat[E, K], fromEntity: FromEntity[E], toKey: ToKey[K]): DatastoreOperation[K] =
    DatastoreService.safeDeleteEntity[E, K](entity)

  /** Delete all the entities with the given keys, this will pass regardless of whether the entities exists in datastore or not */
  def deleteAll[E, K](keys: Seq[K])(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Seq[K]] =
    DatastoreService.deleteAll[E, K](keys)

  /** Delete all the entities, this will pass regardless of whether the entities exists in datastore or not */
  def deleteAllEntities[E, K](entities: Seq[E])(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Seq[K]] =
    DatastoreService.deleteAllEntities[E, K](entities)

  /** Create a [[Query]] for the entity type */
  def list[E]()(implicit format: EntityFormat[E, _]): Query[E] =
    DatastoreService.list

  /** Find the entity with the given key */
  def findOne[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): DatastoreOperation[Option[E]] =
    DatastoreService.findOne(key)

  /** Create a projection [[Query]] from the entity type [E] into the projection type [A]. You must pass a mapping from
    * entity property to projection property for every property on the projection type.
    *
    * {{{
    * case class Person(firstName: String, lastName: String, age: Int, height: Double)
    * case class PersonProjection(firstName: String, ageInYears: Int)
    *
    * implicit val personFormat = EntityFormat[Person, String]("People")(p => p.firstName + p.lastName)
    * implicit val fromProjection = FromEntity[PersonProjection]
    *
    * // Required to map firstName to firstName even though names are the same
    * projectInto[Person, PersonProjection]("firstName" -> "firstName", "age" -> "ageInYears")
    *
    * // This will fail at runtime since the firstName field will not be on the projection
    * projectInto[Person, PersonProjection]("age" -> "ageInYears")
    * }}}
    */
  def projectInto[E, A](firstMapping: (String, String), remainingMappings: (String, String)*)(implicit format: EntityFormat[E, _],
                                                                                              fromEntity: FromEntity[A]): Query[A] =
    DatastoreService.projectInto(firstMapping, remainingMappings: _*)

  /** Run the operation in a transaction, rolling back any changes if the operation fails */
  def transactionally[A](operation: DatastoreOperation[A]): DatastoreOperation[A] = DatastoreService.transactionally(operation)

  /** Run the operation synchronously */
  def run[A](operation: DatastoreOperation[A]): Either[DatastoreError, A] = DatastoreOperationInterpreter.run(operation)

  /** Run the operation synchronously mapping a Left into a Failure */
  def runF[A](operation: DatastoreOperation[A]): Try[A] = DatastoreOperationInterpreter.runF(operation)

  /** Run the operation asynchronously */
  def runAsync[A](operation: DatastoreOperation[A])(implicit executionContext: ExecutionContext): Future[Either[DatastoreError, A]] =
    DatastoreOperationInterpreter.runAsync(operation)

  /** Run the operation asynchronously mapping a Left into a Failure */
  def runAsyncF[A](operation: DatastoreOperation[A])(implicit executionContext: ExecutionContext): Future[A] =
    DatastoreOperationInterpreter.runAsyncF(operation)

}
