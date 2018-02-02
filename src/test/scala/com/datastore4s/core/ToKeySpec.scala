package com.datastore4s.core

import com.google.cloud.datastore.{DatastoreOptions, Key}
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}
import com.datastore4s.core.ToKey._
import com.datastore4s.core.utils.{TestDatastore, TestKeyFactory}

class ToKeySpec extends FlatSpec with GeneratorDrivenPropertyChecks with Matchers {

  "The String AsKey" should "take any string and create a key using the key name" in {
    forAll(Gen.alphaNumStr.filter(!_.isEmpty)) { value =>
      createKey(StringToKey, value).getName shouldBe value
    }
  }

  "The Long AsKey" should "take any long and create a key using the key id" in {
    forAll(Gen.choose(Long.MinValue, Long.MaxValue)) { value =>
      createKey(LongToKey, value).getId shouldBe value
    }
  }

  private val datastore = TestDatastore()

  def testKey[A](asKey: ToKey[A])(value: A)(assertion: Key => Unit) = {
    assertion(createKey(asKey, value))
  }

  private def createKey[A](asKey: ToKey[A], value: A) = {
    asKey.toKey(value, TestKeyFactory(datastore))
  }

}
