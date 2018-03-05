package com.ovoenergy.datastore4s

import com.google.cloud.datastore.{FullEntity, Key}
import org.scalatest.Matchers
import org.scalatest.FlatSpec

class EntitySpec extends FlatSpec with Matchers {

  private val testKey = Key.newBuilder("test-project", "test-namespace", "test-id").build()
  private val fieldName = "FIELD_NAME"
  private val newFieldName = "NEW_FIELD_NAME"
  private val fieldValue = "test-value"

  "A wrapped datastore entity" should "return an empty result not an exception if a field is missing" in {
    val dsEntity = FullEntity.newBuilder(testKey).build()
    val entity = new WrappedEntity(dsEntity)
    entity.field(fieldName) shouldBe None
  }

  it should "wrap the value of a field that exists" in {
    val fieldValue = "test-value"
    val dsEntity = FullEntity.newBuilder(testKey).set(fieldName, fieldValue).build()
    val entity = new WrappedEntity(dsEntity)
    entity.field(fieldName) shouldBe Some(StringValue(fieldValue))
  }

  // NOTE: Cannot actually test on ProjectionEntities since you cannot create or mock them.
  "A projection datastore entity" should "return an empty result not an exception if a field is missing after mapping the field name" in {
    val dsEntity = FullEntity.newBuilder(testKey).build()
    val entity = new ProjectionEntity(Map(newFieldName -> fieldName), dsEntity)
    entity.field(fieldName) shouldBe None
  }

  it should "wrap the value of a field that exists after mapping the field name" in {
    val dsEntity = FullEntity.newBuilder(testKey).set(fieldName, fieldValue).build()
    val entity = new ProjectionEntity(Map(newFieldName -> fieldName), dsEntity)
    entity.field(newFieldName) shouldBe Some(StringValue(fieldValue))
  }

  "An entity builder" should "create a wrapped entity using the given fields" in {
    val entity = new WrappedBuilder(testKey).addField(Field(fieldName, StringValue(fieldValue))).build()
    entity.field(fieldName) shouldBe Some(StringValue(fieldValue))
  }

}
