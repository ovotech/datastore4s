# More Customisation

## Sealed Traits

By default sealed traits will have a `type` property attached to both the `FieldFormat` and `EntityFormat` which is equal
to the type name. This can be overridden using the `@SubTypeName` annotation. For example:

```scala
sealed trait Foo
case class Bar(value: Int) extends Foo // Has "type" "Bar"

@SubTypeName("FooBaz")
case class Baz(value: String) extends Foo // Has "type" "FooBaz"

```
