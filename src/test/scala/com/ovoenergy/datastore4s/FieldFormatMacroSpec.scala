package com.ovoenergy.datastore4s

import java.time.Instant

import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}

class FieldFormatMacroSpec extends FlatSpec with Matchers with GeneratorDrivenPropertyChecks {

  implicit val datastoreService = DatastoreService(DatastoreConfiguration("test-project", "test-namespace"))

  case class EntityWithNestedType(id: String, nestedType: SomeNestedType)
  case class EntityWithOptionalNestedType(id: String, nestedType: Option[SomeNestedType])

  case class SomeNestedType(stringField: String,
                            someLongField: Long,
                            someIntField: Int,
                            someBooleanField: Boolean,
                            someTimeField: Instant)

  implicit val instantFormat = ValueFormat.instantEpochMillisValueFormat

  val nestedTypeGen = for {
    string <- Gen.alphaNumStr
    long <- Gen.choose(Long.MinValue, Long.MaxValue)
    int <- Gen.choose(Int.MinValue, Int.MaxValue)
    bool <- Gen.oneOf(true, false)
    time <- Gen.choose(Long.MinValue, Long.MaxValue).map(Instant.ofEpochMilli(_))
  } yield SomeNestedType(string, long, int, bool, time)

  val caseClassEntityGen = for {
    id <- Gen.alphaNumStr.filter(!_.isEmpty)
    nested <- nestedTypeGen
  } yield EntityWithNestedType(id, nested)

  "The FieldFormat macro" should "create a field format that nests the fields of case classes" in {
    implicit val format = FieldFormat[SomeNestedType]
    val entityFormat = EntityFormat[EntityWithNestedType, String]("nested-test-kind")(_.id)

    forAll(caseClassEntityGen) { entity =>
      val roundTripped = entityFormat.fromEntity(DatastoreService.toEntity(entity, entityFormat, datastoreService))
      roundTripped shouldBe Right(entity)
    }
  }

  val caseClassOptionalEntityGen = for {
    id <- Gen.alphaNumStr.filter(!_.isEmpty)
    nested <- nestedTypeGen
    opt <- Gen.oneOf(Some(nested), None)
  } yield EntityWithOptionalNestedType(id, opt)

  it should "also work with optional types" in {
    implicit val format = FieldFormat[SomeNestedType]
    val entityFormat = EntityFormat[EntityWithOptionalNestedType, String]("nested-test-kind")(_.id)

    forAll(caseClassOptionalEntityGen) { entity =>
      val roundTripped = entityFormat.fromEntity(DatastoreService.toEntity(entity, entityFormat, datastoreService))
      roundTripped shouldBe Right(entity)
    }
  }

  val longGen = Gen.choose(Long.MinValue, Long.MaxValue).map(LongType(_))
  val stringGen = Gen.alphaNumStr.map(StringType(_))
  val objGen = Gen.const(ObjectType)
  val sealedTraitEntityGen = for {
    id <- Gen.alphaNumStr.filter(!_.isEmpty)
    sealedValue <- Gen.oneOf(longGen, stringGen, objGen)
  } yield EntityWithSealedType(id, sealedValue)

  sealed trait ValidSealedTrait

  @SubTypeName("AnnotatedLongType")
  case class LongType(long: Long) extends ValidSealedTrait

  case class StringType(string: String) extends ValidSealedTrait

  case object ObjectType extends ValidSealedTrait

  case class EntityWithSealedType(id: String, sealedValue: ValidSealedTrait)

  it should "create a field format that will serialise to any case class or object in the hierarchy" in {
    implicit val format = FieldFormat[ValidSealedTrait]
    val entityFormat = EntityFormat[EntityWithSealedType, String]("sealed-nested-test-kind")(_.id)

    forAll(sealedTraitEntityGen) { entity =>
      val roundTripped = entityFormat.fromEntity(DatastoreService.toEntity(entity, entityFormat, datastoreService))
      roundTripped shouldBe Right(entity)
    }
  }

  it should "use the annotation to determine the subtype name" in {
    implicit val format = FieldFormat[ValidSealedTrait]
    format.toEntityField("foo", LongType(10)).values.get("foo.type") shouldBe Some(StringValue("AnnotatedLongType"))
    format.toEntityField("foo", StringType("bar")).values.get("foo.type") shouldBe Some(StringValue("StringType"))
  }

  case class MissingFieldFormatType()

  case class MissingFieldFormatContainer(field: MissingFieldFormatType)

  it should "Not compile when passed a case class that has a field for which no FieldFormat is implicitly available" in {
    "FieldFormat[MissingFieldFormatContainer]" shouldNot compile
  }

  class NonCaseClass()

  it should "Not compile when passed a non case class" in {
    "FieldFormat[NonCaseClass]" shouldNot compile
  }

  trait NonSealedTraitClass

  it should "Not compile when passed a non sealed trait" in {
    "FieldFormat[NonSealedTraitClass]" shouldNot compile
  }

  sealed trait TraitWithNonCaseClass

  class Class(val something: String) extends TraitWithNonCaseClass

  it should "Only accept sealed traits with case class extensions" in {
    "FieldFormat[TraitWithNonCaseClass]" shouldNot compile
  }

  case class EmptyCaseClass()

  it should "Not compile for case classes with no fields" in {
    "FieldFormat[EmptyCaseClass]" shouldNot compile
  }

}
