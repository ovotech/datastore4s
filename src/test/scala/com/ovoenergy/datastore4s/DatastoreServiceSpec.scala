package com.ovoenergy.datastore4s

import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{FlatSpec, Matchers}

class DatastoreServiceSpec extends FlatSpec with Matchers with GeneratorDrivenPropertyChecks {

  val nonEmptyString = Gen.alphaNumStr.filter(!_.isEmpty).filter(_.length <= 100)

  "The datastore service" should "be able to create a datastore from configuration" in {
    forAll(nonEmptyString, nonEmptyString) { (project, namespace) =>
      val datastore = DatastoreService.createDatastore(DataStoreConfiguration(project, namespace))
      datastore.getOptions.getProjectId shouldBe project
      datastore.getOptions.getNamespace shouldBe namespace
    }
  }

}
