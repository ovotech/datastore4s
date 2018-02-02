package com.datastore4s.core

import java.time.Instant

import com.datastore4s.core.utils.TestDatastore
import com.google.cloud.datastore.DatastoreOptions
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}

import scala.util.Success

class FieldFormatCaseClassSpec extends FlatSpec with Matchers with GeneratorDrivenPropertyChecks {

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

  @EntityKind("nested-test-kind")
  case class EntityWithNestedType(id: String, nestedType: SomeNestedType) extends DatastoreEntity[String] {
    def key = id
  }

  case class SomeNestedType(stringField: String,
                            someLongField: Long,
                            someIntField: Long,
                            someBooleanField: Boolean,
                            someTimeField: Instant)

}
