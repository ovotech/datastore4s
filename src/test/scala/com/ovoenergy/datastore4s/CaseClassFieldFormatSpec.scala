package com.ovoenergy.datastore4s

import java.time.Instant

import com.ovoenergy.datastore4s.ValueFormat.InstantEpochMillisValueFormat
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}

class CaseClassFieldFormatSpec extends FlatSpec with Matchers with GeneratorDrivenPropertyChecks {

  implicit val datastoreService = DatastoreService.createDatastore(DataStoreConfiguration("test-project", "test-namespace"))

  implicit val instantFormat = InstantEpochMillisValueFormat
  val entityGen = for {
    id <- Gen.alphaNumStr.filter(!_.isEmpty)
    string <- Gen.alphaNumStr
    long <- Gen.choose(Long.MinValue, Long.MaxValue)
    int <- Gen.choose(Int.MinValue, Int.MaxValue)
    bool <- Gen.oneOf(true, false)
    time <- Gen.choose(Long.MinValue, Long.MaxValue).map(Instant.ofEpochMilli(_))
  } yield EntityWithNestedType(id, SomeNestedType(string, long, int, bool, time))

  "The apply method of NestedFieldFormat" should "create a field format that nests the fields of case classes" in {
    implicit val format = FieldFormat[SomeNestedType]
    val entityFormat = EntityFormat[EntityWithNestedType, String]("nested-test-kind")(_.id)

    forAll(entityGen) { entity =>
      val roundTripped = entityFormat.fromEntity(DatastoreService.toEntity(entity, entityFormat, datastoreService))
      roundTripped shouldBe Right(entity)
    }
  }

  it should "Not compile when passed a non case class" in {
    "NestedFieldFormat[NonCaseClass]" shouldNot compile
  }

  it should "Not compile when passed a case class that has a field for which no FieldFormat is implicitly available" in {
    "NestedFieldFormat[MissingFieldFormatContainer]" shouldNot compile
  }

  case class EntityWithNestedType(id: String, nestedType: SomeNestedType)

  case class SomeNestedType(stringField: String,
                            someLongField: Long,
                            someIntField: Int,
                            someBooleanField: Boolean,
                            someTimeField: Instant)

  class NonCaseClass(val value:String)

  case class MissingFieldFormatContainer(field: MissingFieldFormatType)

  case class MissingFieldFormatType()
}
