package com.ovoenergy.datastore4s

import com.google.cloud.datastore.{BaseEntity, Key}

trait Entity {
  def field(name: String): Option[DatastoreValue]

  def rawEntity: BaseEntity[Key]
}

case class WrappedEntity(entity: BaseEntity[Key]) extends Entity {
  override def field(name: String) =
    if (entity.contains(name)) Some(new WrappedValue(entity.getValue(name)))
    else None

  override def rawEntity = entity
}

case class ProjectionEntity(mappings: Map[String, String], wrappedEntity: WrappedEntity) extends Entity {
  override def field(name: String) =
    wrappedEntity.field(mappings.getOrElse(name, name))

  override def rawEntity = wrappedEntity.rawEntity
}

trait EntityBuilder {
  def addField(name: String, value: DatastoreValue): EntityBuilder

  def build(): Entity
}

case class WrappedBuilder(builder: com.google.cloud.datastore.Entity.Builder) extends EntityBuilder {
  override def addField(name: String, value: DatastoreValue) =
    WrappedBuilder(builder.set(name, value.dsValue))

  override def build() = WrappedEntity(builder.build())
}

object WrappedBuilder {
  def apply(existingEntity: Entity): EntityBuilder =
    existingEntity.rawEntity match {
      // TODO if we can extract this out of the entityformat we should be ok here. It is only needed since a builder is not passed into the format. If A createKey interface can be extracted we should be good.
      case ent: com.google.cloud.datastore.Entity =>
        WrappedBuilder(com.google.cloud.datastore.Entity.newBuilder(ent))
      case other =>
        throw new RuntimeException(s"Need to fix up internal representation, expected Entity but was: $other")
    }
}
