package com.ovoenergy.datastore4s

import com.ovoenergy.datastore4s.ToAncestor.{LongAncestor, StringAncestor}
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}

class KeyFactoryFacadeSpec extends FlatSpec with GeneratorDrivenPropertyChecks with Matchers {

  private val datastore = DatastoreService.createDatastore(DataStoreConfiguration("test-project", "test-namespace"))
  private val nonEmptyString = Gen.alphaNumStr.filter(!_.isEmpty)

  "The key factory facade" should "build a key with a kind and name" in {
    forAll(nonEmptyString, nonEmptyString) { (kind, name) =>
      val key = KeyFactoryFacade(datastore, Kind(kind)).buildWithName(name)
      key.getKind shouldBe kind
      key.getName shouldBe name
    }
  }

  it should "build a key with a kind and id" in {
    forAll(nonEmptyString, Gen.choose(Long.MinValue, Long.MaxValue)) { (kind, id) =>
      val key = KeyFactoryFacade(datastore, Kind(kind)).buildWithId(id)
      key.getKind shouldBe kind
      key.getId shouldBe id
    }
  }

  it should "be able to add arbitrary ancestors given an implicit ToAncestor[A]" in {
    case class SimpleWrapper(name: String)
    val kind = "simple-wrapped-kind"
    implicit val SimpleToAncestor = ToAncestor.toStringAncestor[SimpleWrapper](kind)(_.name)

    forAll(nonEmptyString) { name =>
      val key = KeyFactoryFacade(datastore, Kind("ancestor-test"))
        .addAncestor(SimpleWrapper(name))
        .buildWithName("test-name") 
      key.getAncestors should have size 1
      val ancestor = key.getAncestors.get(0)
      ancestor.getKind shouldBe kind
      ancestor.getName shouldBe name
    }
  }

  "The toLongAncestor function" should "be able to create a ToAncestor for any A given a function A => Long" in {
    val kindName = "bigDecimalKind"
    val toAncestor = ToAncestor.toLongAncestor[BigDecimal](kindName)(_.longValue())
    forAll(Gen.choose(Long.MinValue, Long.MaxValue)) { long =>
      val ancestor = toAncestor.toAncestor(BigDecimal(long))
      ancestor shouldBe new LongAncestor(Kind(kindName), long)
    }
  }

  "The toStringAncestor function" should "be able to create a ToAncestor for any A given a function A => String" in {
    val kindName = "stringPairKind"
    val toAncestor = ToAncestor.toStringAncestor[(String, String)](kindName)(pair => pair._1 + pair._2)
    forAll(Gen.alphaNumStr, Gen.alphaNumStr) { (string1, string2) =>
      val ancestor = toAncestor.toAncestor(string1, string2)
      ancestor shouldBe new StringAncestor(Kind(kindName), string1 + string2)
    }
  }

}
