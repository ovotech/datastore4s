package com.ovoenergy.datastore4s

import com.ovoenergy.datastore4s.ToKey._
import com.google.cloud.datastore.Key
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}

class ToKeySpec extends FlatSpec with GeneratorDrivenPropertyChecks with Matchers {

  "The String AsKey" should "take any string and create a key using the key name" in {
    forAll(Gen.alphaNumStr.filter(!_.isEmpty)) { value =>
      createKey(StringToKey, value).getName shouldBe value
    }
  }

  "The Long AsKey" should "take any long and create a key using the key id" in {
    forAll(Gen.choose(Long.MinValue, Long.MaxValue)) { value =>
      createKey(LongToKey, Long.box(value)).getId shouldBe value
    }
  }

  private val datastoreService = DatastoreService.createDatastore(DataStoreConfiguration("test-project", "test-namespace"))

  def testKey[A](asKey: ToKey[A])(value: A)(assertion: Key => Unit) = {
    assertion(createKey(asKey, value))
  }

  private def createKey[A](asKey: ToKey[A], value: A) = datastoreService.createKey(value, Kind("test-kind"))(asKey)

}
