# Datastore Operations

Datastore Operations inherited from the `DatastoreRepository` trait:

1. `put[A](entity: A)` will persist an entity using its entity format, replacing any entity with the same key. Returns `DatastoreOperation[Persisted[A]]`.
2. `putAll[A](entities: Seq[A])` will persist all entities in a batch using the entity format for `A`, replacing any entities
 with the same keys. Returns `DatastoreOperation[Seq[Persisted[A]]]`.
3. `save[A](entity: A)` will persist an entity using its entity format, it will return an error if an entity already exists 
with the same key. Returns `DatastoreOperation[Persisted[A]]`.
4. `saveAll[A](entities: Seq[A])` will persist all entities in a batch using the entity format for `A`, returning an error if
any entities with the same keys exist. Returns `DatastoreOperation[Seq[Persisted[A]]]`.
5. `delete[E, K](key: K)` will delete the entity with the given key. Please note if no entity exists with the given key 
a success will still be returned. Returns `DatastoreOperation[K]`.
6. `deleteAll[E, K](keys: Seq[K])` will delete all the entities with the given keys. Please note if no entity exists with 
any given key a success will still be returned. Returns `DatastoreOperation[Sqe[K]]`.
7. `findOne[E, K](key: K)` returns a `Option` of the entity with the given key. Returns `DatastoreOperation[Option[E]]`.

## Queries

8. `list[E]` creates a query for the given entity type as long as an entity format is implicitly in scope 
    - You can add filters to the query
        - `withAncestor[A](ancestor: A)` filters the results to only entities with the given ancestor, there must be a
        `ToAncestor[A]` in scope
        - `withPropertyEq[A](fieldName: String, value: A)` filters the results to just entities the given property equal 
        to the given value, there must be a `ValueFormat[A]` in scope. The field name and type cannot be checked at compile time
        - `withPropertyLessThan[A](fieldName: String, value: A)` filters the results to just entities the given property less than the given value,
         there must be a `ValueFormat[A]` in scope. The field name and type cannot be checked at compile time
        - `withPropertyLessThanEq[A](fieldName: String, value: A)` filters the results to just entities the given property less than or equal to the given value, 
        there must be a `ValueFormat[A]` in scope. The field name and type cannot be checked at compile time
        - `withPropertyGreaterThan[A](fieldName: String, value: A)` filters the results to just entities the given property greater than the given value,
        there must be a `ValueFormat[A]` in scope. The field name and type cannot be checked at compile time
        - `withPropertyGreaterThanEq[A](fieldName: String, value: A)` filters the results to just entities the given property greater than or equal to the given value,
        there must be a `ValueFormat[A]` in scope. The field name and type cannot be checked at compile time
    - Queries can return either:
        - `DatastoreOperation[Stream[Either[DatastoreError, A]]]` by calling `stream()` if you need the individual errors for each entity
        - `DatastoreOperation[Seq[A]]` by calling `sequenced()` where all entity errors are combined into one
9. `projectInto[E, Projection](entityField -> projectionField...)` creates a projection query from the given entity type 
 into the projection type using the given field mappings. There must be both an `EntityFormat[E, _]` and `FromEntity[Projection]`
 in scope. There is a `FromEntity[A]` macro. This function does not check that the field names or types match up at compile time. 
 Note that this operation is experimental and may be replaced/removed in future versions.

## Execution

Datastore operations can be executed using 4 different interpreting functions, each of which requires an implicit `DatastoreService`.

1. Synchronous
    - `run` which will run the operation synchronously and return `Either[DatastoreError, A]`
    - `runF` which will run the operation synchronously and return `Try[A]`, turning a `Left` into a `Failure`
2. Asynchronous (Also require implicit `ExecutionContext`)
    - `runAsync` which will run the operation asynchronously and return `Future[Either[DatastoreError, A]]`
    - `runAsyncF` which will run the operation asynchronously and return `Future[A]`, flattening a `Left` into a `Failure`


[BACK](../README.md)