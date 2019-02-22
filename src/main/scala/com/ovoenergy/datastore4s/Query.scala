package com.ovoenergy.datastore4s

import com.google.cloud.datastore._
import com.google.cloud.datastore.StructuredQuery.{CompositeFilter, OrderBy, PropertyFilter}
import com.ovoenergy.datastore4s.Query.FilterSupplier

/** Object representing a query to be run on datastore, uses a builder pattern */
sealed trait Query[E] {

  /** Only return entities that have the given ancestor */
  def withAncestor[A](a: A)(implicit toAncestor: ToAncestor[A]): Query[E]

  def withPropertyEq[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]): Query[E]

  def withPropertyLessThan[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]): Query[E]

  def withPropertyLessThanEq[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]): Query[E]

  def withPropertyGreaterThan[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]): Query[E]

  def withPropertyGreaterThanEq[A](propertyName: String, value: A)(implicit valueFormat: ValueFormat[A]): Query[E]

  def orderByAscending(property: String): Query[E]

  def orderByDescending(property: String): Query[E]

  /** Limit the number of results returned */
  def limit(limit: Int): Query[E]

  /** Offset the results */
  def offset(offset: Int): Query[E]

  /** Returns a stream of all query results in a stream */
  def stream(): DatastoreOperation[Stream[Either[DatastoreError, E]]]

  /** Return all results of the query in a sequence, combining all errors if there are any */
  def sequenced(): DatastoreOperation[Seq[E]]

}

object Query {
  def ancestorToKey(ancestor: Ancestor, datastoreService: DatastoreService): Key =
    ancestor match {
      case StringAncestor(kind, name) => datastoreService.createKey(name, kind)
      case LongAncestor(kind, id)     => datastoreService.createKey(Long.box(id), kind)
    }

  type FilterSupplier = DatastoreService => PropertyFilter
}

private[datastore4s] class DatastoreQuery[E, D <: BaseEntity[Key]](val queryBuilderSupplier: () => StructuredQuery.Builder[D],
                                                                   val filters: Seq[FilterSupplier] = Seq.empty,
                                                                   val orders: Seq[OrderBy] = Seq.empty,
                                                                   val entityFunction: D => Entity)(implicit fromEntity: FromEntity[E])
    extends Query[E] {

  override def withAncestor[A](a: A)(implicit toAncestor: ToAncestor[A]): Query[E] = {
    val newFilter = (datastoreService: DatastoreService) => {
      val ancestorKey = Query.ancestorToKey(toAncestor.toAncestor(a), datastoreService)
      PropertyFilter.hasAncestor(ancestorKey)
    }
    new DatastoreQuery(queryBuilderSupplier, newFilter +: filters, orders, entityFunction)
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
    new DatastoreQuery(queryBuilderSupplier, newFilter +: filters, orders, entityFunction)
  }

  override def limit(limit: Int): Query[E] =
    new DatastoreQuery(() => queryBuilderSupplier().setLimit(limit), filters, orders, entityFunction)

  override def offset(offset: Int): Query[E] =
    new DatastoreQuery(() => queryBuilderSupplier().setOffset(offset), filters, orders, entityFunction)

  override def orderByAscending(property: String): Query[E] =
    new DatastoreQuery(queryBuilderSupplier, filters, OrderBy.asc(property) +: orders, entityFunction)

  override def orderByDescending(property: String): Query[E] =
    new DatastoreQuery(queryBuilderSupplier, filters, OrderBy.desc(property) +: orders, entityFunction)

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

  private def buildQuery(datastoreService: DatastoreService) = {
    val filters = addFilters(queryBuilderSupplier(), datastoreService)
    addOrders(filters, datastoreService).build()
  }

  private def addFilters(builder: StructuredQuery.Builder[D], datastoreService: DatastoreService) =
    filters.map(_.apply(datastoreService)) match {
      case Nil                        => builder
      case onlyFilter :: Nil          => builder.setFilter(onlyFilter)
      case firstFilter :: moreFilters => builder.setFilter(CompositeFilter.and(firstFilter, moreFilters: _*))
    }

  private def addOrders(builder: StructuredQuery.Builder[D], datastoreService: DatastoreService) = orders match {
    case Nil                      => builder
    case firstOrder :: moreOrders => builder.setOrderBy(firstOrder, moreOrders: _*)
  }

  override def sequenced(): DatastoreOperation[Seq[E]] = stream().flatMapEither(DatastoreError.sequence(_))

}
