package com.ovoenergy.datastore4s

import org.scalatest.{FlatSpec, Matchers}

class DatastoreConfigurationITSpec extends FlatSpec with Matchers {

  private val projectId = "test-project-id"

  it should "create a datastore service with options taken from the environment" in { // Set in build.sbt
    val options = DatastoreService(FromEnvironmentVariables).options
    options.getProjectId shouldBe "datastore4s"
    options.getNamespace shouldBe "datastore4s-namespace"
  }

}
