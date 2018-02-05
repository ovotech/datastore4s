package com.datastore4s.core

import com.google.cloud.datastore.StructuredQuery.PropertyFilter
import com.google.cloud.datastore._

import scala.collection.JavaConverters._

trait Query[E] {

  def withAncestor(ancestor: Ancestor): Query[E]

  def withAncestor[A](a: A)(implicit toAncestor: ToAncestor[A]): Query[E] = withAncestor(toAncestor.toAncestor(a))

  def withPropertyEq(propertyName: String, value: Int): Query[E] // TODO extend this somehow. Perhaps with a DSL?

  def withPropertyEq(propertyName: String, value: String): Query[E] // TODO extend this somehow. Perhaps with a DSL? Annoying overloading is a problem

  def toSeq(): Seq[E]

}

object Query {
  def ancestorToKey(ancestor: Ancestor, keyFactory: com.google.cloud.datastore.KeyFactory): Key = ancestor match {
    case StringAncestor(kind, name) => keyFactory.setKind(kind.name).newKey(name)
    case LongAncestor(kind, id) => keyFactory.setKind(kind.name).newKey(id)
  }
}

// TODO see if the datastore query and projection query can be combined when FromEntity is split out so that the Entity class that extends BaseEntity does not matter.
case class DatastoreQuery[E](queryBuilder: com.google.cloud.datastore.StructuredQuery.Builder[Entity])(implicit format: EntityFormat[E, _], datastore: Datastore) extends Query[E] {

  override def withAncestor(ancestor: Ancestor) = {
    val key = Query.ancestorToKey(ancestor, datastore.newKeyFactory())
    DatastoreQuery(queryBuilder.setFilter(PropertyFilter.hasAncestor(key)))
  }

  override def withPropertyEq(propertyName: String, value: Int) = DatastoreQuery(queryBuilder.setFilter(PropertyFilter.eq(propertyName, value)))

  override def withPropertyEq(propertyName: String, value: String) = DatastoreQuery(queryBuilder.setFilter(PropertyFilter.eq(propertyName, value)))

  override def toSeq() = datastore.run(queryBuilder.build(), Seq.empty[ReadOption]: _*).asScala.toSeq.map(format.fromEntity)
}

case class Project(queryBuilder: com.google.cloud.datastore.StructuredQuery.Builder[ProjectionEntity])(implicit datastore: Datastore) {
  def into[A]()(implicit fromEntityProjection: FromProjection[A]) = ProjectionQuery(queryBuilder)
}

case class ProjectionQuery[A](queryBuilder: com.google.cloud.datastore.StructuredQuery.Builder[ProjectionEntity])(implicit fromEntityProjection: FromProjection[A], datastore: Datastore) extends Query[A] {
  override def withAncestor(ancestor: Ancestor) = {
    val key = Query.ancestorToKey(ancestor, datastore.newKeyFactory())
    ProjectionQuery(queryBuilder.setFilter(PropertyFilter.hasAncestor(key)))
  }

  override def withPropertyEq(propertyName: String, value: Int) = ProjectionQuery(queryBuilder.setFilter(PropertyFilter.eq(propertyName, value)))

  override def withPropertyEq(propertyName: String, value: String) = ProjectionQuery(queryBuilder.setFilter(PropertyFilter.eq(propertyName, value)))

  override def toSeq() = datastore.run(queryBuilder.build(), Seq.empty[ReadOption]: _*).asScala.toSeq.map(fromEntityProjection.fromProjection)
}


