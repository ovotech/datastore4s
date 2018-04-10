package com.ovoenergy.datastore4s

import com.google.cloud.datastore.Key
import com.ovoenergy.datastore4s.DatastoreOperationInterpreter.{run => runOp}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import scala.util.{Failure, Success}

class DatastoreServiceSpec extends FlatSpec with Matchers with MockitoSugar with BeforeAndAfter {

  private val mockDatastoreService = mock[DatastoreService]
  private val mockEntityFormat = mock[EntityFormat[Object, String]]
  private val testKey = Key.newBuilder("test-project", "test-namespace", "test-id").build()

  implicit val format = mockEntityFormat
  implicit val service = mockDatastoreService

  private val entityKey = "key"
  private val kind = Kind("kind")

  private val mockEntity = mock[Entity]
  private val mockEntityObject = mock[Object]

  before {
    when(mockDatastoreService.createKey(entityKey, kind)).thenReturn(testKey)
    when(mockEntityFormat.kind).thenReturn(kind)
  }

  "The datastore service" should "return an error from find if an exception is thrown by the datastore" in {
    val error = new Exception("error")
    when(mockDatastoreService.find(testKey)).thenReturn(Failure(error))

    val result = runOp(DatastoreService.findOne[Object, String](entityKey))
    result shouldBe Left(new DatastoreException(error))
  }

  it should "return a None if nothing is returned from a find" in {
    when(mockDatastoreService.find(testKey)).thenReturn(Success(None))

    val result = runOp(DatastoreService.findOne[Object, String](entityKey))
    result shouldBe Right(None)
  }

  it should "return a deserialised entity if one is returned from a find" in {
    when(mockDatastoreService.find(testKey)).thenReturn(Success(Some(mockEntity)))

    when(mockEntityFormat.fromEntity(mockEntity)).thenReturn(Right(mockEntityObject))

    val result = runOp(DatastoreService.findOne[Object, String](entityKey))
    result shouldBe Right(Some(mockEntityObject))
  }

  it should "return an error if an entity is returned but cannot be deserialised" in {
    when(mockDatastoreService.find(testKey)).thenReturn(Success(Some(mockEntity)))

    val error = new DeserialisationError("failed")
    when(mockEntityFormat.fromEntity(mockEntity)).thenReturn(Left(error))

    val result = runOp(DatastoreService.findOne[Object, String](entityKey))
    result shouldBe Left(error)
  }

  it should "return and error if an exception is thrown trying to delete an entity" in {
    val error = new Exception("error")
    when(mockDatastoreService.delete(testKey)).thenReturn(Some(error))

    val result = runOp(DatastoreService.delete[Object, String](entityKey))
    result shouldBe Left(new DatastoreException(error))
  }

  it should "return the key if a delete call is successful" in {
    when(mockDatastoreService.delete(testKey)).thenReturn(None)

    val result = runOp(DatastoreService.delete[Object, String](entityKey))
    result shouldBe Right(entityKey)
  }

  it should "return an error if an exception is thrown on an attempt to put an entity" in {
    runOp(DatastoreService.put(mockEntityObject))
    pending
  }

  it should "return a wrapped entity if a put is successful" in {
    pending
  }

  it should "return an error if an exception is thrown on an attempt to save an entity" in {
    pending
  }

  it should "return a wrapped entity if a save is successful" in {
    pending
  }

  it should "return a stream of the results of a query" in {
    pending
  }

  it should "create a datastore service with the manually passed options" in {
    val configuration = DataStoreConfiguration("test-project-id", "test-namespace")
    val serviceConfiguration = DatastoreService(configuration).configuration
    serviceConfiguration shouldBe configuration
  }

  it should "create a datastore service with options taken from the environment" in {
    val serviceConfiguration = DatastoreService(FromEnvironmentVariables).configuration
    serviceConfiguration shouldBe DataStoreConfiguration("datastore4s", "datastore4s-namespace") // Set in build.sbt
  }

}
