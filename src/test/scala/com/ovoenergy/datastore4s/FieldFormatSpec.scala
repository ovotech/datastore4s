package com.ovoenergy.datastore4s


import org.scalacheck.Gen
import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import com.ovoenergy.datastore4s.FieldFormat._

class FieldFormatSpec extends FlatSpec with GeneratorDrivenPropertyChecks with Matchers {

  "The implicit value to field format function" should "take any value format and use it to store it as a field" in {
    forallTestRoundTrip(Gen.alphaNumStr)
  }

  it should "store the value as a string field if using the string value format" in {
    testEntity("value") {
      _.field(fieldName) shouldBe Some(StringValue("value"))
    }
  }

  "The Option implicit def format" should "take any value for which there is a format and store that field or null" in {
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
    implicit val format = ValueFormat.formatFrom(SimpleWrapper.apply)(_.innerValue)
    forallTestRoundTrip(Gen.alphaNumStr.map(SimpleWrapper(_)))

    testEntity(SimpleWrapper("hello")) { entity =>
      entity.field(fieldName) shouldBe Some(StringValue("hello"))
    }
  }

  "The Either implicit def format" should "take any value for which there is a format and store that field" in {
    val generator: Gen[Either[String, Int]] = for {
      left <- Gen.alphaNumStr.map(Left(_))
      right <- Gen.choose(Int.MinValue, Int.MaxValue).map(Right(_))
      gen <- Gen.oneOf(left, right)
    } yield gen
    forallTestRoundTrip(generator)

    testEntity(Left("hello"): Either[String, Int]) { entity =>
      entity.field(fieldName) shouldBe Some(StringValue("hello"))
      entity.field(fieldName + ".either_side") shouldBe Some(StringValue("Left"))
    }
    testEntity(Right(12): Either[String, Int]) { entity =>
      entity.field(fieldName) shouldBe Some(LongValue(12))
      entity.field(fieldName + ".either_side") shouldBe Some(StringValue("Right"))
    }
  }

  private val firstProperty = "testName1"
  private val secondProperty = "testName2"
  private val field = Field(Map(firstProperty -> StringValue("testValue1"), secondProperty -> LongValue(12)))

  "Fields" should "be indexed by default" in {
    (field.values.get(firstProperty), field.values.get(firstProperty)) match {
      case (Some(firstWrapped: WrappedValue), Some(secondWrapped: WrappedValue)) =>
        firstWrapped.dsValue.excludeFromIndexes() shouldBe false
        secondWrapped.dsValue.excludeFromIndexes() shouldBe false
      case other => fail(s"expected 2 wrapped values for $firstProperty and $secondProperty but got $other")
    }

  }

  "Fields" should "be able to have their indexes ignored" in {
    val ignored = field.ignoreIndexes
    (ignored.values.get(firstProperty), ignored.values.get(firstProperty)) match {
      case (Some(firstWrapped: WrappedValue), Some(secondWrapped: WrappedValue)) =>
        firstWrapped.dsValue.excludeFromIndexes() shouldBe true
        secondWrapped.dsValue.excludeFromIndexes() shouldBe true
      case other => fail(s"expected 2 wrapped values for $firstProperty and $secondProperty but got $other")
    }

  }

  val fieldName = "FIELD"

  private def forallTestRoundTrip[A](generator: Gen[A])(implicit fieldFormat: FieldFormat[A]) = {
    forAll(generator) { value =>
      val entity = createEntityWithField(fieldFormat, value)
      val roundTripped = fieldFormat.fromEntityFieldWithContext(fieldName, entity)
      roundTripped shouldBe Right(value)
    }
  }

  private def testEntity[A](value: A)(assertion: Entity => Unit)(implicit fieldFormat: FieldFormat[A]) = {
    assertion(createEntityWithField(fieldFormat, value))
  }

  private val datastoreService = DatastoreService(DatastoreConfiguration("test-project", "test-namespace"))

  private def createEntityWithField[A](fieldFormat: FieldFormat[A], value: A) = {
    val key = datastoreService.createKey("test-key", Kind("test-kind"))
    new WrappedBuilder(key).addField(fieldFormat.toEntityField(fieldName, value)).build()
  }

}
