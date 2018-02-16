package com.ovoenergy.datastore4s

import java.time.Instant

import com.google.cloud.datastore.{Blob, LatLng}
import org.scalacheck.Gen
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import com.ovoenergy.datastore4s.FieldFormat._
import com.google.cloud.Timestamp
import com.ovoenergy.datastore4s.internal._
import com.ovoenergy.datastore4s.internal.ValueFormat.{BigDecimalStringValueFormat, ByteArrayValueFormat, InstantEpochMillisValueFormat}
import com.ovoenergy.datastore4s.utils.StubEntityBuilder

class FieldFormatSpec extends FlatSpec with FieldFormatTestCases {

  implicit val instantFormat = InstantEpochMillisValueFormat // TODO just reduce to one simple case
  implicit val bdFormat = BigDecimalStringValueFormat
  implicit val baFormat = ByteArrayValueFormat

  "The String format" should "take any string and store it as a field" in {
    forallTestRoundTrip(Gen.alphaNumStr)
  }

  it should "store the value as a string field" in {
    testEntity("value") {
      _.field(fieldName) shouldBe Some(StringValue("value"))
    }
  }

  "The Long format" should "take any long and store it as a field" in {
    forallTestRoundTrip(Gen.choose(Long.MinValue, Long.MaxValue))
  }

  it should "store the value as a long field" in {
    testEntity(12L) {
      _.field(fieldName) shouldBe Some(LongValue(12L))
    }
  }

  "The Boolean format" should "take any boolean and store it as a field" in {
    forallTestRoundTrip(Gen.oneOf(true, false))
  }

  it should "store the value as a boolean field" in {
    testEntity(true) {
      _.field(fieldName) shouldBe Some(BooleanValue(true))
    }
  }

  "The Double format" should "take any double and store it as a field" in {
    forallTestRoundTrip(Gen.choose(Double.MinValue, Double.MaxValue))
  }

  it should "store the value as a double field" in {
    testEntity(5.65842126) {
      _.field(fieldName) shouldBe Some(DoubleValue(5.65842126))
    }
  }

  "The Int format" should "take any integer and store it as a field" in {
    forallTestRoundTrip(Gen.choose(Int.MinValue, Int.MaxValue))
  }

  it should "store the value as a long field" in {
    testEntity(10) {
      _.field(fieldName) shouldBe Some(LongValue(10))
    }
  }

  "The Timestamp format" should "take any timestamp and store it as a field" in {
    forallTestRoundTrip(Gen.choose(0L, 100000L).map(Timestamp.ofTimeMicroseconds(_)))
  }

  it should "store the value as a blob field" in {
    val time = Timestamp.now()
    testEntity(time) { entity =>
      entity.field(fieldName) shouldBe Some(TimestampValue(time))
    }
  }

  "The LatLng format" should "take any latlng and store it as a field" in {
    val latLngGen = for {
      lat <- Gen.choose(-90, 90)
      lang <- Gen.choose(-90, 90)
    } yield LatLng.of(lat, lang)
    forallTestRoundTrip(latLngGen)
  }

  it should "store the value as a blob field" in {
    val latLang = LatLng.of(12, 109.2)
    testEntity(latLang) { entity =>
      entity.field(fieldName) shouldBe Some(LatLngValue(latLang))
    }
  }

  "The EpochMilli format" should "take any Instant and store it as a field" in {
    forallTestRoundTrip(Gen.choose(Long.MinValue, Long.MaxValue).map(Instant.ofEpochMilli(_)))
  }

  it should "store the value as a long field" in {
    val millis = 1236785472L
    testEntity(Instant.ofEpochMilli(millis)) { entity =>
      entity.field(fieldName) shouldBe Some(LongValue(millis))
    }
  }

  "The BigDecimalString format" should "take any BigDecimal and store it as a field" in {
    forallTestRoundTrip(Gen.choose(Double.MinValue, Double.MaxValue).map(BigDecimal(_)))
  }

  it should "store the value as a string field" in {
    val amount = 601623.873
    testEntity(BigDecimal(amount)) { entity =>
      entity.field(fieldName) shouldBe Some(StringValue("601623.873"))
    }
  }

  "The Option implicit def format" should "take any value for which there is a a format and store that field or null" in {
    val generator = for {
      s <- Gen.alphaNumStr
      opt <- Gen.oneOf(Option(s), None)
    } yield opt
    forallTestRoundTrip(generator)

    testEntity(Option("hello")) { entity =>
      entity.field(fieldName) shouldBe Some(StringValue("hello"))
    }
    testEntity(Option.empty[String]) { entity =>
      entity.field(fieldName) shouldBe 'defined
    }
  }

  "Field format generated from functions" should "wrap an existing format in constructor and extractor functions" in {
    case class SimpleWrapper(innerValue: String)
    implicit val format = FieldFormat.fieldFormatFromFunctions(SimpleWrapper.apply)(_.innerValue) // TODO should this be value format??? Probs
    forallTestRoundTrip(Gen.alphaNumStr.map(SimpleWrapper(_)))

    testEntity(SimpleWrapper("hello")) { entity =>
      entity.field(fieldName) shouldBe Some(StringValue("hello"))
    }
  }

}

trait FieldFormatTestCases extends GeneratorDrivenPropertyChecks with Matchers {

  val fieldName = "FIELD"

  def forallTestRoundTrip[A](generator: Gen[A])(implicit fieldFormat: FieldFormat[A]) = {
    forAll(generator) { value =>
      val entity = createEntityWithField(fieldFormat, value)
      val roundTripped = fieldFormat.fromField(entity, fieldName)
      roundTripped shouldBe Right(value)
    }
  }

  def testEntity[A](value: A)(assertion: Entity => Unit)(implicit fieldFormat: FieldFormat[A]) = {
    assertion(createEntityWithField(fieldFormat, value))
  }

  private def createEntityWithField[A](fieldFormat: FieldFormat[A], value: A) = {
    fieldFormat.addField(value, fieldName, StubEntityBuilder()).build()
  }

}
