package com.ovoenergy.datastore4s

import com.google.cloud.datastore.{FullEntity, Key}

final case class Kind (name: String)

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
}

private[datastore4s] class ProjectionEntity(mappings: Map[String, String], actualEntity: com.google.cloud.datastore.BaseEntity[Key])
    extends Entity {
  override def field(name: String): Option[DatastoreValue] = {
    val fieldName = mappings.getOrElse(name, name)
    if (actualEntity.contains(fieldName)) Some(new WrappedValue(actualEntity.getValue(fieldName)))
    else None
  }
}

sealed trait EntityBuilder {
  def addField(field: Field): EntityBuilder

  def add[A](name: String, value: A)(implicit format: FieldFormat[A]): EntityBuilder =
    addField(format.toEntityField(name, value))

  def build(): Entity
}

private[datastore4s] class WrappedBuilder(key: Key, fields: Seq[(String, DatastoreValue)] = Seq.empty) extends EntityBuilder {
  override def addField(field: Field): EntityBuilder =
    new WrappedBuilder(key, field.values ++ fields)

  override def build(): Entity = {
    val builder = com.google.cloud.datastore.Entity.newBuilder(key)
    val entity = fields.foldLeft(builder) { case (b, (name, WrappedValue(value))) => b.set(name, value) }.build()
    new WrappedEntity(entity)
  }
}
