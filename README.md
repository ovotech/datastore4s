# Datastore4s

Datastore4s is a library to make using [Datastore](https://cloud.google.com/datastore/docs/) with Scala 
simpler and less error-prone by abstracting away the lower level details of datastores entities.

**Note: This API is in its early stages and breaking changes may occur.**

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
case class representing a Person.

```scala
import com.ovoenergy.datastore4s._

case class Person(firstName: String, lastName: String, age: Int)

object PersonRepository extends DefaultDatastoreSupport {

  implicit val personFormat = 
    EntityFormat[Person, String]("person-kind")(p => p.firstName + p.lastName)

  override def dataStoreConfiguration = 
    DataStoreConfiguration("my-project", "some-namespace")
    
  def storePerson(person: Person): Persisted[Person] = put(person)
    
  def streamPeople: Stream[Person] = list[Person].stream()

}

```

## Entities

### Entity Formats

To be able to persist and read entities from google datastore simply create your case class
and use the EntityFormat macro. You need to provide:
 
- the type of the entity and the key
- a string of the kind under which you want your entities to be stored
- a function between the entity and key type which will be used to create the Key for that entity

Formats for keys and fields will be resolved implicitly.

### Custom Keys

The key types that are supported by default are `String` and `java.lang.Long`. If 
your EntityFormat uses anything other than these two types you will need to provide an implicit
`ToKey[A]` for your format.

**NOTE: I am trying to change it so that we can use `Long` instead of `java.lang.Long`
but there is a strange error when implicitly resolving `ToKey[Long]` that I need to fix**

```scala
import com.ovoenergy.datastore4s._
import com.google.cloud.datastore.Key

object Repository {
  
  case class Employee(name: String, age: Int, department: String)
  
  case class CustomKey(name: String, department: String)

  implicit object CustomToKey extends ToKey[CustomKey] {
    override def toKey(value: CustomKey, keyFactory: KeyFactory): Key = 
      keyFactory.addAncestor(StringAncestor(Kind("Department"), value.department)).buildWithName(value.name)
  }
  
  implicit val format = EntityFormat[Employee, CustomKey]("employees")(e => CustomKey(e.name, e.department))
}
```

### Ancestors

The `Ancestor` trait has two simple subtypes `StringAncestor` and `LongAncestor`. While you can use these directly
it is highly preferable to create an implicit `ToAncestor[A]` for a custom ancestor type. This means 
that the same type can be used for both creating keys and running queries and both will automatically have the same kind.

**NOTE: I may remove support for using Ancestors directly and demand a `ToAncestor[A]` to avoid problems relating to Kinds
and a divergence between keys and queries**

```scala
import com.ovoenergy.datastore4s._
import com.google.cloud.datastore.Key

object Repository {
  case class Department(name: String)
  
  // Provide the name of the kind and a function from A => String (Or A => Long in the case of a LongAncestor)
  implicit val departmentAncestor = ToAncestor.toStringAncestor[Department]("Department")(_.name)
  
  case class CustomKey(name: String, department: Department)
  
  implicit object CustomToKey extends ToKey[CustomKey] {
    override def toKey(value: CustomKey, keyFactory: KeyFactory): Key = 
      keyFactory.addAncestor(value.department).buildWithName(value.name)
  }
  // The DefaultDatastoreSupport contains utility methods pointing to `toStringAncestor` and `toLongAncestor`
  // so you do not have to call ToAncestor directly
}
```

### Custom Field Types

**Note: This section is likely to change to provide functions from A to datastore.Value[_]**

There are `FieldFormat[A]`s already implicitly available for:

- `String`
- `Long`
- `Boolean`
- `Double`
- `Int`
- `Array[Byte]`
- `BigDecimal`
- `java.time.Instant`
- `Option[A]` for any `[A]` for which a format exists
- `Seq[A]` for any `[A]` for which a format exists
- `com.google.cloud.Timestamp`
- `com.google.cloud.datastore.LatLng`

There is a utility function available for creating your own field formats from simple refined wrappers
around a type for which a format already exists in implicit scope:

```scala
import com.ovoenergy.datastore4s.FieldFormat

case class CustomString(innerValue: String)

implicit val format = FieldFormat.fieldFormatFromFunctions(CustomString.apply)(_.innerValue)
// Again DefaultDatastoreSupport contains a utility method fieldFormatFromFunctions
```

For larger case classes comprised of fields for which formats are already in implicit scope there 
is a macro to generate a format that will nest the fields using dot notation:

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

## Queries

Queries can be performed directly using the `DatatstoreService` object.

**Note: This design is likely to change so that queries and other operations
are represented by a Monad expressing an action to be perfomed rather than being
performed at the point functions are called**

### Get

### Stream

### From entity

### Projections

## Suggested Use

## Further Work

## Feedback And Contribution