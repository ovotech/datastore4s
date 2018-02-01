package com.datastore4s.core

import com.google.cloud.datastore.Entity

trait DefaultDatastoreSupport {

  val dataStoreConfiguration: DataStoreConfiguration

  private implicit lazy val service = DatastoreService.createDatastore(dataStoreConfiguration)
  implicit val keyFactorySupplier = () => service.newKeyFactory()

  def fieldFormatFromFunctions[A, B](constructor: B => A)(extractor: A => B)(implicit existingFormat: FieldFormat[B]): FieldFormat[A] = {
    new FieldFormat[A] {
      override def addField(value: A, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = existingFormat.addField(extractor(value), fieldName, entityBuilder)

      override def fromField(entity: Entity, fieldName: String): A = constructor(existingFormat.fromField(entity, fieldName))
    }
  }

  def toStringAncestor[A](kind: Kind)(f: A => String): ToAncestor[A] = a => StringAncestor(kind, f(a))

  def toLongAncestor[A](kind: Kind)(f: A => Long): ToAncestor[A] = a => LongAncestor(kind, f(a))

  def put[E <: DatastoreEntity[_]](entity: E)(implicit format: EntityFormat[E, _]): Persisted[E] = DatastoreService.put(entity)

  def list[E <: DatastoreEntity[_]]()(implicit format: EntityFormat[E, _]): Query[E] = DatastoreService.list

}
