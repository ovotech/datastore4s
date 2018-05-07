package com.ovoenergy.datastore4s

import com.google.cloud.NoCredentials
import org.scalatest.{FlatSpec, Matchers}

class DatastoreConfigurationSpec extends FlatSpec with Matchers {

  "The datastore service" should "create a datastore service with the manually passed options" in {
    val projectId = "test-project-id"
    val namespace = "test-namespace"
    val configuration = DatastoreConfiguration(projectId, namespace)
    val options = DatastoreService(configuration).options
    options.getProjectId shouldBe projectId
    options.getNamespace shouldBe namespace
  }

  it should "create a datastore service with the manually passed options with default namespace" in {
    val projectId = "test-project-id"
    val options = DatastoreService(DatastoreConfiguration(projectId)).options
    options.getProjectId shouldBe projectId
    options.getNamespace shouldBe ""
  }

  it should "create a datastore service with the manually passed options and emulator host" in {
    val projectId = "test-project-id"
    val namespace = "test-namespace"
    val host = "https://emulatorHost"
    val configuration = DatastoreConfiguration(projectId, host, Some(namespace))
    val options = DatastoreService(configuration).options
    options.getProjectId shouldBe projectId
    options.getNamespace shouldBe namespace
    options.getCredentials shouldBe NoCredentials.getInstance()
    options.getHost shouldBe host
  }

  it should "create a datastore service with the manually passed options with default namespace and emulator host" in {
    val projectId = "test-project-id"
    val host = "https://emulatorHost"
    val configuration = DatastoreConfiguration(projectId, host, None)
    val options = DatastoreService(configuration).options
    options.getProjectId shouldBe projectId
    options.getNamespace shouldBe ""
    options.getCredentials shouldBe NoCredentials.getInstance()
    options.getHost shouldBe host
  }

  it should "create a datastore service with options taken from the environment with an emulator" in { // Set in build.sbt
    val options = DatastoreService(FromEnvironmentVariables).options
    options.getProjectId shouldBe "datastore4s"
    options.getNamespace shouldBe "datastore4s-namespace"
    options.getHost shouldBe "https://localhost"
    options.getCredentials shouldBe NoCredentials.getInstance()
  }

}
