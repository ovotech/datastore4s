package com.ovoenergy.datastore4s.internal

import com.google.cloud.datastore.{BaseEntity, Key}

trait Entity {
  def field(name: String): Option[DatastoreValue]

  def rawEntity: BaseEntity[Key]
}

case class WrappedEntity[E <: BaseEntity[Key]](entity: E) extends Entity {
  override def field(name: String) = if (entity.contains(name)) Some(new WrappedValue(entity.getValue(name))) else None

  override def rawEntity = entity
}

trait EntityBuilder {
  def addField(name: String, value: DatastoreValue): EntityBuilder

  def build(): Entity
}

case class WrappedBuilder(builder: com.google.cloud.datastore.Entity.Builder) extends EntityBuilder {
  override def addField(name: String, value: DatastoreValue) = WrappedBuilder(builder.set(name, value.dsValue))

  override def build() = WrappedEntity(builder.build())
}
