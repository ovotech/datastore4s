package com.datastore4s.core

import com.google.cloud.datastore.StructuredQuery.PropertyFilter
import com.google.cloud.datastore.{Datastore, Entity, PathElement, ReadOption}

import scala.util.Try

trait Query[E <: DatastoreEntity[_]] {

  def withAncestor(ancestor: Ancestor): Query[E]

  def withAncestor[A](a: A)(implicit toAncestor: ToAncestor[A]): Query[E] = withAncestor(toAncestor.toAncestor(a))

  def withPropertyEq(propertyName: String, value: Int): Query[E] // TODO extend this somehow. Perhaps with a DSL?

  def toSeq(): Seq[Try[E]]

}

case class DatastoreQuery[E <: DatastoreEntity[_]](queryBuilder: com.google.cloud.datastore.StructuredQuery.Builder[Entity])(implicit format: EntityFormat[E, _], datastore: Datastore) extends Query[E] {

  import scala.collection.JavaConverters._

  override def withAncestor(ancestor: Ancestor) = {
    val kf = datastore.newKeyFactory()
    val key = ancestor match {
      case StringAncestor(kind, name) => kf.setKind(kind.name).newKey(name)
      case LongAncestor(kind, id) => kf.setKind(kind.name).newKey(id)
    }
    DatastoreQuery(queryBuilder.setFilter(PropertyFilter.hasAncestor(key)))
  }

  override def withPropertyEq(propertyName: String, value: Int) = DatastoreQuery(queryBuilder.setFilter(PropertyFilter.eq(propertyName, value)))

  override def toSeq() = datastore.run(queryBuilder.build(), Seq.empty[ReadOption]: _*).asScala.toSeq.map(format.fromEntity)
}
