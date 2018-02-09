package com.ovoenergy.datastore4s.internal

import com.ovoenergy.datastore4s.internal.ValueFormat._
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}

class ValueFormatSpec extends FlatSpec with GeneratorDrivenPropertyChecks with Matchers {

  "The String value format" should "write strings to a string value" in {
    forAll(Gen.alphaNumStr) { str =>
      StringValueFormat.toValue(str) shouldBe StringValue(str)
    }
  }

  it should "read string values into strings" in {
    forAll(Gen.alphaNumStr) { str =>
      StringValueFormat.fromValue(StringValue(str)) shouldBe Right(str)
    }
  }

  it should "not read other types to strings" in {
    forAll(Gen.oneOf(longValueGen, doubleValueGen)) { value =>
      StringValueFormat.fromValue(value) shouldBe 'Left
    }
  }

  "The Long value format" should "write longs to a long value" in {
    forAll(Gen.choose(Long.MinValue, Long.MaxValue)) { long =>
      LongValueFormat.toValue(long) shouldBe LongValue(long)
    }
  }

  it should "read long values into longs" in {
    forAll(Gen.choose(Long.MinValue, Long.MaxValue)) { long =>
      LongValueFormat.fromValue(LongValue(long)) shouldBe Right(long)
    }
  }

  it should "not read other types to longs" in {
    forAll(Gen.oneOf(stringValueGen, doubleValueGen)) { value =>
      LongValueFormat.fromValue(value) shouldBe 'Left
    }
  }

  "The Double value format" should "write doubles to a double value" in {
    forAll(Gen.choose(Double.MinValue, Double.MaxValue)) { double =>
      DoubleValueFormat.toValue(double) shouldBe DoubleValue(double)
    }
  }

  it should "read double values into doubles" in {
    forAll(Gen.choose(Double.MinValue, Double.MaxValue)) { double =>
      DoubleValueFormat.fromValue(DoubleValue(double)) shouldBe Right(double)
    }
  }

  it should "not read other types to doubles" in {
    forAll(Gen.oneOf(stringValueGen, longValueGen)) { value =>
      DoubleValueFormat.fromValue(value) shouldBe 'Left
    }
  }


  private val stringValueGen = Gen.alphaNumStr.map(StringValue(_))
  private val longValueGen = Gen.choose(Long.MinValue, Long.MaxValue).map(LongValue(_))
  private val doubleValueGen = Gen.choose(Double.MinValue, Double.MaxValue).map(DoubleValue(_))

}
