package com.datastore4s.core

trait DefaultDatastoreSupport {

  def dataStoreConfiguration: DataStoreConfiguration

  private implicit lazy val service = DatastoreService.createDatastore(dataStoreConfiguration)
  implicit val keyFactorySupplier = () => service.newKeyFactory()

  def fieldFormatFromFunctions[A, B](constructor: B => A)(extractor: A => B)(implicit existingFormat: FieldFormat[B]): FieldFormat[A] =
    FieldFormat.fieldFormatFromFunctions(constructor)(extractor)

  // TODO move following function out to an object
  def toStringAncestor[A](kind: Kind)(f: A => String): ToAncestor[A] = a => StringAncestor(kind, f(a))

  // TODO move following function out to an object
  def toLongAncestor[A](kind: Kind)(f: A => Long): ToAncestor[A] = a => LongAncestor(kind, f(a))

  def put[E](entity: E)(implicit format: EntityFormat[E, _]): Persisted[E] = DatastoreService.put(entity)

  def list[E]()(implicit format: EntityFormat[E, _]): Query[E] = DatastoreService.list

  def findOne[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): Option[E] = DatastoreService.findOne(key)

  def project[E](firstField: String, remainingFields: String*)(implicit format: EntityFormat[E, _]): Project =
    DatastoreService.project(firstField, remainingFields: _*)

}
