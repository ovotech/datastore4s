package com.datastore4s.core

import com.google.cloud.datastore.{BaseEntity, Entity}

import scala.util.Try

trait DefaultDatastoreSupport {

  def dataStoreConfiguration: DataStoreConfiguration

  private implicit lazy val service = DatastoreService.createDatastore(dataStoreConfiguration)
  implicit val keyFactorySupplier = () => service.newKeyFactory()

  def fieldFormatFromFunctions[A, B](constructor: B => A)(extractor: A => B)(implicit existingFormat: FieldFormat[B]): FieldFormat[A] = {
    new FieldFormat[A] {
      override def addField(value: A, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = existingFormat.addField(extractor(value), fieldName, entityBuilder)

      override def fromField[E <: BaseEntity[_]](entity: E, fieldName: String): A = constructor(existingFormat.fromField(entity, fieldName))
    }
  }
  def toStringAncestor[A](kind: Kind)(f: A => String): ToAncestor[A] = a => StringAncestor(kind, f(a))

  def toLongAncestor[A](kind: Kind)(f: A => Long): ToAncestor[A] = a => LongAncestor(kind, f(a))

  def put[E <: DatastoreEntity[_]](entity: E)(implicit format: EntityFormat[E, _]): Persisted[E] = DatastoreService.put(entity)

  def list[E <: DatastoreEntity[_]]()(implicit format: EntityFormat[E, _]): Query[E] = DatastoreService.list

  def findOne[E <: DatastoreEntity[K], K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K]): Option[E] = DatastoreService.findOne(key)

  def project[E <: DatastoreEntity[_]](firstField: String, remainingFields: String*)(implicit format: EntityFormat[E, _]): Project =
    DatastoreService.project(firstField, remainingFields: _*)

}
