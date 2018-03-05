package com.ovoenergy.datastore4s

import com.google.cloud.datastore._
import com.google.cloud.datastore.StructuredQuery.PropertyFilter

sealed trait Query[E] {

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
  def ancestorToKey(ancestor: Ancestor, datastoreService: DatastoreService): Key =
    ancestor match {
      case StringAncestor(kind, name) =>
        datastoreService.createKey(name, kind)
      case LongAncestor(kind, id) => datastoreService.createKey(Long.box(id), kind)
    }
}

private[datastore4s] class DatastoreQuery[E, D <: BaseEntity[Key]](queryBuilderSupplier: DatastoreService => StructuredQuery.Builder[D],
                                                                   entityFunction: D => Entity)(implicit fromEntity: FromEntity[E])
    extends Query[E] {

  override def withAncestor[A](a: A)(implicit toAncestor: ToAncestor[A]): Query[E] = {
    val newSupplier = (datastoreService: DatastoreService) => {
      val ancestorKey = Query.ancestorToKey(toAncestor.toAncestor(a), datastoreService)
      queryBuilderSupplier(datastoreService).setFilter(PropertyFilter.hasAncestor(ancestorKey))
    }
    new DatastoreQuery(newSupplier, entityFunction)
  }

  override def withPropertyEq[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]): Query[E] =
    withFilter(propertyName, value)(PropertyFilter.eq)

  override def withPropertyLessThan[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]): Query[E] =
    withFilter(propertyName, value)(PropertyFilter.lt)

  override def withPropertyLessThanEq[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]): Query[E] =
    withFilter(propertyName, value)(PropertyFilter.le)

  override def withPropertyGreaterThan[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]): Query[E] =
    withFilter(propertyName, value)(PropertyFilter.gt)

  override def withPropertyGreaterThanEq[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]): Query[E] =
    withFilter(propertyName, value)(PropertyFilter.ge)

  private def withFilter[A](propertyName: String, value: A)(
    filterBuilder: (String, Value[_]) => PropertyFilter
  )(implicit valueFormat: ValueFormat[A]): Query[E] = {
    val newSupplier = (datastoreService: DatastoreService) => {
      val dsValue = valueFormat.toValue(value) match {
        case WrappedValue(wrappeValue) => wrappeValue
      }
      queryBuilderSupplier(datastoreService).setFilter(filterBuilder(propertyName, dsValue))
    }
    new DatastoreQuery(newSupplier, entityFunction)
  }

  override def stream() = DatastoreOperation { datastoreService =>
    try {
      Right(
        datastoreService
          .runQuery(queryBuilderSupplier(datastoreService).build())
          .map(entityFunction)
          .map(fromEntity.fromEntity)
      )
    } catch {
      case f: Throwable => DatastoreError.exception(f)
    }
  }

  override def sequenced(): DatastoreOperation[Seq[E]] = stream().flatMapEither(DatastoreError.sequence(_))

}
