package com.datastore4s.core

import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}

class DatastoreServiceSpec extends FlatSpec with Matchers with GeneratorDrivenPropertyChecks {

  val nonEmptyString = Gen.alphaNumStr.filter(!_.isEmpty)

  "The datastore service" should "be able to create a datastore from configuration" in {
    forAll(nonEmptyString, nonEmptyString) { (project, namespace) =>
      val datastore = DatastoreService.createDatastore(DataStoreConfiguration(project, namespace))
      datastore.getOptions.getProjectId shouldBe project
      datastore.getOptions.getNamespace shouldBe namespace
    }
  }

}
