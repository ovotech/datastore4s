package com.ovoenergy.datastore4s

import com.google.cloud.datastore.{Key, StringValue => DsStringValue}
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

case class EmptyCaseClass()

class EntityFormatSpec extends FeatureSpec with Matchers {

  implicit object SealedToKey extends ToKey[SealedKey] {
    override def toKey(value: SealedKey, keyFactory: KeyFactory): Key = value match {
      case StringKey(name) => keyFactory.buildWithName(name)
      case LongKey(id) => keyFactory.buildWithId(id)
    }
  }

  implicit val datastoreService = DatastoreService(DataStoreConfiguration("test-project", "test-namespace"))

  feature("The EntityFormat macro") {
    scenario("Attempt to make an EntityFormat of a type that is not a case class or sealed trait") {
      """EntityFormat[NonCaseClass, String]("non-case-class")(_.key)""" shouldNot compile
    }

    scenario("Attempt to make an EntityFormat of a type that is a case class with no fields") {
      """EntityFormat[EmptyCaseClass, String]("no-field-case-class")(_ => "Hello")""" shouldNot compile
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

    scenario("Attempt to make an EntityFormat and ignore the index of a property that does not exist") {
      """EntityFormat.ignoreIndexes[LongKeyObject, java.lang.Long]("someProperty")("long-type")(_.key)""".stripMargin shouldNot compile
    }

    scenario("Attempt to make an EntityFormat and ignore the index of a property that is not a constant") {
      """val property = "key"
        EntityFormat.ignoreIndexes[LongKeyObject, java.lang.Long](property)("long-type")(_.key)""".stripMargin shouldNot compile
    }

    scenario("Attempt to make an EntityFormat and only index of a property that does not exist") {
      """EntityFormat.onlyIndex[LongKeyObject, java.lang.Long]("someProperty")("long-type")(_.key)""".stripMargin shouldNot compile
    }

    scenario("Attempt to make an EntityFormat and only index a property that is not a constant") {
      """val property = "key"
        EntityFormat.onlyIndex[LongKeyObject, java.lang.Long](property)("long-type")(_.key)""".stripMargin shouldNot compile
    }

    scenario("A simple case class with only a long key") {
      val longEntityFormat = EntityFormat[LongKeyObject, JavaLong]("long-type")(_.key)
      val record = LongKeyObject(20)
      val entity = DatastoreService.toEntity(record, longEntityFormat, datastoreService)
      longEntityFormat.kind.name shouldBe "long-type"
      longEntityFormat.key(record) shouldBe 20
      entity.fieldOfType[Long]("key") shouldBe Right(20)

      val roundTripped = longEntityFormat.fromEntity(entity)
      roundTripped shouldBe Right(record)
    }

    scenario("A case class with a string key and string property") {
      val stringEntityFormat = EntityFormat[StringKeyObject, String]("string-type")(_.someKey)
      val record = StringKeyObject("key", "propertyValue")
      val entity = DatastoreService.toEntity(record, stringEntityFormat, datastoreService)
      stringEntityFormat.kind.name shouldBe "string-type"
      stringEntityFormat.key(record) shouldBe "key"
      entity.fieldOfType[String]("someProperty") shouldBe Right("propertyValue")
      entity.fieldOfType[String]("someKey") shouldBe Right("key")

      val roundTripped = stringEntityFormat.fromEntity(entity)
      roundTripped shouldBe Right(record)
    }

    scenario("A case class that uses a non string or numeric key") {
      implicit val idAsKey = IdToKey
      implicit val parentFormat = ValueFormat.formatFromFunctions(Parent.apply)(_.name)
      implicit val idFieldFormat = FieldFormat[Id]
      val complexEntityFormat = EntityFormat[ComplexKeyObject, Id]("complex-kind")(_.id)

      val record = ComplexKeyObject(Id("key", Parent("parent")))
      val entity = DatastoreService.toEntity(record, complexEntityFormat, datastoreService)
      complexEntityFormat.kind.name shouldBe "complex-kind"
      complexEntityFormat.key(record) shouldBe Id("key", Parent("parent"))

      entity.fieldOfType[Id]("id") shouldBe Right(Id("key", Parent("parent")))

      val roundTripped = complexEntityFormat.fromEntity(entity)
      roundTripped shouldBe Right(record)
    }

    scenario("A sealed trait hierarchy") {
      val sealedEntityFormat = EntityFormat[SealedEntityType, SealedKey]("sealed-type")(SealedEntityType.key(_))

      val firstRecord = FirstSubType("first-key", 2036152)
      val firstEntity = DatastoreService.toEntity(firstRecord, sealedEntityFormat, datastoreService)
      sealedEntityFormat.kind.name shouldBe "sealed-type"
      sealedEntityFormat.key(firstRecord) shouldBe StringKey("first-key")
      firstEntity.fieldOfType[String]("key") shouldBe Right("first-key")
      firstEntity.fieldOfType[Long]("someLongValue") shouldBe Right(2036152)

      sealedEntityFormat.fromEntity(firstEntity) shouldBe Right(firstRecord)

      val secondRecord = SecondSubType(83746286466723l, true, 1824672.23572)
      val secondEntity = DatastoreService.toEntity(secondRecord, sealedEntityFormat, datastoreService)
      sealedEntityFormat.key(secondRecord) shouldBe LongKey(83746286466723l)
      secondEntity.fieldOfType[Long]("key") shouldBe Right(83746286466723l)
      secondEntity.fieldOfType[Boolean]("someBoolean") shouldBe Right(true)
      secondEntity.fieldOfType[Double]("someDouble") shouldBe Right(1824672.23572)

      sealedEntityFormat.fromEntity(secondEntity) shouldBe Right(secondRecord)
    }

    scenario("An entity for which a field has an index ignored") {
      val stringEntityFormat = EntityFormat.ignoreIndexes[StringKeyObject, String]("someProperty")("string-type")(_.someKey)
      val record = StringKeyObject("key", "propertyValue")
      val entity = DatastoreService.toEntity(record, stringEntityFormat, datastoreService)
      stringEntityFormat.kind.name shouldBe "string-type"
      stringEntityFormat.key(record) shouldBe "key"
      entity.fieldOfType[String]("someProperty") shouldBe Right("propertyValue")
      entity.fieldOfType[String]("someKey") shouldBe Right("key")

      entity match {
        case e: WrappedEntity =>
          e.entity.getValue[DsStringValue]("someProperty").excludeFromIndexes() shouldBe true
          e.entity.getValue[DsStringValue]("someKey").excludeFromIndexes() shouldBe false
        case _ => fail("Expected a wrapped entity")
      }

      val roundTripped = stringEntityFormat.fromEntity(entity)
      roundTripped shouldBe Right(record)
    }

    scenario("An entity for which all fields but one are indexed") {
      val stringEntityFormat = EntityFormat.onlyIndex[StringKeyObject, String]("someProperty")("string-type")(_.someKey)
      val record = StringKeyObject("key", "propertyValue")
      val entity = DatastoreService.toEntity(record, stringEntityFormat, datastoreService)
      stringEntityFormat.kind.name shouldBe "string-type"
      stringEntityFormat.key(record) shouldBe "key"
      entity.fieldOfType[String]("someProperty") shouldBe Right("propertyValue")
      entity.fieldOfType[String]("someKey") shouldBe Right("key")

      entity match {
        case e: WrappedEntity =>
          e.entity.getValue[DsStringValue]("someKey").excludeFromIndexes() shouldBe true
          e.entity.getValue[DsStringValue]("someProperty").excludeFromIndexes() shouldBe false
        case _ => fail("Expected a wrapped entity")
      }

      val roundTripped = stringEntityFormat.fromEntity(entity)
      roundTripped shouldBe Right(record)
    }
  }

}