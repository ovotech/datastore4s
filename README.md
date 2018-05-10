# Datastore4s
[![CircleCI](https://circleci.com/gh/ovotech/datastore4s/tree/master.svg?style=svg)](https://circleci.com/gh/ovotech/datastore4s/tree/master)
[![Download](https://api.bintray.com/packages/ovotech/maven/datastore4s/images/download.svg) ](https://bintray.com/ovotech/maven/datastore4s/_latestVersion)

Datastore4s is a scala library for [GCP Datastore](https://cloud.google.com/datastore/docs/). Datastore4s
hides the complexities of the Datastore API and removes boilerplate code making it simpler and less error-prone to use.

## Getting Started

The library is available in the OVO Bintray repository. Add this snippet to your build.sbt to use it.

```sbtshell
resolvers += Resolver.bintrayRepo("ovotech", "maven")
libraryDependencies += "com.ovoenergy" %% "datastore4s" % "0.1.4",
```

### A Simple Example

Here is a basic example of using datastore4s to persist and list a case class representing a `Person` using their first
and last name to generate the datastore key.

```scala
import com.ovoenergy.datastore4s._

case class Person(firstName: String, lastName: String, age: Int)

object PersonRepository extends DatastoreRepository {

  override def datastoreConfiguration = DatastoreConfiguration("my-project", "my-namespace")

  implicit val personFormat = EntityFormat[Person, String]("person-kind")(p => p.firstName + p.lastName)
    
  def storePerson(person: Person): Either[DatastoreError, Persisted[Person]] = run(put(person))
    
  def allPeople: Either[DatastoreError, Seq[Person]] = run(list[Person].sequenced())

}
```

To use datastore4s simply extend the `DatastoreRepository` trait and supply the implicit formats needed to persist entities.

## Datastore Operations

Datastore operations do not execute immediately, instead they describe an action to be performed by a `DatastoreService`.
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

Some simple operations include:

- `put[E](entity: E)` which performs an upsert on the entity passed.
- `delete[E, K](key: K)` which deletes the entity with the given key if it exists.
- `findOne[E, K](key: K)` which returns an `Option` of the entity with the given key.
- `list[E].sequenced()` which returns a `Seq` of all the entities of the given type.

Operations can then be executed synchronously using `run` or asynchronously using `runAsync`. For a full list of operations
and interpreters see the [Operations](./docs/Operations.md) documentation.

## Entities

Entity (de)serialisation is based on three `Format` traits.
- `EntityFormat`s which determine how a scala type is turned into a datastore entity. An `Entity` can have many `Field`s.
- `FieldFormat`s which determine how a field of an entity is stored in datastore. A `Field` can have many `Value`s.
- `ValueFormat`s which determine the mapping between a scala type and a datastore type.

### Entity Formats

To be able to persist and read entities from google datastore simply create your case class and use the `EntityFormat` macro.
The same macro can be used to create `EntityFormat`s for sealed trait hierarchies that only contain case classes. An additional 
field `"type"` will be used on the entity to determine which subtype in the hierarchy the entity represents.

To use the macro you need to provide:
 
- the type of the entity and the type of the key.
- a string of the kind under which you want your entities to be stored.
- a function between the entity type and key type which will be used to create the unique key for that entity.

For example:

`EntityFormat[Person, String]("person-kind")(person => person.name)`

**Warning:** Key types cannot be primitive. Out of the box only `String` and `java.lang.Long` keys are supported, if you 
need a custom type then see the [Datastore Key Customisation](./docs/CustomKeys.md) documentation. 

### Value Formats

`ValueFormat[A]` is used to determine how to store (and retrieve) a type as a datastore value in both persistence and queries. 
There are multiple `ValueFormat[A]`s already implicitly available for: 

- `String` which is stored as a `StringValue`
- `Long` which is stored as a `LongValue`
- `Int` which is stored as a `LongValue`
- `Boolean` which is stored as a `BooleanValue`
- `Double` which is stored as a `DoubleValue`
- `Float` which is stored as a `DoubleValue`
- `Option[A]` for any `[A]` for which a format exists, which is stored as a `NullValue` or the expected value for `A`
- `Seq[A]` for any `[A]` for which a format exists, which is stored as a `ListValue` of the expected value for `A`
- `Set[A]` for any `[A]` for which a format exists,  which is stored as a `ListValue` of the expected value for `A`
- `com.google.cloud.Timestamp`
- `com.google.cloud.datastore.Blob`
- `com.google.cloud.datastore.LatLng`

There are also formats available that can be brought into implicit scope (explicitly or by inheriting the `DefaultFormats` or `DefaultDatastoreRepository` traits) for: 
- `Array[Byte]` in the form of `ValueFormat.byteArrayValueFormat`,  which is stored as a `com.google.cloud.datastore.Blob`
- `BigDecimal` in the form of `BigDecimalStringValueFormat` (or `ValueFormat.bigDecimalDoubleValueFormat` which is not in the default trait)
- `java.time.Instant` in the form of `ValueFormat.instantEpochMillisValueFormat`, which is stored as a `LongValue`

These are not implicit by default to allow your own implementations for those types.

#### Custom Types

There is a utility function available for creating your own value formats by providing functions to and from a type for which a 
format already exists in implicit scope:

```scala
import com.ovoenergy.datastore4s.ValueFormat

case class CustomString(innerValue: String)

object CustomString {
  implicit val format = ValueFormat.formatFrom(CustomString.apply)(_.innerValue)
  // DatastoreRepository contains an alias function formatFrom
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
    
  implicit val format = ValueFormat.failableFormatFrom(PositiveInteger.apply)(_.value)
  // DatastoreRepository contains an alias function failableFormatFrom
}
```

### Field Formats

When a field only contains one value a `FieldFormat` will be generated using the `ValueFormat`. For fields of type `Either[L, R]` 
a `FieldFormat` is generated using `ValueFormat[L]` and `ValueFormat[R]` and a value `"either_side"` of `"Left"` or `"Right"` is added.

#### Case Classes

If you have a field that is a custom case class that is comprised of fields for which `FieldFormat`s are already in implicit
scope there is a macro to generate a `FieldFormat` that will nest the fields of that case class using dots to separate the fields:

```scala
import com.ovoenergy.datastore4s.FieldFormat

case class Employee(name: String, age: Int, department: Department)
case class Department(name:String, departmentHead: String)
object Department {
  implicit val format = FieldFormat[Department]
}
```

Using the format above an Employee entity would be serialised to have values:

- name of type `String`
- age of type `Int`
- department.name of type `String`
- department.departmentHead of type `String`

#### Sealed Trait Hierarchies

Similarly to create a `FieldFormat` for a sealed trait hierarchy composed of only case classes and/or objects simply use the same macro,
this will store a nested `fieldname.type` value on the entity to determine what subtype the field is.

### For Those Who Hate Macros

If you do not want to use the macros you can create the formats yourself, it is however likely you will end up writing 
the same code that would have been generated by the macro. For example:

```scala
import com.ovoenergy.datastore4s._

case class Person(firstName: String, lastName: String, age: Int, job: Job)
case class Job(title: String, wage: BigDecimal)

object NonMacroExample {

  implicit object JobFormat extends FieldFormat[Job] {
    override def toEntityField(fieldName: String, value: Job): Field = Field(
        s"$fieldName.title" -> toValue(value.title),
        s"$fieldName.wage" -> toValue(value.wage)
      )

    override def fromEntityField(fieldName: String, entity: Entity) = for {
      title <- entity.fieldOfType[String](s"$fieldName.title")
      wage <- entity.fieldOfType[BigDecimal](s"$fieldName.wage")
    } yield Job(title, wage)
  }

  implicit object PersonFormat extends EntityFormat[Person, String] {
    override val kind = Kind("person")
    override def key(person: Person) = person.firstName + person.lastName

    override def toEntity(person: Person, builder: EntityBuilder): Entity =
      builder.add("firstName", person.firstName).add("lastName", person.lastName)
        .add("age", person.age).add("job", person.job).build()

    override def fromEntity(entity: Entity): Either[DatastoreError, Person] = for {
      firstName <- entity.fieldOfType[String]("firstName")
      lastName <- entity.fieldOfType[String]("lastName")
      age <- entity.fieldOfType[Int]("age")
      job <- entity.fieldOfType[Job]("job")
    } yield Person(firstName, lastName, age, job)
  }
}
```

The above is the same as: 

```scala
import com.ovoenergy.datastore4s._

case class Person(firstName: String, lastName: String, age: Int, job: Job)
case class Job(title: String, wage: BigDecimal)

object MacroExample {
  implicit val jobFormat = FieldFormat[Job]
  implicit val personFormat = EntityFormat[Person, String]("person")(person => person.firstName + person.lastName)
}
```

## Further Documentation
- [Operations](./docs/Operations.md)
- [Datastore Key Customisation](./docs/CustomKeys.md)
- [Configuring Your Repository](./docs/Configuration.md)
- [Entity Indexes](./docs/Indexes.md)
- [Examples](./examples/Examples.md)

## Feedback And Contribution

Feedback, Issues and PR's are welcome.
