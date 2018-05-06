# Custom Keys

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
      keyFactory.buildWithName(s"${value.name}@${value.department}")
  }
  
  implicit val format = EntityFormat[Employee, CustomKey]("employees")(e => CustomKey(e.name, e.department))
}
```

There is also a utility function in `DatastoreRepository` called `toKey[A]` that allows you to simply pass a function, for example:

```scala
implicit val customToKey = toKey[CustomKey]((value, keyFactory) => keyFactory.buildWithName(s"${value.name}@${value.department}"))
```

## Ancestors

To use ancestors in keys and queries create an implicit `ToAncestor[A]` for your types using the `toStringAncestor` or
`toLongAncestor` function. For example:

```scala
import com.ovoenergy.datastore4s._
import com.google.cloud.datastore.Key

object AncestorExample extends DatastoreRepository {
  case class Department(name: String)
  
  // Provide the name of the kind and a function A => String 
  // (Or A => Long in the case of a LongAncestor)
  implicit val departmentAncestor = toStringAncestor[Department]("Department")(_.name)
  
  case class CustomKey(name: String, department: Department)
  
  implicit val customToKey = toKey[CustomKey]((value, keyFactory) =>
      keyFactory.addAncestor(value.department).buildWithName(value.name))
}
```
