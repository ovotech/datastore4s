# Datastore Operations

Here is a list of the Datastore Operations inherited from the `DatastoreRepository` trait:

- `put[A](entity: A)` will persist an entity using its entity format, replacing any entity with the same key. Returns `DatastoreOperation[Persisted[A]]`.
- `putAll[A](entities: Seq[A])` will persist all entities in a batch using the entity format for `A`, replacing any entities
  with the same keys. Returns `DatastoreOperation[Seq[Persisted[A]]]`.
- `save[A](entity: A)` will persist an entity using its entity format, it will return an error if an entity already exists
  with the same key. Returns `DatastoreOperation[Persisted[A]]`.
- `saveAll[A](entities: Seq[A])` will persist all entities in a batch using the entity format for `A`, returning an error if
  any entities with the same keys exist. Returns `DatastoreOperation[Seq[Persisted[A]]]`.
- `delete[E, K](key: K)` will delete the entity with the given key. Please note if no entity exists with the given key
  a success will still be returned. Returns `DatastoreOperation[K]`.
- `safeDelete[E, K](key: K)` will delete the entity with the given key, failing if the entity did not exist prior to delete.
  Returns `DatastoreOperation[K]`. This operation performs a find prior to delete.
- `deleteEntity[E, K](entity: E)` will delete the entity passed. Please note if the passed entity does not exist in datastore
  a success will still be returned. Returns `DatastoreOperation[K]` of the key of the deleted entity.
- `safeDeleteEntity[E, K](entity: E)` will delete the entity passed, failing if the entity did not exist prior to delete.
  Returns `DatastoreOperation[K]`. This operation performs a find prior to delete.
- `deleteAll[E, K](keys: Seq[K])` will delete all the entities with the given keys. Please note if no entity exists with
  any given key a success will still be returned. Returns `DatastoreOperation[Seq[K]]`.
- `deleteAllEntities[E, K](entities: Seq[E])` will delete all the entities. Please note if an entity does not exist in datastore
  any given key a success will still be returned. Returns `DatastoreOperation[Seq[K]]` of the entity keys.
- `findOne[E, K](key: K)` returns a `Option` of the entity with the given key. Returns `DatastoreOperation[Option[E]]`.

## Execution

Datastore operations can be executed using interpreting functions, each of which requires an implicit `DatastoreService`.

- Synchronously
  - `run` which will run the operation synchronously and return `Either[DatastoreError, A]`
  - `runF` which will run the operation synchronously and return `Try[A]`, turning a `Left` into a `Failure`
- Asynchronous (Also requires an implicit `ExecutionContext`)
  - `runAsync` which will run the operation asynchronously and return `Future[Either[DatastoreError, A]]`
  - `runAsyncF` which will run the operation asynchronously and return `Future[A]`, flattening a `Left` into a `Failure`

## Queries

To create a query use the `list[E]` function. A query can be turned into a `DatastoreOperation` using one of:

- `sequenced` which will return `DatastoreOperation[Seq[A]]` where all entity errors are combined into one
- `stream` which will return `DatastoreOperation[Stream[Either[DatastoreError, A]]]` which is useful if you need the individual
  errors for each entity or wish to simply log the errors and only use valid entities.

You can add the following filters to the query:

- `withAncestor[A](ancestor: A)` filters the results to only entities with the given ancestor, there must be a `ToAncestor[A]` in scope
- `withPropertyEq[A](fieldName: String, value: A)` filters the results to just entities the given property equal to the
  given value, there must be a `ValueFormat[A]` in scope. The field name and type cannot be checked at compile time.
- `withPropertyLessThan[A](fieldName: String, value: A)` filters the results to just entities the given property less than the given value,
  there must be a `ValueFormat[A]` in scope. The field name and type cannot be checked at compile time.
- `withPropertyLessThanEq[A](fieldName: String, value: A)` filters the results to just entities the given property less than or equal to the given value,
  there must be a `ValueFormat[A]` in scope. The field name and type cannot be checked at compile time.
- `withPropertyGreaterThan[A](fieldName: String, value: A)` filters the results to just entities the given property greater than the given value,
  there must be a `ValueFormat[A]` in scope. The field name and type cannot be checked at compile time.
- `withPropertyGreaterThanEq[A](fieldName: String, value: A)` filters the results to just entities the given property greater than or equal to the given value,
  there must be a `ValueFormat[A]` in scope. The field name and type cannot be checked at compile time.

Queries can be ordered by properties by using `orderByAscending(property: String)` or `orderByDescending(property: String)`,
note that in the case of `Option[A]`, `None` will be considered less than every `Some(a)`.

You can also limit the number of results by calling `limit(limit: Int)`.

## Projection Queries

Projections can be useful when you need to provide a limited view into a datastore kind. You can create a projection query using
`projectInto[E, Projection](entityField -> projectionField...)`. You will need to provide:

- An implicit `EntityFormat[E, _]`
- An implicit `FromEntity[Projection]`, the `FromEntity` typeclass represents the deserialisation of a datastore entity.
  There is a `FromEntity` macro to generate these typeclasses for your projection views.
- A list of mappings between the entity fields you want to project and the field names on your projection views.
  These mappings are not checked at compile time. The field mappings do not have to match type but the underlying datastore
  type must match up. For example:

```scala
import com.ovoenergy.datastore4s._

case class CustomType(value: String)
object CustomType {
  // Will be stored as a String in datastore
  implicit val format = ValueFormat.formatFrom(CustomType.apply)(_.value)
}

case class Entity(projectionMapping: CustomType, key: Long)
case class Projected(mapped: String)

// This is an acceptable mapping because the underlying datastore type of both fields is String
projectInto[Entity, Projected]("projectionMapping" -> "mapped")
```

## Transaction Support

Any datastore operation can be wrapped within a transaction. For example:

```scala
def transactionalPersist(entity1: TypeOne, entity2: TypeTwo): DatastoreOperation[(TypeOne, TypeTwo, Mapping)]=
  transactionally { for {
    persisted1 <- put(entity1)
    persisted2 <- put(entity2)
    persistedMapping <- save(Mapping(entity1, entity2))
  } yield (persisted1, persisted2, persistedMapping) }
```

If the operation inside the transaction fails an attempt will be made to roll it back.

## Warning

You should still be aware of the [data consistency rules](https://cloud.google.com/datastore/docs/concepts/structuring_for_strong_consistency)
of datastore as they will still affect the queries and transactional operations. For example `list[Entity].sequenced()`
is an eventually consistent query but `list[Entity].withAncestor("foo").sequenced()` is strongly consistent since all queries
using an ancestor are strongly consistent.
