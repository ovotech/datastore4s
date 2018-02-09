package com.ovoenergy.datastore4s.internal

import com.ovoenergy.datastore4s.internal.ValueFormat.StringValueFormat
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}

class ValueFormatSpec extends FlatSpec with GeneratorDrivenPropertyChecks with Matchers {

  "The String value format" should "write strings to a string value" in {
    forAll(Gen.alphaNumStr){ str =>
      StringValueFormat.toValue(str) shouldBe StringValue(str)
    }
  }

  it should "read string values into strings" in {
    forAll(Gen.alphaNumStr){ str =>
      StringValueFormat.fromValue(StringValue(str)) shouldBe Right(str)
    }
  }

  it should "not read other types to strings" in {
    forAll(Gen.oneOf(longValueGen, doubleValueGen)){ value =>
      StringValueFormat.fromValue(value) shouldBe 'Left
    }
  }

  private val longValueGen = Gen.choose(Long.MinValue, Long.MaxValue).map(LongValue(_))
  private val doubleValueGen = Gen.choose(Double.MinValue, Double.MaxValue).map(DoubleValue(_))

}
