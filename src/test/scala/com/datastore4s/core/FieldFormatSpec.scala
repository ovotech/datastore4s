package com.datastore4s.core

import java.time.Instant

import com.google.cloud.datastore.{Entity, Key, LatLng}
import org.scalacheck.Gen
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import com.datastore4s.core.FieldFormat._
import com.google.cloud.Timestamp

class FieldFormatSpec extends FlatSpec with FieldFormatTestCases {

  "The String format" should "take any string and store it as a field" in {
    forallTestRoundTrip(StringFieldFormat)(Gen.alphaNumStr)
  }

  it should "store the value as a string field" in {
    testEntity(StringFieldFormat)("value") {
      _.getString(fieldName) shouldBe "value"
    }
  }

  "The Long format" should "take any long and store it as a field" in {
    forallTestRoundTrip(LongFieldFormat)(Gen.choose(Long.MinValue, Long.MaxValue))
  }

  it should "store the value as a long field" in {
    testEntity(LongFieldFormat)(12L) {
      _.getLong(fieldName) shouldBe 12L
    }
  }

  "The Boolean format" should "take any boolean and store it as a field" in {
    forallTestRoundTrip(BooleanFieldFormat)(Gen.oneOf(true, false))
  }

  it should "store the value as a boolean field" in {
    testEntity(BooleanFieldFormat)(true) {
      _.getBoolean(fieldName) shouldBe true
    }
  }

  "The Double format" should "take any double and store it as a field" in {
    forallTestRoundTrip(DoubleFieldFormat)(Gen.choose(Double.MinValue, Double.MaxValue))
  }

  it should "store the value as a double field" in {
    testEntity(DoubleFieldFormat)(5.65842126) {
      _.getDouble(fieldName) shouldBe 5.65842126
    }
  }

  "The Int format" should "take any integer and store it as a field" in {
    forallTestRoundTrip(IntFieldFormat)(Gen.choose(Int.MinValue, Int.MaxValue))
  }

  it should "store the value as a long field" in {
    testEntity(IntFieldFormat)(10) {
      _.getLong(fieldName).toInt shouldBe 10
    }
  }

  "The ByteArrayBlob format" should "take any blob and store it as a field" in {
    forallTestRoundTrip(ByteArrayBlobFieldFormat)(Gen.alphaNumStr.map(_.getBytes()))
  }

  it should "store the value as a blob field" in {
    val bytes = "Hello test world".getBytes()
    testEntity(ByteArrayBlobFieldFormat)(bytes) { entity =>
      new String(entity.getBlob(fieldName).toByteArray) shouldBe "Hello test world"
    }
  }

  "The Timestamp format" should "take any timestamp and store it as a field" in {
    forallTestRoundTrip(TimestampFieldFormat)(Gen.choose(0L, 100000L).map(Timestamp.ofTimeMicroseconds(_)))
  }

  it should "store the value as a blob field" in {
    val time = Timestamp.now()
    testEntity(TimestampFieldFormat)(time) { entity =>
      entity.getTimestamp(fieldName) shouldBe time
    }
  }

  "The LatLng format" should "take any latlng and store it as a field" in {
    val latLngGen = for {
      lat <- Gen.choose(-90, 90)
      lang <- Gen.choose(-90, 90)
    } yield LatLng.of(lat, lang)
    forallTestRoundTrip(LatLngFieldFormat)(latLngGen)
  }

  it should "store the value as a blob field" in {
    val latLang = LatLng.of(12, 109.2)
    testEntity(LatLngFieldFormat)(latLang) { entity =>
      entity.getLatLng(fieldName) shouldBe latLang
    }
  }

  "The EpochMilli format" should "take any Instant and store it as a field" in {
    forallTestRoundTrip(InstantEpochMilliFormat)(Gen.choose(Long.MinValue, Long.MaxValue).map(Instant.ofEpochMilli(_)))
  }

  it should "store the value as a blob field" in {
    val millis = 1236785472L
    testEntity(InstantEpochMilliFormat)(Instant.ofEpochMilli(millis)) { entity =>
      entity.getLong(fieldName) shouldBe millis
    }
  }

  "The BigDecimalString format" should "take any BigDecimal and store it as a field" in {
    forallTestRoundTrip(BigDecimalStringFormat)(Gen.choose(Double.MinValue, Double.MaxValue).map(BigDecimal(_)))
  }

  it should "store the value as a blob field" in {
    val amount = 601623.873
    testEntity(BigDecimalStringFormat)(BigDecimal(amount)) { entity =>
      entity.getString(fieldName) shouldBe "601623.873"
    }
  }

  "The Option implicit def format" should "take any value for which there is a a format and store that field or null" in {
    val optionalStringFormat = implicitly[FieldFormat[Option[String]]]
    val generator = for {
      s <- Gen.alphaNumStr
      opt <- Gen.oneOf(Option(s), None)
    } yield opt
    forallTestRoundTrip(optionalStringFormat)(generator)

    testEntity(optionalStringFormat)(Option("hello")) { entity =>
      entity.getString(fieldName) shouldBe "hello"
    }
    testEntity(optionalStringFormat)(None) { entity =>
      entity.getString(fieldName) shouldBe null
    }
  }

}

trait FieldFormatTestCases extends GeneratorDrivenPropertyChecks with Matchers {

  val fieldName = "FIELD"

  def forallTestRoundTrip[A](fieldFormat: FieldFormat[A])(generator: Gen[A]) = {
    forAll(generator) { value =>
      val entity = createEntityWithField(fieldFormat, value)
      val roundTripped = fieldFormat.fromField(entity, fieldName)
      roundTripped shouldBe value
    }
  }

  def testEntity[A](fieldFormat: FieldFormat[A])(value: A)(assertion: Entity => Unit) = {
    assertion(createEntityWithField(fieldFormat, value))
  }

  private def createEntityWithField[A](fieldFormat: FieldFormat[A], value: A) = {
    val builder = Entity.newBuilder(Key.newBuilder("test-project", "test-kind", 1L).build())
    fieldFormat.addField(value, fieldName, builder).build()
  }

}
