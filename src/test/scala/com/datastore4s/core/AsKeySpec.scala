package com.datastore4s.core

import com.google.cloud.datastore.{DatastoreOptions, Key}
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}
import com.datastore4s.core.AsKey._

class AsKeySpec extends FlatSpec with GeneratorDrivenPropertyChecks with Matchers {

  "The String AsKey" should "take any string and create a key" in {
    forallTestRoundTrip(StringAsKey)(Gen.alphaNumStr.filter(!_.isEmpty))
  }

  it should "store the value as the name of the key" in {
    testEntity(StringAsKey)("value") {
      _.getName() shouldBe "value"
    }
  }

  "The Long AsKey" should "take any long and create a key" in {
    forallTestRoundTrip(LongAsKey)(Gen.choose(Long.MinValue, Long.MaxValue))
  }

  it should "store the value as the id" in {
    testEntity(LongAsKey)(12L) {
      _.getId() shouldBe 12L
    }
  }

  def forallTestRoundTrip[A](asKey: AsKey[A])(generator: Gen[A]) = {
    forAll(generator) { value =>
      asKey.fromKey(createKey(asKey, value)) shouldBe value
    }
  }

  def testEntity[A](asKey: AsKey[A])(value: A)(assertion: Key => Unit) = {
    assertion(createKey(asKey, value))
  }

  private def createKey[A](asKey: AsKey[A], value: A) = {
    asKey.toKey(value, new KeyFactoryFacade(datastore.newKeyFactory().setKind("test-kind")))
  }

  private val datastore =  DatastoreOptions.newBuilder()
    .setProjectId("test-project")
    .setNamespace("test-namespace")
    .build().getService

}
