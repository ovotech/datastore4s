package com.ovoenergy.datastore4s

import com.google.cloud.datastore.{DatastoreOptions, FullEntity, Key => DsKey}
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

  it should "create a key with an id" in {
    val id = Long.box(10)
    val kind = "TestKind"
    val key = datastore.createKey(id, Kind(kind)) // Using implicit ToKey[java.lang.Long]
    key.getId shouldBe id
    key.hasName shouldBe false
    key.getKind shouldBe kind
    key.getNamespace shouldBe namespace
    key.getProjectId shouldBe projectId
    key.getAncestors should be('empty)
  }

  private val ancestorKind = "SomeAncestor"
  case class CustomAncestor(id: Long)
  implicit val customAncestorToAncestor = ToAncestor.toLongAncestor[CustomAncestor](ancestorKind)(_.id)
  case class CustomType(name: String, ancestor: CustomAncestor)
  implicit object CustomTypeKey extends ToNamedKey[CustomType] { override def toKey(value: CustomType): NamedKey = NamedKey(value.name, value.ancestor) }
  it should "create a key with an ancestor" in {
    val name = "TestName"
    val ancestorId = 1234567890
    val customKey = CustomType(name, CustomAncestor(ancestorId))
    val kind = "TestKind"
    val key = datastore.createKey(customKey, Kind(kind)) // Using implicit ToKey[String]
    key.getName shouldBe name
    key.hasId shouldBe false
    key.getKind shouldBe kind
    key.getNamespace shouldBe namespace
    key.getProjectId shouldBe projectId
    key.getAncestors should have size(1)
    val ancestor = key.getAncestors.get(0)
    ancestor.getKind shouldBe ancestorKind
    ancestor.getId shouldBe ancestorId
    ancestor.hasName shouldBe false
  }

  it should "not allow a projection entity to be 'put' or 'save'd" in {
    val testKey = DsKey.newBuilder("test-project", "test-namespace", "test-id").build()
    val entity = new ProjectionEntity(Map.empty, FullEntity.newBuilder(testKey).build())
    datastore.put(entity) should be('Failure)
    datastore.save(entity) should be('Failure)
  }
}
