package com.datastore4s.core

import java.time.Instant

import org.scalatest.{FlatSpec, Matchers}

import scala.util.Success

class TrialThing extends FlatSpec with DefaultDatastoreSupport with Matchers {
  override val dataStoreConfiguration = DataStoreConfiguration("test-project", "test-namespace")

  implicit val wrapperFormat = fieldFormatFromFunctions(Wrapper.apply)(_.name)
  implicit val format = EntityFormat[SimpleTestObject, String]

  "This" should "work" in {
    val nowByMillis = Instant.ofEpochMilli(Instant.now().toEpochMilli)
    val testObject = SimpleTestObject(Wrapper("test-name"), nowByMillis, 10, 1283765L)
    val roundTripped = format.fromEntity(format.toEntity(testObject))
    roundTripped shouldBe Success(testObject)
  }
}


case class SimpleTestObject(wrapper: Wrapper, createdTime: Instant, age: Int, somethingElse: Long) extends DatastoreEntity[String] {
  override def key = wrapper.name
}

case class Wrapper(name: String)
