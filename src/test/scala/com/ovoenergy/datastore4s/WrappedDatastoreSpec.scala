package com.ovoenergy.datastore4s

import com.google.cloud.datastore.{DatastoreOptions, FullEntity, Key}
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

  it should "not allow a projection entity to be 'put' or 'save'd" in {
    val testKey = Key.newBuilder("test-project", "test-namespace", "test-id").build()
    val entity = new ProjectionEntity(Map.empty, FullEntity.newBuilder(testKey).build())
    datastore.put(entity) should be('Failure)
    datastore.save(entity) should be('Failure)
  }
}
