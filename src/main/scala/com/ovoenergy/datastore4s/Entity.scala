package com.ovoenergy.datastore4s

import com.google.cloud.datastore.{BaseEntity, Key}

class Kind private (val name: String) {
  override def equals(obj: scala.Any): Boolean = obj match {
    case that: Kind => that.name == name
    case _          => false
  }
}

object Kind {

  private def isValid(kind: String): Boolean =
    !(kind.contains('/') || kind.startsWith("__"))

  def apply(kindName: String) = {
    require(isValid(kindName), "A kind must not start with '__' or contain '/'")
    new Kind(kindName)
  }

}

trait Entity {
  def field(name: String): Option[DatastoreValue]

  // TODO should I keep following method?? It may be useful in the case people don't want to use the macros
  def fieldFromFormatPossiblyGoingToBeDeleted[A](name: String)(implicit fieldFormat: FieldFormat[A]): Either[DatastoreError, A] =
    fieldFormat.fromEntityField(name, this)

  def rawEntity: BaseEntity[Key] // TODO delete!!!
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
  def addField(field: Field): EntityBuilder

  // TODO fix arg list. Does this make sense?? it might make sense to change the field format contract to not accept builder but instead return a fn or Seq[Field]?
  def addFieldByFormatPossiblyGoingToBeDeleted[A](name: String, value: A)(implicit format: FieldFormat[A]): EntityBuilder =
    addField(format.toEntityField(name, value))

  def build(): Entity
}

case class WrappedBuilder(key: Key, fields: Seq[(String, DatastoreValue)] = Seq.empty) extends EntityBuilder {
  override def addField(field: Field) =
    copy(fields = field.values ++ fields)

  override def build() = {
    val builder = com.google.cloud.datastore.Entity.newBuilder(key)
    val entity = fields.foldLeft(builder) { case (b, (name, value)) => b.set(name, value.dsValue) }.build()
    WrappedEntity(entity)
  }
}
