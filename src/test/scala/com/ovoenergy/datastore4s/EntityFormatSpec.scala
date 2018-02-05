package com.ovoenergy.datastore4s

import com.datastore4s.core.{KeyFactory, ToKey}
import com.datastore4s.core.utils.TestDatastore
import com.google.cloud.datastore.Entity
import com.ovoenergy.datastore4s.utils.TestDatastore
import org.scalatest.{FeatureSpec, Matchers}

class EntityFormatSpec extends FeatureSpec with Matchers {

  val datastore = TestDatastore()

  implicit val keyFactorySupplier = () => datastore.newKeyFactory()

  feature("The EntityFormat macro") {
    scenario("Attempt to make an EntityFormat of a type that is not a case class") {
      """EntityFormat[NonCaseClass, String]("non-case-class")(_.key)""" shouldNot compile
    }
    scenario("Attempt to make an EntityFormat when an implicit field format is not available") {
      """EntityFormat[MissingFieldFormatEntity, String]("missing")(_.stringField)""" shouldNot compile
    }
    scenario("Attempt to make an EntityFormat when an implicit ToKey is not available") {
      """EntityFormat[MissingKeyFormatEntity, MissingFieldFormatType]("missing")(_.missingTypeField)""" shouldNot compile
    }
    scenario("A simple case class with only a long key") {
      val longEntityFormat = EntityFormat[LongKeyObject, java.lang.Long]("long-type")(_.key)
      val record = LongKeyObject(20)
      val entity = longEntityFormat.toEntity(record)
      entity.getKey.getKind shouldBe "long-type"
      entity.getKey().getId shouldBe 20

      val roundTripped = longEntityFormat.fromEntity(entity)
      roundTripped shouldBe record
    }
    scenario("A case class with a string key and string property") {
      val stringEntityFormat = EntityFormat[StringKeyObject, String]("string-type")(_.someKey)
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
      val complexEntityFormat = EntityFormat[ComplexKeyObject, Id]("complex-kind")(_.id)

      val record = ComplexKeyObject(Id("key", "parent"))
      val entity: Entity = complexEntityFormat.toEntity(record)
      entity.getKey.getKind shouldBe "complex-kind"
      val key = entity.getKey()
      key.getName shouldBe "key"

      key.getAncestors should have size 1
      val ancestor = key.getAncestors.get(0)
      ancestor.getName shouldBe "parent" // TODO proper handling of ancestors

      val roundTripped = complexEntityFormat.fromEntity(entity)
      roundTripped shouldBe record
    }
  }

  case class StringKeyObject(someKey: String, someProperty: String)

  case class LongKeyObject(key: Long)

  case class ComplexKeyObject(id: Id)

  case class Id(id: String, parent: String)

  object IdToKey extends ToKey[Id] {
    private val ancestorKind = Kind("test-ancestor")

    override def toKey(value: Id, keyFactory: KeyFactory) = keyFactory.addAncestor(StringAncestor(ancestorKind, value.parent)).buildWithName(value.id)
  }

  class NonCaseClass(val key: String)

  case class MissingFieldFormatEntity(missingTypeField: MissingFieldFormatType, stringField: String)

  case class MissingFieldFormatType()

}