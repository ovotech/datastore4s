package com.ovoenergy.datastore4s

import com.google.cloud.datastore.{BaseEntity, FullEntity, Key}
import com.google.cloud.datastore.{Entity => DsEntity}

final case class Kind(name: String)

object Kind {

  private def isValid(kind: String): Boolean =
    !(kind.contains('/') || kind.startsWith("__"))

  def apply(kindName: String): Kind = {
    require(isValid(kindName), "A kind must not start with '__' or contain '/'")
    new Kind(kindName)
  }

}

sealed trait Entity {
  def field(name: String): Option[DatastoreValue]

  def fieldOfType[A](name: String)(implicit fieldFormat: FieldFormat[A]): Either[DatastoreError, A] =
    fieldFormat.fromEntityField(name, this)

}

private[datastore4s] class WrappedEntity(val entity: FullEntity[Key]) extends Entity {
  override def field(name: String): Option[DatastoreValue] =
    if (entity.contains(name)) Some(new WrappedValue(entity.getValue(name)))
    else None

  override def toString = s"WrappedDatastoreEntity($entity)"

  override def equals(obj: scala.Any) = obj match {
    case e: WrappedEntity => e.entity == entity
    case _                => false
  }
}

private[datastore4s] class ProjectionEntity(val mappings: Map[String, String], val actualEntity: BaseEntity[Key]) extends Entity {
  override def field(name: String): Option[DatastoreValue] = {
    val fieldName = mappings.getOrElse(name, name)
    if (actualEntity.contains(fieldName)) Some(new WrappedValue(actualEntity.getValue(fieldName)))
    else None
  }

  override def toString = s"DatastoreProjection(mappings: $mappings, actualEntity: $actualEntity)"

  override def equals(obj: scala.Any) = obj match {
    case e: ProjectionEntity => e.mappings == mappings && e.actualEntity == actualEntity
    case _                   => false
  }
}

sealed trait EntityBuilder {
  def addField(field: Field): EntityBuilder

  def add[A](name: String, value: A)(implicit format: FieldFormat[A]): EntityBuilder =
    addField(format.toEntityField(name, value))

  def addIgnoringIndex[A](name: String, value: A)(implicit format: FieldFormat[A]): EntityBuilder =
    addField(format.toEntityField(name, value).ignoreIndexes)

  def build(): Entity
}

private[datastore4s] class WrappedBuilder(val key: Key, val fields: Map[String, DatastoreValue] = Map.empty) extends EntityBuilder {
  override def addField(field: Field): EntityBuilder =
    new WrappedBuilder(key, field.values ++ fields)

  override def build(): Entity = {
    val entity = fields.foldLeft(DsEntity.newBuilder(key)) { case (b, (name, WrappedValue(value))) => b.set(name, value) }.build()
    new WrappedEntity(entity)
  }

  override def equals(obj: scala.Any): Boolean = obj match {
    case other: WrappedBuilder => key == other.key && fields == other.fields
    case _                     => false
  }

  override def toString = s"EntityBuilder(key: $key, fields: $fields)"
}
