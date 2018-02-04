package com.datastore4s.core

import java.time.Instant

import com.datastore4s.core.utils.TestDatastore
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}

class NestedFieldFormatSpec extends FlatSpec with Matchers with GeneratorDrivenPropertyChecks {

  val datastore = TestDatastore()

  implicit val keyFactorySupplier = () => datastore.newKeyFactory()

  val entityGen = for {
    id <- Gen.alphaNumStr.filter(!_.isEmpty)
    string <- Gen.alphaNumStr
    long <- Gen.choose(Long.MinValue, Long.MaxValue)
    int <- Gen.choose(Int.MinValue, Int.MaxValue)
    bool <- Gen.oneOf(true, false)
    time <- Gen.choose(Long.MinValue, Long.MaxValue).map(Instant.ofEpochMilli(_))
  } yield EntityWithNestedType(id, SomeNestedType(string, long, int, bool, time))

  "The apply method of Field Format" should "create a field format that nests the fields of case classes" in {
    implicit val format = NestedFieldFormat[SomeNestedType]
    val entityFormat = EntityFormat[EntityWithNestedType, String]

    forAll(entityGen) { entity =>
      val roundTripped = entityFormat.fromEntity(entityFormat.toEntity(entity))
      roundTripped shouldBe entity
    }
  }

  it should "Not compile when passed a non case class" in {
    "NestedFieldFormat[NonCaseClass]" shouldNot compile
  }

  it should "Not compile when passed a case class that has a field for which no FieldFormat is implicitly available" in {
    "NestedFieldFormat[MissingFieldFormatContainer]" shouldNot compile
  }

  @EntityKind("nested-test-kind")
  case class EntityWithNestedType(id: String, nestedType: SomeNestedType) extends DatastoreEntity[String] {
    def key = id
  }

  case class SomeNestedType(stringField: String,
                            someLongField: Long,
                            someIntField: Long,
                            someBooleanField: Boolean,
                            someTimeField: Instant)

  class NonCaseClass(val value:String)

  case class MissingFieldFormatContainer(field: MissingFieldFormatType)

  case class MissingFieldFormatType()
}
