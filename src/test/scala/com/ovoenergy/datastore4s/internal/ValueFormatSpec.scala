package com.ovoenergy.datastore4s.internal

import java.time.Instant

import com.google.cloud.Timestamp
import com.google.cloud.datastore.{Blob, LatLng}
import com.ovoenergy.datastore4s.internal.ValueFormat._
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FlatSpec, Inside, Matchers}

class ValueFormatSpec extends FlatSpec with GeneratorDrivenPropertyChecks with Matchers with Inside {

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
    forAll(Gen.oneOf(longValueGen, doubleValueGen, booleanValueGen, blobValueGen, timestampValueGen, latLngValueGen)) { value =>
      StringValueFormat.fromValue(value) shouldBe 'Left
    }
  }

  it should "read a written value correctly" in {
    forAllTestRoundTrip(Gen.alphaNumStr)
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
    forAll(Gen.oneOf(stringValueGen, doubleValueGen, booleanValueGen, blobValueGen, timestampValueGen, latLngValueGen)) { value =>
      LongValueFormat.fromValue(value) shouldBe 'Left
    }
  }

  it should "read a written value correctly" in {
    forAllTestRoundTrip(Gen.choose(Long.MinValue, Long.MaxValue))
  }

  "The Int value format" should "write ints to a int value" in {
    forAll(Gen.choose(Int.MinValue, Int.MaxValue)) { int =>
      IntValueFormat.toValue(int) shouldBe LongValue(int)
    }
  }

  it should "read int values into ints" in {
    forAll(Gen.choose(Int.MinValue, Int.MaxValue)) { int =>
      IntValueFormat.fromValue(LongValue(int)) shouldBe Right(int)
    }
  }

  it should "read a written value correctly" in {
    forAllTestRoundTrip(Gen.choose(Int.MinValue, Int.MaxValue))
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
    forAll(Gen.oneOf(stringValueGen, longValueGen, booleanValueGen, blobValueGen, timestampValueGen, latLngValueGen)) { value =>
      DoubleValueFormat.fromValue(value) shouldBe 'Left
    }
  }

  it should "read a written value correctly" in {
    forAllTestRoundTrip(Gen.choose(Double.MinValue, Double.MaxValue))
  }

  "The Boolean value format" should "write booleans to a boolean value" in {
    forAll(Gen.oneOf(true, false)) { bool =>
      BooleanValueFormat.toValue(bool) shouldBe BooleanValue(bool)
    }
  }

  it should "read boolean values into booleans" in {
    forAll(Gen.oneOf(true, false)) { bool =>
      BooleanValueFormat.fromValue(BooleanValue(bool)) shouldBe Right(bool)
    }
  }

  it should "not read other types to booleans" in {
    forAll(Gen.oneOf(stringValueGen, longValueGen, doubleValueGen, blobValueGen, timestampValueGen, latLngValueGen)) { value =>
      BooleanValueFormat.fromValue(value) shouldBe 'Left
    }
  }

  it should "read a written value correctly" in {
    forAllTestRoundTrip(Gen.oneOf(true, false))
  }

  "The Blob value format" should "write blobs to a blob value" in {
    forAll(byteArrayGen.map(Blob.copyFrom)) { blob =>
      BlobValueFormat.toValue(blob) shouldBe BlobValue(blob)
    }
  }

  it should "read blob values into blobs" in {
    forAll(byteArrayGen.map(Blob.copyFrom)) { blob =>
      BlobValueFormat.fromValue(BlobValue(blob)) shouldBe Right(blob)
    }
  }

  it should "not read other types to blob" in {
    forAll(Gen.oneOf(stringValueGen, longValueGen, doubleValueGen, booleanValueGen, timestampValueGen, latLngValueGen)) { value =>
      BlobValueFormat.fromValue(value) shouldBe 'Left
    }
  }

  it should "read a written value correctly" in {
    forAllTestRoundTrip(byteArrayGen.map(Blob.copyFrom))
  }

  "The Timestamp value format" should "write timestamps to a timestamp value" in {
    forAll(timestampGen) { timestamp =>
      TimestampValueFormat.toValue(timestamp) shouldBe TimestampValue(timestamp)
    }
  }

  it should "read timestamp values into timestamps" in {
    forAll(timestampGen) { timestamp =>
      TimestampValueFormat.fromValue(TimestampValue(timestamp)) shouldBe Right(timestamp)
    }
  }

  it should "not read other types to timestamps" in {
    forAll(Gen.oneOf(stringValueGen, longValueGen, doubleValueGen, booleanValueGen, blobValueGen, latLngValueGen)) { value =>
      TimestampValueFormat.fromValue(value) shouldBe 'Left
    }
  }

  it should "read a written value correctly" in {
    forAllTestRoundTrip(timestampGen)
  }

  "The LatLng value format" should "write latlngs to a latlng value" in {
    forAll(latLngGen) { latlng =>
      LatLngValueFormat.toValue(latlng) shouldBe LatLngValue(latlng)
    }
  }

  it should "read latlng values into latlngs" in {
    forAll(latLngGen) { latlng =>
      LatLngValueFormat.fromValue(LatLngValue(latlng)) shouldBe Right(latlng)
    }
  }

  it should "not read other types to latlngs" in {
    forAll(Gen.oneOf(stringValueGen, longValueGen, doubleValueGen, booleanValueGen, blobValueGen, timestampValueGen)) { value =>
      LatLngValueFormat.fromValue(value) shouldBe 'Left
    }
  }

  it should "read a written value correctly" in {
    forAllTestRoundTrip(latLngGen)
  }

  "The ByteArray value format" should "write byte arrays to a blob value" in {
    forAll(byteArrayGen.filter(!_.isEmpty)) { byteArray =>
      ByteArrayValueFormat.toValue(byteArray) shouldBe BlobValue(Blob.copyFrom(byteArray))
    }
  }

  it should "read blob values into byte arrays" in {
    forAll(byteArrayGen.filter(!_.isEmpty)) { byteArray =>
      ByteArrayValueFormat.fromValue(BlobValue(Blob.copyFrom(byteArray))).map(_.deep) shouldBe Right(byteArray.deep)
    }
  }

  it should "read a written value correctly" in {
    forAll(byteArrayGen.filter(!_.isEmpty)) { byteArray =>
      ByteArrayValueFormat.fromValue(ByteArrayValueFormat.toValue(byteArray)).map(_.deep) shouldBe Right(byteArray.deep)
    }
  }

  "The Instant value format" should "write instants to a long value" in {
    forAll(Gen.choose(Long.MinValue, Long.MaxValue)) { millis =>
      InstantEpochMillisValueFormat.toValue(Instant.ofEpochMilli(millis)) shouldBe LongValue(millis)
    }
  }

  it should "read long values into instants" in {
    forAll(Gen.choose(Long.MinValue, Long.MaxValue)) { millis =>
      InstantEpochMillisValueFormat.fromValue(LongValue(millis)) shouldBe Right(Instant.ofEpochMilli(millis))
    }
  }

  it should "read a written value correctly" in {
    implicit val format = InstantEpochMillisValueFormat
    forAllTestRoundTrip(Gen.choose(Long.MinValue, Long.MaxValue).map(Instant.ofEpochMilli))
  }

  "The BigDecimal value format" should "write bigdecimals to a string value" in {
    forAll(bigDecimalGen) { bigDecimal =>
      BigDecimalStringValueFormat.toValue(bigDecimal) shouldBe StringValue(bigDecimal.toString())
    }
  }

  it should "read string values into bigdecimals" in {
    forAll(bigDecimalGen) { bigDecimal =>
      BigDecimalStringValueFormat.fromValue(StringValue(bigDecimal.toString())) shouldBe Right(bigDecimal)
    }
  }

  it should "return an error if the string is not numerical" in {
    forAll(Gen.alphaStr) { string =>
      BigDecimalStringValueFormat.fromValue(StringValue(string)) shouldBe 'Left
    }
  }

  it should "read a written value correctly" in {
    implicit val format = BigDecimalStringValueFormat
    forAllTestRoundTrip(bigDecimalGen)
  }

  "The list value format" should "write any A to a list value" in {
    forAll(Gen.listOf(Gen.alphaNumStr)) { stringList =>
      val format = implicitly[ValueFormat[Seq[String]]]
      format.toValue(stringList) shouldBe ListValue(stringList.map(StringValue(_)))
    }
  }

  it should "read list values into lists" in {
    forAll(Gen.listOf(Gen.alphaNumStr)) { stringList =>
      val format = implicitly[ValueFormat[Seq[String]]]
      inside(format.fromValue(ListValue(stringList.map(StringValue(_))))) {
        case Right(list) => list should contain theSameElementsAs stringList
        case Left(error) => fail(s"Expected a Right of a list of strings but got: $error")
      }
    }
  }

  it should "return an error if any element of the list is the wrong type" in {
    forAll(Gen.listOf(Gen.alphaNumStr)) { stringList =>
      val format = implicitly[ValueFormat[Seq[String]]]

      val values = LongValue(0) +: stringList.map(StringValue(_))
      format.fromValue(ListValue(values)) shouldBe 'Left
    }
  }

  it should "read a written value correctly" in {
    val listGen: Gen[Seq[String]] = Gen.listOf(Gen.alphaNumStr)
    forAll(listGen) { stringList =>
      val format = implicitly[ValueFormat[Seq[String]]]
      inside(format.fromValue(format.toValue(stringList))) {
        case Right(list) => list should contain theSameElementsAs stringList
        case Left(error) => fail(s"Expected a Right of a list of strings but got: $error")
      }
    }
  }

  "The option value format" should "write any A to a value" in {
    val format = implicitly[ValueFormat[Option[String]]]
    forAll(Gen.alphaNumStr) { someString =>
      format.toValue(Some(someString)) shouldBe StringValue(someString)
    }
  }

  it should "write a None to a null value" in {
    val format = implicitly[ValueFormat[Option[String]]]
    format.toValue(None) shouldBe NullValue()
  }

  it should "read a value into options" in {
    val format = implicitly[ValueFormat[Option[String]]]
    forAll(Gen.alphaNumStr) { someString =>
      format.fromValue(StringValue(someString)) shouldBe Right(Some(someString))
    }
  }

  it should "read a null value to a None" in {
    val format = implicitly[ValueFormat[Option[String]]]
    format.fromValue(NullValue()) shouldBe Right(None)
  }

  it should "read a written value correctly" in {
    val optionGenerator: Gen[Option[String]] =
      Gen.alphaNumStr.flatMap(string => Gen.oneOf(Option.empty[String], Some(string)))
    forAllTestRoundTrip(optionGenerator)
  }

  "The value format from functions" should "be able to create a value format from construction and destruction functions" in {
    case class SimpleWrapper(innerValue: String)
    implicit val format = ValueFormat.formatFromFunctions(SimpleWrapper.apply)(_.innerValue)
    format.toValue(SimpleWrapper("hello")) shouldBe StringValue("hello")
    format.fromValue(StringValue("hello")) shouldBe Right(SimpleWrapper("hello"))
    forAllTestRoundTrip(Gen.alphaNumStr.map(SimpleWrapper(_)))
  }

  it should "be able to create a value format from construction and destruction functions where the constructor can fail" in {
    case class PositiveIntWrapper(val innerValue: Int)
    object PositiveIntWrapper {
      def apply(value: Int): Either[String, PositiveIntWrapper] =
        if (value < 0) Left("Only accepting positive ints") else Right(new PositiveIntWrapper(value))
    }
    implicit val format = ValueFormat.formatFromFunctionsEither(PositiveIntWrapper.apply)(_.innerValue)
    format.toValue(new PositiveIntWrapper(10)) shouldBe LongValue(10)
    format.fromValue(LongValue(10)) shouldBe Right(new PositiveIntWrapper(10))
    format.fromValue(LongValue(-10)) shouldBe 'Left
    forAll(Gen.choose(0, Int.MaxValue), Gen.choose(Int.MinValue, -1)) { (positive, negative) =>
      format.fromValue(LongValue(positive)) shouldBe Right(new PositiveIntWrapper(positive))
      format.fromValue(LongValue(negative)) shouldBe 'Left
    }
  }

  private val stringValueGen = Gen.alphaNumStr.map(StringValue(_))
  private val longValueGen = Gen.choose(Long.MinValue, Long.MaxValue).map(LongValue(_))
  private val doubleValueGen = Gen.choose(Double.MinValue, Double.MaxValue).map(DoubleValue(_))
  private val booleanValueGen = Gen.oneOf(true, false).map(BooleanValue(_))
  private val byteArrayGen: Gen[Array[Byte]] = Gen.alphaNumStr.map(_.getBytes())
  private val blobValueGen = byteArrayGen.map(Blob.copyFrom).map(BlobValue(_))
  private val timestampGen = Gen.choose(0L, 100000L).map(Timestamp.ofTimeMicroseconds)
  private val timestampValueGen = timestampGen.map(TimestampValue(_))
  private val latLngGen = for {
    lat <- Gen.choose(-90, 90)
    lang <- Gen.choose(-90, 90)
  } yield LatLng.of(lat, lang)
  private val latLngValueGen = latLngGen.map(LatLngValue(_))
  private val bigDecimalGen = Gen.choose(Double.MinValue, Double.MaxValue).map(BigDecimal(_))

  private def forAllTestRoundTrip[A](generator: Gen[A])(implicit format: ValueFormat[A]) = {
    forAll(generator)(value => format.fromValue(format.toValue(value)) shouldBe Right(value))
  }

}
