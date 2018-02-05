package com.datastore4s.core

trait DefaultDatastoreSupport {

  def dataStoreConfiguration: DataStoreConfiguration

  private implicit val datastore = DatastoreService.createDatastore(dataStoreConfiguration)
  implicit val keyFactorySupplier = () => datastore.newKeyFactory()

  def fieldFormatFromFunctions[A, B](constructor: B => A)(extractor: A => B)(implicit existingFormat: FieldFormat[B]): FieldFormat[A] =
    FieldFormat.fieldFormatFromFunctions(constructor)(extractor)

  def toStringAncestor[A](kind: String)(f: A => String): ToAncestor[A] = ToAncestor.toStringAncestor(kind)(f)

  def toLongAncestor[A](kind: String)(f: A => Long): ToAncestor[A] = ToAncestor.toLongAncestor(kind)(f)

  def put[E](entity: E)(implicit format: EntityFormat[E, _]): Persisted[E] = DatastoreService.put(entity)

  def list[E]()(implicit format: EntityFormat[E, _]): Query[E] = DatastoreService.list

  def findOne[E, K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): Option[E] = DatastoreService.findOne(key)

  def project[E](firstField: String, remainingFields: String*)(implicit format: EntityFormat[E, _]): Project =
    DatastoreService.project(firstField, remainingFields: _*)

}
