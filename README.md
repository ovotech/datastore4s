# Datastore4s
[![CircleCI Badge](https://circleci.com/gh/ovotech/datastore4s/tree/master.svg?style=shield)](https://circleci.com/gh/ovotech/datastore4s/tree/master)

Datastore4s is a library to make using [Datastore](https://cloud.google.com/datastore/docs/) with Scala 
simpler and less error-prone by abstracting away the lower level details of datastores entities.

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

case class Person(firstName: String, lastName: String, age: Int)

object PersonRepository extends DefaultDatastoreSupport {

  implicit val personFormat = 
    EntityFormat[Person, String]("person-kind")(p => p.firstName + p.lastName)

  override def dataStoreConfiguration = 
    DataStoreConfiguration("my-project", "some-namespace")
    
  def storePerson(person: Person): Persisted[Person] = put(person)
    
  def streamPeople: Stream[Either[DatastoreError, Person]] = list[Person].stream()

}

```

## Recommended Usage

While the internal functions can be called explicitly it is much simpler to create your own `Datastore4sSupport` trait to 
hold implicit configuration and mix that into your client code.

```scala
trait Datastore4sSupport extends DefaultDatastoreSupport {
   // EntityFormats, ValueFormats, keys etc.
}

object Repository extends Datastore4sSupport {
  
}
```

## Entities

### Entity Formats

To be able to persist and read entities from google datastore simply create your case class
and use the EntityFormat macro. You need to provide:
 
- the type of the entity and the key
- a string of the kind under which you want your entities to be stored.
- a function between the entity and key type which will be used to create the Key for that entity

Formats for keys and fields will be resolved implicitly.

#### Sealed Trait Hierarchies

EntityFormats can also be created for sealed trait hierarchies that only contain case classes. A field `"type"`
will be used on the entity to determine which subtype the entity represents, for example:

```scala
sealed trait Entity { val name: String }

case class LongEntity(name: String, long: Long) extends Entity

case class DoubleEntity(name: String, double: Double) extends Entity

implicit val format = EntityFormat[Entity, String]("sealed-kind")(_.name)
```

### Custom Keys

The key types that are supported by default are `String` and `java.lang.Long`. If 
your EntityFormat uses anything other than these two types you will need to provide an implicit
`ToKey[A]` for your format.

```scala
import com.ovoenergy.datastore4s._
import com.google.cloud.datastore.Key

object Repository {
  
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

### Ancestors

To use ancestors in keys and queries create an implicit `ToAncestor[A]` for your types. For example:

```scala
import com.ovoenergy.datastore4s._
import com.google.cloud.datastore.Key

object Repository {
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

### Value Formats

`ValueFormat[A]` is used to determine how to store a type as a datastore value in both persistence and queries. 
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

There is a utility function available for creating your own value formats from functions to and from a type for which a 
format already exists in implicit scope:

```scala
import com.ovoenergy.datastore4s.internal.ValueFormat

case class CustomString(innerValue: String)

implicit val format = ValueFormat.formatFromFunctions(CustomString.apply)(_.innerValue)
// Again DefaultDatastoreSupport contains a utility method fieldFormatFromFunctions
```

In the case where it is possible the creation of your custom type may fail when passed a value from datastore simply return
an `Either[String, A]` from your function:

```scala
import com.ovoenergy.datastore4s.internal.ValueFormat

class PositiveInteger(val value: Int)

object PositiveInteger { 
  def apply(int: Int): Either[String, PositiveInteger] = 
    if(int <= 0) Left("whoops not positive") else Right(new PositiveInteger(int))
}

implicit val format = ValueFormat.formatFromFunctionsEither(PositiveInteger.apply)(_.value)
// Again DefaultDatastoreSupport contains a utility method formatFromFunctionsEither
```

### Custom Field Types

Usually a custom ValueFormat will suffice. In a very few cases you may wish to customise how a field is turned into and retrieved
from a Value. This is what the `FieldFormat[A]` trait is for. Two such cases are:

#### Case Classes

If you have a field that is a custom case class that is not itself an entity that is comprised of fields for which formats 
are already in implicit scope there is a macro to generate a format that will nest the fields of that case class using dot notation:

```scala
import com.ovoenergy.datastore4s.NestedFieldFormat

case class Department(name:String, departmentHead: String)
implicit val format = NestedFieldFormat[Department]
```

Using the nested format above an Employee entity of type:

```scala
case class Employee(name: String, age: Int, department: Department)
```

Would be serialised to have properties:

- name of type String
- age of type Int
- department.name of type String
- department.departmentHead of type String

#### Sealed Trait Hierarchies

Similarly to create a field format for a sealed trait heirarchy composed of only case clases simply use the `SealedFieldFormat`
macro, this will store a nested `fieldname.type` field on the entity to determine what subtype the field is. 

**TODO: Possibly allow objects in the hierarchy?**

## Datastore Actions

Actions can be performed directly using the `DatatstoreService` object, but you will need to implicitly provide the Datastore
object (this can be created by calling `implicit val datastore = DatastoreService.createDatastore(config)`) but it is much
preferable to simply extend the `DefaultDatastoreSupport` trait.

**Note: This design is likely to change so that queries and other operations
are represented by a Monad expressing an action to be perfomed rather than being
performed at the point functions are called. E.g. Free Monad**

### Persisting Entities

Simply call the `put` function with an implicit `EntityFormat[E, _]` in scope. You will get a `Persisted[E]` in return.

### Find One

To perform a findOne operation, simply provide the expected key and the entityType. If no implicit evidence can be found that 
they key type matches to the entity the query will not compile.

```scala
val oli: Option[Either[DatastoreError, Person]] = findOne("OliBoyle")
// OR
val oli = findOne[Person, String]("OliBoyle")
```

### List

To return all entities for a type you can either call `list[Type].stream` to return a `Stream[Either[DatastoreError, Type]]`
or `list[Type].sequenced` to return a `Either[DatastoreError, Seq[Type]]` depending on which result type you prefer.

It is also possible to apply two kinds of filters to these queries:

- Ancestor filters: `list[Person].withAncestor(Department("IT")).stream()` which will use the implicit `ToAncestor[A]` in scope
- Propterty filters: `list[Person].withPropertyEq("department", Department("IT")).stream()` which will use the implicit `ValueFormat[A]` in scope

**Note: The property filters are stringly typed and currently not checked at compile time.**

### Projections

Projections from an entity are possible. However they currently rely on stringly typed mappings that are not yet checked 
at compile time. I am looking to improve this in the future. A projection query requires a valid entity type and any type
whose fields are a subset of the entity's fields. 

**Note: The exact type of the fields does not have to match, so long as the underlying datastore value types match. I will
try to elevate this check to compile time in a further release if possible.**

The type being projected into will need an implicit `FromEntity[A]` in scope. `EntityFormat[E, _]` extends `FromEntity[E]`
so if your projection is to an entity it will already be in scope. If not the `FromEntity[E]` macro will easily apply to 
case classes and sealed hierarchies of case classes.

```scala

case class Person(firstName: String, lastName: String, age: Int, height: Double, weight: Double)
case class WeightAgeRow(personAge: Int, personWeight: Double)

import com.ovoenergy.datastore4s._

object Repository extends DefaultDatastoreSupport {
  implicit val personFormat = EntityFormat[Person, String]("person-kind")(p => p.firstName + p.lastName)
  implicit val rowFromEntity = FromEntity[WeightAgeRow]

  def rows = project[Person].into[WeightAgeRow].mapping("age" -> "personAge", "weight" -> "personWeight").stream()
  
  override def dataStoreConfiguration = 
      DataStoreConfiguration("my-project", "some-namespace")
}
```

After the `mapping` function call projcetion queries can have the same filters applied to them as regular ones. 

## Further Work

- Default Values?
- Migration?
- Check Stringly typed calls at compile time?
- Batch operations, transactions and other datastore features.

## Feedback And Contribution

## Disclaimer

This API is in its early stages and breaking changes may occur. It should not be considered production-ready.