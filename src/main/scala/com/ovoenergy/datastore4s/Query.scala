package com.ovoenergy.datastore4s

import com.google.cloud.datastore.StructuredQuery.PropertyFilter
import com.google.cloud.datastore._

import scala.collection.JavaConverters._

trait Query[E] {
  // TODO replace raw query with some form of Monad representation of an action to be executed.

  def withAncestor(ancestor: Ancestor): Query[E]

  def withAncestor[A](a: A)(implicit toAncestor: ToAncestor[A]): Query[E] = withAncestor(toAncestor.toAncestor(a))

  def withPropertyEq(propertyName: String, value: Int): Query[E] // TODO extend this somehow. Perhaps with a DSL?

  def withPropertyEq(propertyName: String, value: String): Query[E] // TODO extend this somehow. Perhaps with a DSL? Annoying overloading is a problem. Implicit use of DatastoreValue?

  def stream(): Stream[E]

}

object Query {
  def ancestorToKey(ancestor: Ancestor, keyFactory: com.google.cloud.datastore.KeyFactory): Key = ancestor match {
    case StringAncestor(kind, name) => keyFactory.setKind(kind.name).newKey(name)
    case LongAncestor(kind, id) => keyFactory.setKind(kind.name).newKey(id)
  }
}

case class DatastoreQuery[E](queryBuilder: StructuredQuery.Builder[_ <: BaseEntity[_]])(implicit fromEntity: FromEntity[E], datastore: Datastore) extends Query[E] {

  override def withAncestor(ancestor: Ancestor) = {
    val key = Query.ancestorToKey(ancestor, datastore.newKeyFactory())
    DatastoreQuery(queryBuilder.setFilter(PropertyFilter.hasAncestor(key)))
  }

  override def withPropertyEq(propertyName: String, value: Int) = DatastoreQuery(queryBuilder.setFilter(PropertyFilter.eq(propertyName, value)))

  override def withPropertyEq(propertyName: String, value: String) = DatastoreQuery(queryBuilder.setFilter(PropertyFilter.eq(propertyName, value)))

  override def stream() = datastore.run(queryBuilder.build(), Seq.empty[ReadOption]: _*).asScala.toStream.map(fromEntity.fromEntity)
}

case class Project[E]()(implicit datastore: Datastore, format: EntityFormat[E, _]) {
  def into[A]()(implicit fromEntity: FromEntity[A]) = Projection[E, A]()
}

case class Projection[E, A]()(implicit datastore: Datastore, format: EntityFormat[E, _], fromEntity: FromEntity[A]) {
  def mapping(firstMapping: (String, String), remainingMappings: (String, String)*): Query[A] = {
    val mappings = (firstMapping +: remainingMappings).toMap
    val kind = format.kind.name
    val queryBuilder = com.google.cloud.datastore.Query.newProjectionEntityQueryBuilder().setKind(kind).setProjection(firstMapping._1, remainingMappings.map(_._1): _*)
    ProjectionQuery(mappings, queryBuilder)
  }
}

case class ProjectionQuery[E, A](mappings: Map[String, String], queryBuilder: StructuredQuery.Builder[ProjectionEntity])(implicit datastore: Datastore, format: EntityFormat[E, _], fromEntity: FromEntity[A]) extends Query[A] {
  override def withAncestor(ancestor: Ancestor) = {
    val key = Query.ancestorToKey(ancestor, datastore.newKeyFactory())
    ProjectionQuery(mappings, queryBuilder.setFilter(PropertyFilter.hasAncestor(key)))
  }

  override def withPropertyEq(propertyName: String, value: Int) = ProjectionQuery(mappings, queryBuilder.setFilter(PropertyFilter.eq(propertyName, value)))

  override def withPropertyEq(propertyName: String, value: String) = ProjectionQuery(mappings, queryBuilder.setFilter(PropertyFilter.eq(propertyName, value)))

  override def stream() = datastore.run(queryBuilder.build(), Seq.empty[ReadOption]: _*).asScala.toStream.map { e =>
    val builder = ProjectionEntity.newBuilder(e)
    for ((original, newValue) <- mappings) {
      val value: Value[_] = e.getValue(original)
      builder.set(newValue, value)
    }
    builder.build()
  }.map(fromEntity.fromEntity(_))
}
