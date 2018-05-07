# Indexes

By default all fields of an entity will be indexed. If you would like to not index certain properties then you can provide 
a vararg list of properties to not index:

`EntityFormat.ignoreIndexes[MyEntity, MyKey]("ignoreProperty1", "ignoreProperty2", ...)("kind")(_.key)`

If you would instead like to only index certain properties and have all others not indexed by default then you can provide
a vararg list of properties to index:

`EntityFormat.onlyIndex[MyEntity, MyKey]("property1", "property2", ...)("kind")(_.key)`

This property list will be checked at compile time to ensure:
- In the case of a case class that all properties exist on that case class
- In the case of a sealed trait that all properties exist on **at least one** of the subtypes

## WARNINGS

- If you exclude an index of a property you using for queries then your queries will not work.
- You can only exclude indexes from top level properties, we aim to add more fine grained control in the future. For example:

```scala
import com.ovoenergy.datastore4s.EntityFormat

case class Property(field1: String, field2: Int)
case class Entity(key: String, property: Property)

// does compile
EntityFormat.onlyIndex[Entity, String]("property")("kind")(_.key)

// does not compile
EntityFormat.onlyIndex[Entity, String]("property.field1")("kind")(_.key)
```

## Manually Ignoring indexes

### ValueFormat Index customisation

In the case that you know at a type level that you never want to index any values of a type then you can:

```scala
import com.ovoenergy.datastore4s.ValueFormat

case class DebugLog(log: String) // Always a very large string
object DebugLog {
  implicit val debugLogFormat = ValueFormat.formatFrom(DebugLog.apply)(_.log).ignoreIndex
}
```

### FieldFormat Index customisation

If at type level you know you need to ignore indexes the whole field you can do so in the `FieldFormat`.

```scala
import com.ovoenergy.datastore4s.FieldFormat

case class IgnorableType(property1: String, property2: Int)
object IgnorableType {
  implicit val format = FieldFormat[IgnorableType].ignoreIndexes
}

```

### EntityFormat Index customisation

If you only want to ignore certain fields on an entity and are not using the macros this can be done in the `EntityFormat`.
In the following example only the `age` is not indexed.

```scala
import com.ovoenergy.datastore4s._

case class Person(firstName: String, lastName: String, age: Int)

object NonMacroIndexExample {

  implicit object PersonFormat extends EntityFormat[Person, String] {
    override def toEntity(person: Person, builder: EntityBuilder): Entity = builder
      .add("firstName", person.firstName)
      .add("lastName", person.lastName)
      .addIgnoringIndex("age", person.age) // Don't index age
      .build()

    override def fromEntity(entity: Entity): Either[DatastoreError, Person] = for {
      firstName <- entity.fieldOfType[String]("firstName")
      lastName <- entity.fieldOfType[String]("lastName")
      age <- entity.fieldOfType[Int]("age")
    } yield Person(firstName, lastName, age)
    
    override val kind = Kind("person")
    override def key(person: Person) = person.firstName + person.lastName
  }
}
```
