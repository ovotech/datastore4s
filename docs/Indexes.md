# Indexes

We aim to add more index support in the future. For now there are two ways to customise indexes. By default all fields of
an entity will be indexed. If you would like to not index certain properties then you can provide a vararg list of
properties to not index to:

```scala
EntityFormat.ignoreIndexes[MyEntity, MeyKey]("ignoreProperty1", "ignoreProperty2", ...)("kind")(_.key)
```

If you would instead like to only index certain properties and have all others not indexed by default then you can provide
a vararg list of properties to index to:

```scala
EntityFormat.onlyIndex[MyEntity, MeyKey]("property1", "property2", ...)("kind")(_.key)
```

This property list will be checked at compile time to ensure:
- In the case of a case class that all properties exist on that case class
- In the case of a sealed trait that all properties exist on **AT LEAST ONE** of the subtypes

## WARNINGS

- If you exclude an index of a property you using for queries then your queries will not work.
- You can only exclude indexes from top level properties, we aim to add more fine grained control in the future. For example:

```scala
case class Property(field1: String, field2: Int)
case class Entity(key: String, property: Property)

// does compile
EntityFormat.onlyIndex[Entity, String]("property")("kind")(_.key)

// does not compile
EntityFormat.onlyIndex[Entity, String]("property.field1")("kind")(_.key)
```

// TODO Unsafe Index functions
