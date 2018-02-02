package com.datastore4s.core

import com.google.cloud.datastore.{DatastoreOptions, Entity}
import org.scalatest.{FeatureSpec, Matchers}

import scala.util.{Success, Try}

trait EntitySupport {
  implicit val stringEntityFormat = EntityFormat[StringKeyObject, String]

  implicit val longEntityFormat = EntityFormat[LongKeyObject, Long]

  implicit val idAsKey = IdToKey
  implicit val idFieldFormat = NestedFieldFormat[Id]
  implicit val complexEntityFormat = EntityFormat[ComplexKeyObject, Id]
}

class ToFromEntitySpec extends FeatureSpec with Matchers with EntitySupport {

  val datastore = DatastoreOptions.newBuilder()
    .setProjectId("test-project")
    .setNamespace("test-namespace")
    .build().getService

  implicit val keyFactorySupplier = () => datastore.newKeyFactory()

  feature("ToEntity and FromEntity") {
    scenario("A simple case class with only a long key") {
      val record = LongKeyObject(20)
      val entity = toEntity[LongKeyObject, Long](record)
      entity.getKey.getKind shouldBe "long-type"
      entity.getKey().getId shouldBe 20

      val roundTripped = fromEntity[LongKeyObject, Long](entity)
      roundTripped shouldBe record
    }
    scenario("A case class with a string key and string property") {
      val record = StringKeyObject("key", "propertyValue")
      val entity: Entity = toEntity[StringKeyObject, String](record)
      entity.getKey.getKind shouldBe "string-type"
      entity.getKey().getName shouldBe "key"
      entity.getString("someProperty") shouldBe "propertyValue"

      val roundTripped = fromEntity[StringKeyObject, String](entity)
      roundTripped shouldBe record
    }
    scenario("A case class that uses a non string or numeric key") {
      val record = ComplexKeyObject(Id("key", "parent"))
      val entity: Entity = toEntity[ComplexKeyObject, Id](record)
      entity.getKey.getKind shouldBe "ComplexKeyObject"
      val key = entity.getKey()
      key.getName shouldBe "key"

      key.getAncestors should have size 1
      val ancestor = key.getAncestors.get(0)
      ancestor.getName shouldBe "parent" // TODO proper handling of ancestors

      val roundTripped = fromEntity[ComplexKeyObject, Id](entity)
      roundTripped shouldBe record

    }
  }

  private def toEntity[EntityType <: DatastoreEntity[KeyType], KeyType](value: EntityType)(implicit format: EntityFormat[EntityType, KeyType]): Entity = {
    format.toEntity(value)
  }

  private def fromEntity[EntityType <: DatastoreEntity[KeyType], KeyType](entity: Entity)(implicit format: EntityFormat[EntityType, KeyType]): EntityType = {
    format.fromEntity(entity)
  }
}

@EntityKind("string-type")
case class StringKeyObject(someKey: String, someProperty: String) extends DatastoreEntity[String] {
  override def key = someKey
}

@EntityKind("long-type")
case class LongKeyObject(someKey: Long) extends DatastoreEntity[Long] {
  override def key = someKey
}

case class ComplexKeyObject(id: Id) extends DatastoreEntity[Id] {
  override def key = id
}

case class Id(id: String, parent: String)

object IdToKey extends ToKey[Id] {
  private val ancestorKind = Kind("test-ancestor")

  override def toKey(value: Id, keyFactory: KeyFactory) = keyFactory.addAncestor(StringAncestor(ancestorKind, value.parent)).buildWithName(value.id)
}
