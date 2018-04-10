package com.ovoenergy.datastore4s

import com.google.cloud.datastore.DatastoreOptions
import org.scalatest.{FlatSpec, Matchers}

class WrappedDatastoreSpec extends FlatSpec with Matchers {

  val namespace = "test-namespace"
  val projectId = "test-project-id"

  private val datastore = new WrappedDatastore(DatastoreOptions.newBuilder()
    .setNamespace(namespace)
    .setProjectId(projectId)
    .build().getService)

  "The wrapped datastore" should "create a key with the expected values" in {
    val name = "TestName"
    val kind = "TestKind"
    val key = datastore.createKey(name, Kind(kind)) // Using implicit ToKey[String]
    key.getName shouldBe name
    key.hasId shouldBe false
    key.getKind shouldBe kind
    key.getNamespace shouldBe namespace
    key.getProjectId shouldBe projectId
    key.getAncestors should be('empty)
  }
}
