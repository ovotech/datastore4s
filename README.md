# Datastore4s
[![CircleCI](https://circleci.com/gh/ovotech/datastore4s/tree/master.svg?style=svg)](https://circleci.com/gh/ovotech/datastore4s/tree/master)

Datastore4s is a library to make using [Datastore](https://cloud.google.com/datastore/docs/) with Scala 
simpler and less error-prone by abstracting away the lower level details of the datastore API.

## Getting Started

The library is available in the Bintray OVO repository. Add this snippet to your build.sbt to use it.

```sbtshell
import sbt._
import sbt.Keys.

resolvers += Resolver.bintrayRepo("ovotech", "maven")

libraryDependencies ++= {
  val datastore4sVersion = "0.1" // see the Maven badge above for the latest version
  Seq(
    "com.ovoenergy" %% "datastore4s" % datastore4sVersion,
  )
}

```

## A Contrived Example

What follows is a basic example of using datastore4s to persist and stream a simple
case class representing a Person using thier first and last name to generate their key.

```scala
import com.ovoenergy.datastore4s._
import scala.concurrent._

case class Person(firstName: String, lastName: String, age: Int)

object PersonRepository extends DatastoreRepository {

  implicit val personFormat = 
    EntityFormat[Person, String]("person-kind")(p => p.firstName + p.lastName)

  override def dataStoreConfiguration = 
    DataStoreConfiguration("my-project", "some-namespace")
    
  def storePerson(person: Person)(implicit executionContext: ExecutionContext): Future[Persisted[Person]] = 
    runAsyncF(put(person))
    
  def allPeople: Either[DatastoreError, Seq[Person]] = run(list[Person].sequenced())

}

```

While the internal functions can be called explicitly it is much simpler to extend the `DatastoreRepository` trait.

## Datastore Operations

### Execution

Datastore operations can be executed using 4 different functions, these are available in the `DatastoreRepository` trait.

1. Synchronous
    - `run` which will run the operation synchronously and return `Either[DatastoreError, A]`
    - `runF` which will run the operation synchronously and return `Try[A]`, turning a `Left` into a `Failure`
2. Asynchronous
    - `runAsync` which will run the operation asynchronously and return `Future[Either[DatastoreError, A]]`
    - `runAsyncF` which will run the operation asynchronously and return `Future[A]`, flattening a `Left` into a `Failure`

### Operations

1. `put[A](entity: A)` will persist an entity using its entity format, replacing any entity with the same key 
2. `add[A](entity: A)` will persist an entity using its entity format, it will return an error if an entity already exists with the same key
3. `delete[E, K](key: K)` will delete the entity with the given key
4. `findOne[E, K](key: K)` returns a `Option` of the entity with the given key
5. `list[E]` creates a query for the given entity type as long as an entity format is implicitly in scope 
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
        - `Stream[Either[DatastoreError, A]]` by calling `stream()` if you need the individual errors for each entity
        - `Seq[A]` by calling `sequenced()` where all entity errors are combined into one
6. `projectInto[E, Projection](entityField -> projectionField...)` creates a projection query from the given entity type 
 into the projection type using the given field mappings. There must be both an `EntityFormat[E, _]` and `FromEntity[Projection]`
 in scope. There is a `FromEntity[A]` macro. This function does not check that the field names or types match up at compile time.

Datastore Operations can also be combined in for comprehensions e.g:

```scala
import com.ovoenergy.datastore4s._
import com.ovoenergy.datastore4s.DatastoreService._

case class Person(firstName: String, lastName: String, age: Int)
object ForComprehensionExample {
  val operation: DatastoreOperation[Seq[Person]] = for {
    _ <- put(Person("oli", "boyle", 26))
    oli <- findOne[Person, String]("oliboyle")
    _ <- put(Person("john", "doe", 27))
    twentySevenYearOlds <- list[Person].withPropertyEq("age", 27).sequenced()
  } yield oli.toSeq ++ twentySevenYearOlds
}
```

## Entities

Entity (de)serialisaion is based on three `Format` traits.
- `ValueFormat`s which determines how a scala type is transformed into a datastore value
- `FieldFormat`s which determines how a field of an entity is stored in datastore
- `EntityFormat`s which determines how a scala type is turned into a datastore entity

### Value Formats

`ValueFormat[A]` is used to determine how to store (and retrieve) a type as a datastore value in both persistence and queries. 
There are multiple `ValueFormat[A]`s already implicitly available for: 

- `String`
- `Long`
- `Boolean`
- `Double`
- `Int`
- `Option[A]` for any `[A]` for which a format exists
- `Seq[A]` for any `[A]` for which a format exists
- `com.google.cloud.Timestamp`
- `com.google.cloud.datastore.Blob`
- `com.google.cloud.datastore.LatLng`

There are also formats available that can be brought into implicit scope for: 
- `Array[Byte]` in the form of `ByteArrayValueFormat`
- `BigDecimal` in the form of `BigDecimalStringValueFormat`
- `java.time.Instant` in the form of `InstantEpochMillisValueFormat`

These are not implicit by default to allow your own implementations for those types.

There is a utility function available for creating your own value formats by providing functions to and from a type for which a 
format already exists in implicit scope:

```scala
import com.ovoenergy.datastore4s.ValueFormat

case class CustomString(innerValue: String)

object CustomString {
  implicit val format = ValueFormat.formatFromFunctions(CustomString.apply)(_.innerValue)
  // DatastoreRepository contains a utility method formatFromFunctions
}
```

In the case where it is possible the creation of your custom type may fail when passed a value from datastore, simply return
an `Either[String, A]` from your function:

```scala
import com.ovoenergy.datastore4s.ValueFormat

class PositiveInteger(val value: Int)

object PositiveInteger { 
  def apply(int: Int): Either[String, PositiveInteger] = 
    if(int <= 0) Left("whoops not positive") else Right(new PositiveInteger(int))
    
  implicit val format = ValueFormat.formatFromFunctionsEither(PositiveInteger.apply)(_.value)
  // Again DefaultDatastoreSupport contains a utility method formatFromFunctionsEither
}
```

### Field Formats

Usually a custom `ValueFormat` will suffice, this will then simply be used to generate a field from that type with the name of
the field on your case class. In a very few cases you may wish to customise how a field is turned into and retrieved from 
the fields of an entitiy. This is what the `FieldFormat` trait is for. Implicitly formats are available for:

- Any type `[A]` for which a `ValueFormat[A]` is in scope
- `Either[L, R]` for any `[L]` and `[R]` for which a format exists (by using an `"either_side"` property)

There is also a macro which can be used to generate field formats for both case classes and sealed trait hierarchies.

#### Case Classes

If you have a field that is a custom case class that is not itself an entity that is comprised of fields for which formats 
are already in implicit scope there is a macro to generate a format that will nest the fields of that case class using dots 
to separate the fields:

```scala
import com.ovoenergy.datastore4s.FieldFormat

case class Employee(name: String, age: Int, department: Department)
case class Department(name:String, departmentHead: String)
object Department {
  implicit val format = FieldFormat[Department]
}
```

Using the nested format above an Employee entity would be serialised to have properties:

- name of type String
- age of type Int
- department.name of type String
- department.departmentHead of type String

#### Sealed Trait Hierarchies

Similarly to create a field format for a sealed trait hierarchy composed of only case classes and/or objects simply use the same macro,
this will store a nested `fieldname.type` field on the entity to determine what subtype the field is.

### Entity Formats

To be able to persist and read entities from google datastore simply create your case class and use the `EntityFormat` macro.
EntityFormats can also be created for sealed trait hierarchies that only contain case classes using the same macro. 
A field `"type"` will be used on the entity to determine which subtype the entity represents.
To use the macro you need to provide:
 
- the type of the entity and the key
- a string of the kind under which you want your entities to be stored
- a function between the entity and key type which will be used to create the Key for that entity

For example:

`EntityFormat[Person, String]("person-kind")(person => person.name)`

**NOTE: The Key type cannot be primitive currently due to a compilation error**

#### Custom Keys

The key types that are supported by default are `String` and `java.lang.Long`. If your EntityFormat uses anything other 
than these two types you will need to provide an implicit `ToKey[A]` for your format. For example:

```scala
import com.ovoenergy.datastore4s._
import com.google.cloud.datastore.Key

object CustomKeyExample {
  
  case class Employee(name: String, age: Int, department: String)
  
  case class CustomKey(name: String, department: String)

  implicit object CustomToKey extends ToKey[CustomKey] {
    override def toKey(value: CustomKey, keyFactory: KeyFactory): Key = 
      keyFactory.buildWithName(value.department + value.name)
  }
  
  implicit val format = EntityFormat[Employee, CustomKey]("employees")(e => CustomKey(e.name, e.department))
}
```

**Note: If using a `Long` field as an key, the Key type must be `java.lang.Long` but it the field on your case class can be simply `Long`.**

#### Ancestors

To use ancestors in keys and queries create an implicit `ToAncestor[A]` for your types using the `toStringAncestor` or
`toLongAncestor` function. For example:

```scala
import com.ovoenergy.datastore4s._
import com.google.cloud.datastore.Key

object AncestorExample {
  case class Department(name: String)
  
  // Provide the name of the kind and a function from A => String 
  // (Or A => Long in the case of a LongAncestor)
  implicit val departmentAncestor = 
    ToAncestor.toStringAncestor[Department]("Department")(_.name)
  
  case class CustomKey(name: String, department: Department)
  
  implicit object CustomToKey extends ToKey[CustomKey] {
    override def toKey(value: CustomKey, keyFactory: KeyFactory): Key = 
      keyFactory.addAncestor(value.department).buildWithName(value.name)
  }
  // The DefaultDatastoreSupport trait contains utility methods pointing to 
  // `toStringAncestor` and `toLongAncestor`
}
```

### For Those Who Hate Macros

If you do not want to use macros there is nothing wrong with creating the formats yourself, it is however likely you will
end up writing the same code that would have been generated. For example:

```scala
import com.ovoenergy.datastore4s._

case class Department(name: String)
case class Employee(name: String, age: Int, department: Department)

case class CustomKey(name: String, department: Department)

object NonMacroExample {

  // Other formats e.g. ancestor and key examples from above

  implicit object EmployeeFormat extends EntityFormat[Employee, CustomKey] {
  
    override val kind = Kind("employees")
    override def key(employee: Employee) = CustomKey(employee.name, employee.department)
    
    override def toEntity(record: Employee, builder: EntityBuilder): Entity = {
      builder.add("name", record.name).add("age", record.age)
        .add("department", record.department).build()
    }
    
    override def fromEntity(entity: Entity): Either[DatastoreError, Employee] = for {
      name <- entity.fieldOfType[String]("name")
      age <- entity.fieldOfType[Int]("age")
      department <- entity.fieldOfType[Department]("department")
    } yield Employee(name, age, department)
  
  }

}
```

## Feedback And Contribution

## Disclaimer

This API is in its early stages and breaking changes may occur. It should not be considered production-ready.

