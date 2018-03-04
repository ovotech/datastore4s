package com.ovoenergy.datastore4s

import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}

class SealedTraitFieldFormatSpec extends FlatSpec with Matchers with GeneratorDrivenPropertyChecks {

  implicit val datastoreService = DatastoreService.createDatastoreService(DataStoreConfiguration("test-project", "test-namespace"))

  val entityGen = for {
    id <- Gen.alphaNumStr.filter(!_.isEmpty)
    sealedValue <- Gen.oneOf(Gen.choose(Long.MinValue, Long.MaxValue).map(LongType(_)), Gen.alphaNumStr.map(StringType(_)))
  } yield EntityWithSealedType(id, sealedValue)

  trait NonSealedTraitClass

  class NonCaseClass()

  case class CaseClass()

  sealed trait TraitWithObject

  object Object extends TraitWithObject

  sealed trait TraitWithNonCaseClass

  class Class(val something: String) extends TraitWithNonCaseClass

  sealed trait ValidSealedTrait

  case class LongType(long: Long) extends ValidSealedTrait

  case class StringType(string: String) extends ValidSealedTrait

  case class EntityWithSealedType(id: String, sealedValue: ValidSealedTrait)

  "The apply method of SealedFieldFormat" should "create a field format that will serialise to any case class in the hierarchy" in {
    implicit val format = FieldFormat[ValidSealedTrait]
    val entityFormat = EntityFormat[EntityWithSealedType, String]("nested-test-kind")(_.id)

    forAll(entityGen) { entity =>
      val roundTripped = entityFormat.fromEntity(DatastoreService.toEntity(entity, entityFormat, datastoreService))
      roundTripped shouldBe Right(entity)
    }
  }

  it should "Not compile when passed a non sealed trait" in {
    "SealedFieldFormat[NonSealedTraitClass]" shouldNot compile
    "SealedFieldFormat[String]" shouldNot compile
    "SealedFieldFormat[NonCaseClass]" shouldNot compile
    "SealedFieldFormat[CaseClass]" shouldNot compile
  }

  it should "Only accept sealed traits with case class extensions" in {
    "SealedFieldFormat[TraitWithObject]" shouldNot compile
    "SealedFieldFormat[TraitWithNonCaseClass]" shouldNot compile
  }

}
