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

private[datastore4s] class DatastoreQuery[E, D <: BaseEntity[Key]](queryBuilderSupplier: Datastore => StructuredQuery.Builder[D],
                                                                   entityFunction: D => Entity)(implicit fromEntity: FromEntity[E])
    extends Query[E] {

  override def withAncestor[A](a: A)(implicit toAncestor: ToAncestor[A]) = {
    val newSupplier = (datastore: Datastore) => {
      val ancestorKey = Query.ancestorToKey(toAncestor.toAncestor(a), datastore.newKeyFactory())
      queryBuilderSupplier(datastore).setFilter(PropertyFilter.hasAncestor(ancestorKey))
    }
    new DatastoreQuery(newSupplier, entityFunction)
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
    val newSupplier = (datastore: Datastore) => {
      val dsValue = valueFormat.toValue(value) match {
        case WrappedValue(value) => value
      }
      queryBuilderSupplier(datastore).setFilter(filterBuilder(propertyName, dsValue))
    }
    new DatastoreQuery(newSupplier, entityFunction)
  }

  override def stream() = DatastoreOperation { datastore =>
    try {
      Right(
        datastore
          .run(queryBuilderSupplier(datastore).build(), Seq.empty[ReadOption]: _*)
          .asScala
          .toStream
          .map(entityFunction)
          .map(fromEntity.fromEntity)
      )
    } catch {
      case f: Throwable => DatastoreError.exception(f)
    }
  }

  override def sequenced() = stream().flatMapEither(DatastoreError.sequence(_))

}
