package com.ovoenergy.datastore4s

import java.io.File

import org.scalatest.{FlatSpec, Matchers}

class DatastoreConfigurationITSpec extends FlatSpec with Matchers {

  private val projectId = "test-project-id"

  "The datastore service" should "create a valid configuration for " in {
    val credsFile = new File("src/it/resources/service-account-example.json")
    val config =  DataStoreConfiguration(projectId, credsFile)
    config should be('Success)
  }

  it should "return a failure if the credentials file is not valid" in {
    DataStoreConfiguration(projectId, credentialsFile = null) should be('Failure)
    DataStoreConfiguration(projectId, credentialsFile = new File("file-that-does-not-exist.txt")) should be('Failure)
    DataStoreConfiguration(projectId, credentialsFile = new File("src/it/resources/not-a-valid-service-account-file.json")) should be('Failure)
  }

}
