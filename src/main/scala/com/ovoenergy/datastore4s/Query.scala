package com.ovoenergy.datastore4s

import com.google.cloud.datastore._
import com.google.cloud.datastore.StructuredQuery.{CompositeFilter, PropertyFilter}
import com.ovoenergy.datastore4s.Query.FilterSupplier

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

  type FilterSupplier = DatastoreService => PropertyFilter
}

private[datastore4s] class DatastoreQuery[E, D <: BaseEntity[Key]](val queryBuilderSupplier: () => StructuredQuery.Builder[D],
                                                                   val filters: Seq[FilterSupplier] = Seq.empty,
                                                                   val entityFunction: D => Entity)(implicit fromEntity: FromEntity[E])
    extends Query[E] {

  override def withAncestor[A](a: A)(implicit toAncestor: ToAncestor[A]): Query[E] = {
    val newFilter = (datastoreService: DatastoreService) => {
      val ancestorKey = Query.ancestorToKey(toAncestor.toAncestor(a), datastoreService)
      PropertyFilter.hasAncestor(ancestorKey)
    }
    new DatastoreQuery(queryBuilderSupplier, newFilter +: filters, entityFunction)
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
    val newFilter = (_: DatastoreService) => {
      val dsValue = valueFormat.toValue(value) match {
        case WrappedValue(wrappeValue) => wrappeValue
      }
      filterBuilder(propertyName, dsValue)
    }
    new DatastoreQuery(queryBuilderSupplier, newFilter +: filters, entityFunction)
  }

  override def stream() = DatastoreOperation { datastoreService =>
    try {
      Right(
        datastoreService
          .runQuery(buildQuery(datastoreService))
          .map(entityFunction)
          .map(fromEntity.fromEntity)
      )
    } catch {
      case f: Throwable => DatastoreError.exception(f)
    }
  }

  private def buildQuery(datastoreService: DatastoreService) = filters.map(_.apply(datastoreService)) match {
    case Nil                        => queryBuilderSupplier().build()
    case onlyFilter :: Nil          => queryBuilderSupplier().setFilter(onlyFilter).build()
    case firstFilter :: moreFilters => queryBuilderSupplier().setFilter(CompositeFilter.and(firstFilter, moreFilters: _*)).build()
  }

  override def sequenced(): DatastoreOperation[Seq[E]] = stream().flatMapEither(DatastoreError.sequence(_))

}
