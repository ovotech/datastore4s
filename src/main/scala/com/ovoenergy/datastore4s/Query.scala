package com.ovoenergy.datastore4s

import com.google.cloud.datastore._
import com.google.cloud.datastore.StructuredQuery.PropertyFilter
import com.ovoenergy.datastore4s.ToAncestor.{LongAncestor, StringAncestor}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

trait Query[E] {

  def withAncestor[A](a: A)(implicit toAncestor: ToAncestor[A]): Query[E]

  def withPropertyEq[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]): Query[E]

  def withPropertyLessThan[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]): Query[E]

  def withPropertyLessThanEq[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]): Query[E]

  def withPropertyGreaterThan[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]): Query[E]

  def withPropertyGreaterThanEq[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]): Query[E]

  def stream(): DatastoreOperation[Stream[Either[DatastoreError, E]]]

  def sequenced(): DatastoreOperation[Seq[E]]

}

object Query {
  def ancestorToKey(ancestor: Ancestor, keyFactory: com.google.cloud.datastore.KeyFactory): Key =
    ancestor match {
      case StringAncestor(kind, name) =>
        keyFactory.setKind(kind.name).newKey(name)
      case LongAncestor(kind, id) => keyFactory.setKind(kind.name).newKey(id)
    }
}

private[datastore4s] class DatastoreQuery[E, D <: BaseEntity[Key]](queryBuilder: StructuredQuery.Builder[D], entityFunction: D => Entity)(
  implicit fromEntity: FromEntity[E],
  datastore: Datastore
) extends Query[E] {

  override def withAncestor[A](a: A)(implicit toAncestor: ToAncestor[A]) = {
    val key =
      Query.ancestorToKey(toAncestor.toAncestor(a), datastore.newKeyFactory())
    new DatastoreQuery(queryBuilder.setFilter(PropertyFilter.hasAncestor(key)), entityFunction)
  }

  override def withPropertyEq[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]) =
    withFilter(propertyName, value)(PropertyFilter.eq)

  override def withPropertyLessThan[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]) =
    withFilter(propertyName, value)(PropertyFilter.lt)

  override def withPropertyLessThanEq[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]) =
    withFilter(propertyName, value)(PropertyFilter.le)

  override def withPropertyGreaterThan[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]) =
    withFilter(propertyName, value)(PropertyFilter.gt)

  override def withPropertyGreaterThanEq[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]) =
    withFilter(propertyName, value)(PropertyFilter.ge)

  private def withFilter[A](propertyName: String, value: A)(
    filterBuilder: (String, Value[_]) => PropertyFilter
  )(implicit valueFormat: ValueFormat[A]): Query[E] = {
    val dsValue = valueFormat.toValue(value) match { case WrappedValue(value) => value }
    new DatastoreQuery(queryBuilder.setFilter(filterBuilder(propertyName, dsValue)), entityFunction)
  }

  override def stream() = DatastoreOperation { () =>
    Try(
      datastore
        .run(queryBuilder.build(), Seq.empty[ReadOption]: _*)
        .asScala
        .toStream
        .map(entityFunction)
        .map(fromEntity.fromEntity)
    ) match {
      case Success(stream) => Right(stream)
      case Failure(f)      => DatastoreError.error(f.getMessage)
    }
  }

  override def sequenced() = stream().flatMapEither(DatastoreError.sequence(_))

}

case class Project[E]()(implicit datastore: Datastore, format: EntityFormat[E, _]) {
  def into[A]()(implicit fromEntity: FromEntity[A]) = Projection[E, A]()
}

case class Projection[E, A]()(implicit datastore: Datastore, format: EntityFormat[E, _], fromEntity: FromEntity[A]) {
  def mapping(firstMapping: (String, String), remainingMappings: (String, String)*): Query[A] = {
    val kind = format.kind.name
    val queryBuilder = com.google.cloud.datastore.Query
      .newProjectionEntityQueryBuilder()
      .setKind(kind)
      .setProjection(firstMapping._1, remainingMappings.map(_._1): _*)
    val mappings = (firstMapping.swap +: remainingMappings.map(_.swap)).toMap
    new DatastoreQuery[A, com.google.cloud.datastore.ProjectionEntity](queryBuilder, new ProjectionEntity(mappings, _))
  }
}
