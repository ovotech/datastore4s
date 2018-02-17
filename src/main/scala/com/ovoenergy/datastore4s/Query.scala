package com.ovoenergy.datastore4s

import com.google.cloud.datastore.StructuredQuery.PropertyFilter
import com.google.cloud.datastore._
import com.ovoenergy.datastore4s.Query.EntityFunction
import com.ovoenergy.datastore4s.internal.{DatastoreError, ProjectionEntity, ValueFormat, WrappedEntity}

import scala.collection.JavaConverters._

trait Query[E] {
  // TODO replace raw query with some form of Monad representation of an action to be executed.

  def withAncestor[A](a: A)(implicit toAncestor: ToAncestor[A]): Query[E]

  def withPropertyEq[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]): Query[E] // TODO extend this somehow. Perhaps with a DSL?

  def stream(): Stream[Either[DatastoreError, E]]

}

object Query {
  def ancestorToKey(ancestor: Ancestor, keyFactory: com.google.cloud.datastore.KeyFactory): Key = ancestor match {
    case StringAncestor(kind, name) => keyFactory.setKind(kind.name).newKey(name)
    case LongAncestor(kind, id) => keyFactory.setKind(kind.name).newKey(id)
  }
  type EntityFunction = BaseEntity[Key] => internal.Entity
}

case class DatastoreQuery[E](queryBuilder: StructuredQuery.Builder[_ <: BaseEntity[Key]], entityFunction: EntityFunction = WrappedEntity(_))(implicit fromEntity: FromEntity[E], datastore: Datastore) extends Query[E] {

  override def withAncestor[A](a: A)(implicit toAncestor: ToAncestor[A]) = {
    val key = Query.ancestorToKey(toAncestor.toAncestor(a), datastore.newKeyFactory())
    DatastoreQuery(queryBuilder.setFilter(PropertyFilter.hasAncestor(key)), entityFunction)
  }

  override def withPropertyEq[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]) =
    DatastoreQuery(queryBuilder.setFilter(PropertyFilter.eq(propertyName, valueFormat.toValue(value).dsValue)), entityFunction)

  override def stream() = datastore.run(queryBuilder.build(), Seq.empty[ReadOption]: _*).asScala.toStream.map(entityFunction).map(fromEntity.fromEntity)
}

case class Project[E]()(implicit datastore: Datastore, format: EntityFormat[E, _]) {
  def into[A]()(implicit fromEntity: FromEntity[A]) = Projection[E, A]()
}

case class Projection[E, A]()(implicit datastore: Datastore, format: EntityFormat[E, _], fromEntity: FromEntity[A]) {
  def mapping(firstMapping: (String, String), remainingMappings: (String, String)*): Query[A] = {
    val mappings = (firstMapping +: remainingMappings).toMap
    val kind = format.kind.name
    val queryBuilder = com.google.cloud.datastore.Query.newProjectionEntityQueryBuilder().setKind(kind).setProjection(firstMapping._1, remainingMappings.map(_._1): _*)
    DatastoreQuery[A](queryBuilder, (e: BaseEntity[Key]) => ProjectionEntity(mappings, WrappedEntity(e)))
  }
}
