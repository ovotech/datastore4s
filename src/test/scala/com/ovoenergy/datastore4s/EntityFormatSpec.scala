package com.ovoenergy.datastore4s

import com.google.cloud.datastore.Key
import com.ovoenergy.datastore4s.ToKey.JavaLong
import org.scalatest.{FeatureSpec, Matchers}

sealed trait SealedEntityType

sealed trait SealedKey

case class StringKey(key: String) extends SealedKey

case class LongKey(long: Long) extends SealedKey

case class FirstSubType(key: String, someLongValue: Long) extends SealedEntityType

case class SecondSubType(key: Long, someBoolean: Boolean, someDouble: Double) extends SealedEntityType

object SealedEntityType {
  def key(sealedEntityType: SealedEntityType): SealedKey = sealedEntityType match {
    case FirstSubType(key, _) => StringKey(key)
    case SecondSubType(key, _, _) => LongKey(key)
  }
}

case class StringKeyObject(someKey: String, someProperty: String)

case class LongKeyObject(key: Long)

case class ComplexKeyObject(id: Id)

case class Id(id: String, parent: Parent)

case class Parent(name: String)

object IdToKey extends ToKey[Id] {
  implicit val parentToAncestor = ToAncestor.toStringAncestor[Parent]("test-ancestor")(_.name)
  override def toKey(value: Id, keyFactory: KeyFactory) = keyFactory.addAncestor(value.parent).buildWithName(value.id)
}

class NonCaseClass(val key: String)

case class MissingFieldFormatEntity(missingTypeField: MissingFieldFormatType, stringField: String)

case class MissingFieldFormatType()

class EntityFormatSpec extends FeatureSpec with Matchers {

  implicit object SealedToKey extends ToKey[SealedKey] {
    override def toKey(value: SealedKey, keyFactory: KeyFactory): Key = value match {
      case StringKey(name) => keyFactory.buildWithName(name)
      case LongKey(id) => keyFactory.buildWithId(id)
    }
  }

  implicit val datastore = DatastoreService.createDatastore(DataStoreConfiguration("test-project", "test-namespace"))

  feature("The EntityFormat macro") {
    scenario("Attempt to make an EntityFormat of a type that is not a case class or sealed trait") {
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

    scenario("A simple case class with only a long key") {
      val longEntityFormat = EntityFormat[LongKeyObject, JavaLong]("long-type")(_.key)
      val record = LongKeyObject(20)
      val e = DatastoreService.toEntity(record, longEntityFormat)
      val entity = e.rawEntity // TODO try and remove this method.
      longEntityFormat.kind.name shouldBe "long-type"
      longEntityFormat.key(record) shouldBe 20
      entity.getLong("key") shouldBe 20 // TODO move key matching to integration tests??? I feel like these tests should just check te round trip

      val roundTripped = longEntityFormat.fromEntity(e)
      roundTripped shouldBe Right(record)
    }

    scenario("A case class with a string key and string property") {
      val stringEntityFormat = EntityFormat[StringKeyObject, String]("string-type")(_.someKey)
      val record = StringKeyObject("key", "propertyValue")
      val e = DatastoreService.toEntity(record, stringEntityFormat)
      val entity = e.rawEntity // TODO try and remove this method.
      stringEntityFormat.kind.name shouldBe "string-type"
      stringEntityFormat.key(record) shouldBe "key"
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
      val e = DatastoreService.toEntity(record, complexEntityFormat)
      val entity = e.rawEntity // TODO try and remove this method.
      complexEntityFormat.kind.name shouldBe "complex-kind"
      complexEntityFormat.key(record) shouldBe Id("key", Parent("parent"))

      entity.getString("id.id") shouldBe "key"
      entity.getString("id.parent") shouldBe "parent"

      val roundTripped = complexEntityFormat.fromEntity(e)
      roundTripped shouldBe Right(record)
    }

    scenario("A sealed trait hierarchy") {
      val sealedEntityFormat = EntityFormat[SealedEntityType, SealedKey]("sealed-type")(SealedEntityType.key(_))

      val firstRecord = FirstSubType("first-key", 2036152)
      val e1 = DatastoreService.toEntity(firstRecord, sealedEntityFormat)
      val firstEntity = e1.rawEntity // TODO try and remove this method.
      sealedEntityFormat.kind.name shouldBe "sealed-type"
      sealedEntityFormat.key(firstRecord) shouldBe StringKey("first-key")
      firstEntity.getString("key") shouldBe "first-key"
      firstEntity.getLong("someLongValue") shouldBe 2036152

      sealedEntityFormat.fromEntity(e1) shouldBe Right(firstRecord)

      val secondRecord = SecondSubType(83746286466723l, true, 1824672.23572)
      val e2 = DatastoreService.toEntity(secondRecord, sealedEntityFormat)
      val secondEntity = e2.rawEntity // TODO try and remove this method.
      sealedEntityFormat.key(secondRecord) shouldBe LongKey(83746286466723l)
      secondEntity.getLong("key") shouldBe 83746286466723l
      secondEntity.getBoolean("someBoolean") shouldBe true
      secondEntity.getDouble("someDouble") shouldBe 1824672.23572

      sealedEntityFormat.fromEntity(e2) shouldBe Right(secondRecord)
    }
  }

}