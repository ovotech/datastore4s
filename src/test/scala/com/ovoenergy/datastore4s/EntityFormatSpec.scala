package com.ovoenergy.datastore4s

import com.ovoenergy.datastore4s.ToKey.JavaLong
import com.ovoenergy.datastore4s.utils.TestDatastore
import org.scalatest.{FeatureSpec, Matchers}

class EntityFormatSpec extends FeatureSpec with Matchers {

  sealed trait SealedEntityType {
    val key: String
  }

  case class FirstSubType(key: String, someLongValue: Long) extends SealedEntityType

  case class SecondSubType(key: String, someBoolean:Boolean, someDouble: Double) extends SealedEntityType

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

    scenario("Attempt to make an EntityFormat from a non literal kind string") {
      """val kind = "string-type"
         EntityFormat[StringKeyObject, String](kind)(_.someKey)""".stripMargin shouldNot compile
    }

    scenario("Attempt to make an EntityFormat from a kind that starts with two underscores") {
      """EntityFormat[StringKeyObject, String]("__kind")(_.someKey)""".stripMargin shouldNot compile
    }

    scenario("Attempt to make an EntityFormat from a kind containing a '/'") {
      """EntityFormat[StringKeyObject, String]("some/kind")(_.someKey)""".stripMargin shouldNot compile
    }

    scenario("A simple case class with only a long key") {
      val longEntityFormat = EntityFormat[LongKeyObject, JavaLong]("long-type")(_.key)
      val record = LongKeyObject(20)
      val e = longEntityFormat.toEntity(record)
      val entity = e.rawEntity // TODO try and remove this method.
      entity.getKey.getKind shouldBe "long-type"
      entity.getKey().getId shouldBe 20
      entity.getLong("key") shouldBe 20

      val roundTripped = longEntityFormat.fromEntity(e)
      roundTripped shouldBe Right(record)
    }

    scenario("A case class with a string key and string property") {
      val stringEntityFormat = EntityFormat[StringKeyObject, String]("string-type")(_.someKey)
      val record = StringKeyObject("key", "propertyValue")
      val e = stringEntityFormat.toEntity(record)
      val entity = e.rawEntity // TODO try and remove this method.
      entity.getKey.getKind shouldBe "string-type"
      entity.getKey().getName shouldBe "key"
      entity.getString("someProperty") shouldBe "propertyValue"
      entity.getString("someKey") shouldBe "key"

      val roundTripped = stringEntityFormat.fromEntity(e)
      roundTripped shouldBe Right(record)
    }

    scenario("A case class that uses a non string or numeric key") {
      implicit val idAsKey = IdToKey
      implicit val parentFormat = ValueFormat.formatFromFunctions(Parent.apply)(_.name)
      implicit val idFieldFormat = FieldFormat[Id]
      val complexEntityFormat = EntityFormat[ComplexKeyObject, Id]("complex-kind")(_.id)

      val record = ComplexKeyObject(Id("key", Parent("parent")))
      val e = complexEntityFormat.toEntity(record)
      val entity = e.rawEntity // TODO try and remove this method.
      entity.getKey.getKind shouldBe "complex-kind"
      val key = entity.getKey()
      key.getName shouldBe "key"

      key.getAncestors should have size 1
      val ancestor = key.getAncestors.get(0)
      ancestor.getName shouldBe "parent" // TODO proper handling of ancestors

      entity.getString("id.id") shouldBe "key"
      entity.getString("id.parent") shouldBe "parent"

      val roundTripped = complexEntityFormat.fromEntity(e)
      roundTripped shouldBe Right(record)
    }

    scenario("A sealed trait hierarchy") {
      val sealedEntityFormat = EntityFormat[SealedEntityType, String]("sealed-type")(_.key)

      val firstRecord = FirstSubType("first-key", 2036152)
      val e1 = sealedEntityFormat.toEntity(firstRecord)
      val firstEntity = e1.rawEntity // TODO try and remove this method.
      firstEntity.getKey.getKind shouldBe "sealed-type"
      firstEntity.getKey().getName shouldBe "first-key"
      firstEntity.getString("key") shouldBe "first-key"
      firstEntity.getLong("someLongValue") shouldBe 2036152

      sealedEntityFormat.fromEntity(e1) shouldBe Right(firstRecord)

      val secondRecord = SecondSubType("second-key", true, 1824672.23572)
      val e2 = sealedEntityFormat.toEntity(secondRecord)
      val secondEntity = e2.rawEntity // TODO try and remove this method.
      secondEntity.getKey.getKind shouldBe "sealed-type"
      secondEntity.getKey().getName shouldBe "second-key"
      secondEntity.getString("key") shouldBe "second-key"
      secondEntity.getBoolean("someBoolean") shouldBe true
      secondEntity.getDouble("someDouble") shouldBe 1824672.23572

      sealedEntityFormat.fromEntity(e2) shouldBe Right(secondRecord)
    }
  }

  case class StringKeyObject(someKey: String, someProperty: String)

  case class LongKeyObject(key: Long)

  case class ComplexKeyObject(id: Id)

  case class Id(id: String, parent: Parent)

  case class Parent(name: String)

  implicit val parentToAncestor = ToAncestor.toStringAncestor[Parent]("test-ancestor")(_.name)

  object IdToKey extends ToKey[Id] {
    override def toKey(value: Id, keyFactory: KeyFactory) = keyFactory.addAncestor(value.parent).buildWithName(value.id)
  }

  class NonCaseClass(val key: String)

  case class MissingFieldFormatEntity(missingTypeField: MissingFieldFormatType, stringField: String)

  case class MissingFieldFormatType()

}