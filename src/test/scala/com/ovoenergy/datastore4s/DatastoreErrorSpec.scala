package com.ovoenergy.datastore4s

import org.scalatest.{FlatSpec, Matchers}

class DatastoreErrorSpec extends FlatSpec with Matchers {

  "The sequence function" should "return a sequence in the case there are no errors" in {
    val ints: Seq[Either[DatastoreError, Int]] = Seq(Right(10), Right(-4837), Right(1))
    DatastoreError.sequence(ints) shouldBe Right(Seq(10, -4837, 1))
  }

  it should "return an error in the case there is only one error" in {
    val error = new DatastoreException(new RuntimeException("ERROR OCCURRED"))
    val ints = Seq(Right(10), Right(-4837), Right(1), Left(error))
    DatastoreError.sequence(ints) shouldBe Left(new ComposedError(Seq(error)))
  }

  it should "return a composite error when there is more than one error" in {
    val error1 = new DatastoreException(new RuntimeException("ERROR OCCURRED"))
    val error2 = new DatastoreException(new RuntimeException("ANOTHER ERROR OCCURRED"))
    val ints = Seq(Right(10), Left(error2), Right(-4837), Right(1), Left(error1))
    DatastoreError.sequence(ints) match {
      case Left(composed: ComposedError) =>
        composed.errors should contain(error1)
        composed.errors should contain(error2)
      case other => fail(s"Expected a composed error but got: $other")
    }
  }

}
