package com.ovoenergy.datastore4s

import com.google.cloud.datastore.Key
import com.ovoenergy.datastore4s.DatastoreOperationInterpreter.{run => runOp}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{FlatSpec, Matchers}

import scala.util.{Failure, Success}

class DatastoreServiceSpec extends FlatSpec with Matchers with MockitoSugar {

  private val mockDatastoreService = mock[DatastoreService]
  private val mockEntityFormat = mock[EntityFormat[String, String]]
  private val testKey = Key.newBuilder("test-project", "test-namespace", "test-id").build()

  implicit val format = mockEntityFormat
  implicit val service = mockDatastoreService

  "The datastore service" should "return an error from find if an exception is thrown by the datastore" in {
    val entityKey = "key"
    val kind = Kind("kind")
    when(mockDatastoreService.createKey(entityKey, kind)).thenReturn(testKey)
    when(mockEntityFormat.kind).thenReturn(kind)

    val error = new Exception("error")
    when(mockDatastoreService.find(testKey)).thenReturn(Failure(error))

    val result = runOp(DatastoreService.findOne[String, String](entityKey))
    result shouldBe Left(new DatastoreException(error))
  }

  it should "return a None if nothing is returned from a find" in {
    val entityKey = "key"
    val kind = Kind("kind")
    when(mockDatastoreService.createKey(entityKey, kind)).thenReturn(testKey)
    when(mockEntityFormat.kind).thenReturn(kind)

    when(mockDatastoreService.find(testKey)).thenReturn(Success(None))

    val result = runOp(DatastoreService.findOne[String, String](entityKey))
    result shouldBe Right(None)
  }

  it should "return a deserialised entity if one is returned from a find" in {
    val entityKey = "key"
    val kind = Kind("kind")
    when(mockDatastoreService.createKey(entityKey, kind)).thenReturn(testKey)
    when(mockEntityFormat.kind).thenReturn(kind)

    val entity = mock[Entity]
    when(mockDatastoreService.find(testKey)).thenReturn(Success(Some(entity)))

    val entityObject = "DeserialisedEntity"
    when(mockEntityFormat.fromEntity(entity)).thenReturn(Right(entityObject))

    val result = runOp(DatastoreService.findOne[String, String](entityKey))
    result shouldBe Right(Some(entityObject))
  }

  it should "return an error if an entity is returned but cannot be deserialised" in {
    val entityKey = "key"
    val kind = Kind("kind")
    when(mockDatastoreService.createKey(entityKey, kind)).thenReturn(testKey)
    when(mockEntityFormat.kind).thenReturn(kind)

    val entity = mock[Entity]
    when(mockDatastoreService.find(testKey)).thenReturn(Success(Some(entity)))

    val error = new DeserialisationError("failed")
    when(mockEntityFormat.fromEntity(entity)).thenReturn(Left(error))

    val result = runOp(DatastoreService.findOne[String, String](entityKey))
    result shouldBe Left(error)
  }

  it should "return and error if an exception is thrown trying to delete an entity" in {
    pending
  }

  it should "return a Right[Unit] if a delete call is successful" in {
    pending
  }

  it should "return a stream of the results of a query" in {
    pending
  }

  it should "return an error if an exception is thrown on an attempt to put an entity" in {
    pending
  }

  it should "return an error if a projectionEntity is passed to put" in {
    pending
  }

  it should "return a wrapped entity if a put is successful" in {
    pending
  }

  it should "return an error if an exception is thrown on an attempt to save an entity" in {
    pending
  }

  it should "return an error if a projectionEntity is passed to save" in {
    pending
  }

  it should "return a wrapped entity if a save is successful" in {
    pending
  }

}
