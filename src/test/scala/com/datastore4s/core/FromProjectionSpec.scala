package com.datastore4s.core

import java.time.Instant

import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FeatureSpec, Matchers}

class FromProjectionSpec extends FeatureSpec with Matchers with GeneratorDrivenPropertyChecks {

  feature("The FromProjection macro") {
    scenario("Attempt to make a FromProjection of a type that is not a case class") {
      "FromProjection[NonCaseClass]" shouldNot compile
    }
    scenario("Attempt to make a FromProjection when an implicit field format is not available") {
      "FromProjection[MissingKeyFormatContainer]" shouldNot compile
    }
    scenario("A simple case class") {
      // TODO cannot currently make this test since Projection entities cannot be created in the test.
    }
  }

  class NonCaseClass(val key: String)

  case class MissingFieldFormatType()

  case class MissingKeyFormatContainer(key: MissingFieldFormatType)

  case class SomeCustomType(stringField: String,
                            someLongField: Long,
                            someIntField: Int,
                            someBooleanField: Boolean,
                            someTimeField: Instant)

}
