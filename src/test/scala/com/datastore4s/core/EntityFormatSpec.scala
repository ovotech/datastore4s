package com.datastore4s.core

import com.datastore4s.core.utils.TestDatastore
import com.google.cloud.datastore.Entity
import org.scalatest.{FeatureSpec, Matchers}

class EntityFormatSpec extends FeatureSpec with Matchers {

  val datastore = TestDatastore()

  implicit val keyFactorySupplier = () => datastore.newKeyFactory()

  feature("The EntityFormat macro") {
    scenario("Attempt to make an EntityFormat of a type that does not extend DatastoreEntity") {
      "EntityFormat[String, String]" shouldNot compile
    }
    scenario("Attempt to make an EntityFormat of a type that extends DatastoreEntity but the key type is wrong") {
      "EntityFormat[StringKeyObject, Long]" shouldNot compile
    }
    scenario("Attempt to make an EntityFormat of a type that extends DatastoreEntity but is not a case class") {
      "EntityFormat[NonCaseClass, String]" shouldNot compile
    }
    scenario("Attempt to make an EntityFormat of a type that extends DatastoreEntity but is annotated more than once") {
      "EntityFormat[DuplicatedAnnotationClass, String]" shouldNot compile
    }
    scenario("Attempt to make an EntityFormat when an implicit field format is not available") {
      "EntityFormat[MissingFieldFormatEntity, String]" shouldNot compile
    }
    scenario("Attempt to make an EntityFormat when an implicit ToKey is not available") {
      "EntityFormat[MissingKeyFormatEntity, String]" shouldNot compile
    }
    scenario("A simple case class with only a long key") {
      val longEntityFormat = EntityFormat[LongKeyObject, Long]
      val record = LongKeyObject(20)
      val entity = longEntityFormat.toEntity(record)
      entity.getKey.getKind shouldBe "long-type"
      entity.getKey().getId shouldBe 20

      val roundTripped = longEntityFormat.fromEntity(entity)
      roundTripped shouldBe record
    }
    scenario("A case class with a string key and string property") {
      val stringEntityFormat = EntityFormat[StringKeyObject, String]
      val record = StringKeyObject("key", "propertyValue")
      val entity: Entity = stringEntityFormat.toEntity(record)
      entity.getKey.getKind shouldBe "string-type"
      entity.getKey().getName shouldBe "key"
      entity.getString("someProperty") shouldBe "propertyValue"

      val roundTripped = stringEntityFormat.fromEntity(entity)
      roundTripped shouldBe record
    }
    scenario("A case class that uses a non string or numeric key") {
      implicit val idAsKey = IdToKey
      implicit val idFieldFormat = NestedFieldFormat[Id]
      val complexEntityFormat = EntityFormat[ComplexKeyObject, Id]

      val record = ComplexKeyObject(Id("key", "parent"))
      val entity: Entity = complexEntityFormat.toEntity(record)
      entity.getKey.getKind shouldBe "ComplexKeyObject"
      val key = entity.getKey()
      key.getName shouldBe "key"

      key.getAncestors should have size 1
      val ancestor = key.getAncestors.get(0)
      ancestor.getName shouldBe "parent" // TODO proper handling of ancestors

      val roundTripped = complexEntityFormat.fromEntity(entity)
      roundTripped shouldBe record
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

  class NonCaseClass(val key: String) extends DatastoreEntity[String]

  @EntityKind("test-kind")
  @EntityKind("another-test-kind")
  case class DuplicatedAnnotationClass(val key: String) extends DatastoreEntity[String]

  case class MissingFieldFormatEntity(field: MissingFieldFormatType) extends DatastoreEntity[String] {
    def key = "hello"
  }

  case class MissingFieldFormatType()

  case class MissingKeyFormatEntity(key: MissingFieldFormatType) extends DatastoreEntity[MissingFieldFormatType]

}