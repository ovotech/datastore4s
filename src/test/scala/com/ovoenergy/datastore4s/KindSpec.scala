package com.ovoenergy.datastore4s

import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}

import scala.util.Try

class KindSpec extends FlatSpec with Matchers with GeneratorDrivenPropertyChecks {

  "The kind validation" should "reject any string starting with two underscores" in {
    forAll(Gen.alphaNumStr.map(s => "__" + s))(kind => Try(Kind(kind)) shouldBe 'failure)
  }

  it should "reject any string with a '/' in it" in {
    forAll(Gen.alphaNumStr.map(s => s + "/" + s))(kind => Try(Kind(kind)) shouldBe 'failure)
  }

  it should "Accept any other string" in {
    forAll(Gen.alphaNumStr)(kind => Kind(kind).name shouldBe kind)
  }

}
