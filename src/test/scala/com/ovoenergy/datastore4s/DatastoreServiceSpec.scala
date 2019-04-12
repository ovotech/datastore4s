package com.ovoenergy.datastore4s

import com.google.cloud.datastore.{Key, Transaction, Entity => DsEntity, ProjectionEntity => DsProjectionEntity}
import com.ovoenergy.datastore4s.DatastoreError.SuppressedStackTrace
import com.ovoenergy.datastore4s.DatastoreOperationInterpreter.{run => runOp}
import org.mockito.ArgumentMatchers.{eq => mockEq}
import org.mockito.Mockito._
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{BeforeAndAfter, FlatSpec, Matchers}

import scala.util.{Failure, Success}

class DatastoreServiceSpec extends FlatSpec with Matchers with MockitoSugar with BeforeAndAfter {

  private val testKey = Key.newBuilder("test-project", "test-namespace", "test-id").build()
  private val expectedBuilder = new WrappedBuilder(testKey)

  private implicit val mockEntityFormat = mock[EntityFormat[Object, String]]("mockEntityFormat")
  private implicit val mockDatastoreService = mock[DatastoreService]("mockDatastoreService")
  private val mockTransacton = mock[Transaction]("mockTransacton")
  private val mockTransactonalService = mock[DatastoreService]("mockTransactonalService")

  private val entityKey = "key"
  private val kind = Kind("kind")

  private val mockEntity = mock[Entity]("mockEntity")
  private val mockEntityObject = mock[Object]("mockEntityObject")

  before {
    when(mockDatastoreService.createKey(entityKey, kind)).thenReturn(testKey)
    when(mockEntityFormat.toEntityComponents(mockEq(mockEntityObject))).thenReturn(
      new EntityComponents(kind, entityKey, b => if(b == expectedBuilder) mockEntity else null)
    )
    when(mockEntityFormat.kind).thenReturn(kind)
    when(mockDatastoreService.newTransaction()).thenReturn((mockTransacton, mockTransactonalService))

    val entityComponents = new EntityComponents[String](kind, entityKey, (b) => {
      if(b == expectedBuilder) mockEntity else null
    })
    when(mockEntityFormat.toEntityComponents(mockEq(mockEntityObject))).thenReturn(entityComponents)
  }

  it should "return an error from find if an exception is thrown by the datastore" in {
    val error = new Exception("error")
    when(mockDatastoreService.find(testKey)).thenReturn(Failure(error))

    val result = runOp(DatastoreService.findOne[Object, String](entityKey))
    result shouldBe Left(DatastoreException(error))
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

    val error =  DeserialisationError("failed")
    when(mockEntityFormat.fromEntity(mockEntity)).thenReturn(Left(error))

    val result = runOp(DatastoreService.findOne[Object, String](entityKey))
    result shouldBe Left(error)
  }

  it should "return and error if an exception is thrown trying to delete an entity by key" in {
    val error = new Exception("error")
    when(mockDatastoreService.delete(testKey)).thenReturn(Some(error))

    val result = runOp(DatastoreService.delete[Object, String](entityKey))
    result shouldBe Left( DatastoreException(error))
  }

  it should "return the key if a delete by key call is successful" in {
    when(mockDatastoreService.delete(testKey)).thenReturn(None)

    val result = runOp(DatastoreService.delete[Object, String](entityKey))
    result shouldBe Right(entityKey)
  }

  it should "return and error if an exception is thrown trying to deleteAll entities in a key list" in {
    val error = new Exception("error")
    when(mockDatastoreService.deleteAll(Seq(testKey))).thenReturn(Some(error))

    val result = runOp(DatastoreService.deleteAll[Object, String](Seq(entityKey)))
    result shouldBe Left(DatastoreException(error))
  }

  it should "return the key if a deleteAll by key call is successful" in {
    when(mockDatastoreService.deleteAll(Seq(testKey))).thenReturn(None)

    val result = runOp(DatastoreService.deleteAll[Object, String](Seq(entityKey)))
    result shouldBe Right(Seq(entityKey))
  }

  it should "return and error if an exception is thrown trying to delete an entity" in {
    val error = new Exception("error")
    when(mockDatastoreService.delete(testKey)).thenReturn(Some(error))

    val result = runOp(DatastoreService.deleteEntity[Object, String](mockEntityObject))
    result shouldBe Left( DatastoreException(error))
  }

  it should "return the key if a delete call is successful" in {
    when(mockDatastoreService.delete(testKey)).thenReturn(None)

    val result = runOp(DatastoreService.deleteEntity[Object, String](mockEntityObject))
    result shouldBe Right(entityKey)
  }

  it should "return and error if an exception is thrown trying to deleteAll entities in an entity list" in {
    val error = new Exception("error")
    when(mockDatastoreService.deleteAll(Seq(testKey))).thenReturn(Some(error))

    val result = runOp(DatastoreService.deleteAllEntities[Object, String](Seq(mockEntityObject)))
    result shouldBe Left(DatastoreException(error))
  }

  it should "return the key if a deleteAll call is successful" in {
    when(mockDatastoreService.deleteAll(Seq(testKey))).thenReturn(None)

    val result = runOp(DatastoreService.deleteAllEntities[Object, String](Seq(mockEntityObject)))
    result shouldBe Right(Seq(entityKey))
  }

  it should "return an error if an exception is thrown on an attempt to put an entity" in {
    val error = new Exception("error")

    when(mockDatastoreService.put(mockEntity)).thenReturn(Failure(error))

    val result = runOp(DatastoreService.put(mockEntityObject))
    result shouldBe Left(DatastoreException(error))
  }

  it should "return a wrapped entity if a put is successful" in {
    when(mockDatastoreService.put(mockEntity)).thenReturn(Success(mockEntity))

    val result = runOp(DatastoreService.put(mockEntityObject))
    result shouldBe Right(Persisted(mockEntityObject, mockEntity))
  }

  it should "return an error if an exception is thrown on an attempt to save an entity" in {
    val error = new Exception("error")

    when(mockDatastoreService.save(mockEntity)).thenReturn(Failure(error))

    val result = runOp(DatastoreService.save(mockEntityObject))
    result shouldBe Left(DatastoreException(error))
  }

  it should "return a wrapped entity if a save is successful" in {
    when(mockDatastoreService.save(mockEntity)).thenReturn(Success(mockEntity))

    val result = runOp(DatastoreService.save(mockEntityObject))
    result shouldBe Right(Persisted(mockEntityObject, mockEntity))
  }

  it should "return an error if an exception is thrown on an attempt to putAll entities" in {
    val error = new Exception("error")

    when(mockDatastoreService.putAll(Seq(mockEntity))).thenReturn(Failure(error))

    val result = runOp(DatastoreService.putAll(Seq(mockEntityObject)))
    result shouldBe Left(DatastoreException(error))
  }

  it should "return a wrapped entity if a putAll is successful" in {
    when(mockDatastoreService.putAll(Seq(mockEntity))).thenReturn(Success(Seq(mockEntity)))

    val result = runOp(DatastoreService.putAll(Seq(mockEntityObject)))
    result shouldBe Right(Seq(Persisted(mockEntityObject, mockEntity)))
  }

  it should "return an error if an exception is thrown on an attempt to saveAll entities" in {
    val error = new Exception("error")

    when(mockDatastoreService.saveAll(Seq(mockEntity))).thenReturn(Failure(error))

    val result = runOp(DatastoreService.saveAll(Seq(mockEntityObject)))
    result shouldBe Left(DatastoreException(error))
  }

  it should "return a wrapped entity if a saveAll is successful" in {
    when(mockDatastoreService.saveAll(Seq(mockEntity))).thenReturn(Success(Seq(mockEntity)))

    val result = runOp(DatastoreService.saveAll(Seq(mockEntityObject)))
    result shouldBe Right(Seq(Persisted(mockEntityObject, mockEntity)))
  }

  it should "create a list query that will use the correct kind" in {
    val query = DatastoreService.list[Object]
    val castQuery = query.asInstanceOf[DatastoreQuery[Object, DsEntity]]

    val datastoreQuery = castQuery.queryBuilderSupplier().build()
    datastoreQuery.getKind shouldBe kind.name

    val datastoreEntity = DsEntity.newBuilder(testKey).build()
    castQuery.entityFunction(datastoreEntity) match {
      case wrapped: WrappedEntity =>
        wrapped.entity shouldBe datastoreEntity
      case other => fail(s"Expected a wrapped entity but got $other")
    }

    castQuery.filters should be('empty)
  }

  it should "create a projection query that will use the correct kind and mappings" in {
    val query = DatastoreService.projectInto[Object, Object]("entityProperty" -> "projectionProperty")
    val castQuery = query.asInstanceOf[DatastoreQuery[Object, DsProjectionEntity]]

    val datastoreQuery = castQuery.queryBuilderSupplier().build()
    datastoreQuery.getKind shouldBe kind.name
    datastoreQuery.getProjection should have size 1
    datastoreQuery.getProjection.get(0) shouldBe "entityProperty"

    castQuery.entityFunction(null) match { // TODO cannot actually test against a projection entity
      case projection: ProjectionEntity =>
        projection.mappings shouldBe Map("projectionProperty" -> "entityProperty") // NOTE: Reverses the order so that .get() performs lookup by entity property
      case other => fail(s"Expected a projection entity but got $other")
    }

    castQuery.filters should be('empty)
  }

    private val mockOperationObject = mock[Object]("mockOperationObject")

  it should "wrap an operation in a transaction that gets committed" in {
    val operation = DatastoreOperation{ serivce =>
      if(serivce == mockTransactonalService)
        Right(mockOperationObject)
      else
        DatastoreError.deserialisationError(s"Wrong service was passed: $serivce was not $mockTransactonalService")
    }
    when(mockTransacton.commit()).thenReturn(mock[Transaction.Response]("mockResponse"))
    val transactionalOperation = DatastoreService.transactionally(operation)
    transactionalOperation.op(mockDatastoreService) shouldBe Right(mockOperationObject)
  }

  it should "return an error if the transaction cannot be committed" in {
    val operation = DatastoreOperation{ serivce =>
      if(serivce == mockTransactonalService)
        Right(mockOperationObject)
      else
        DatastoreError.deserialisationError(s"Wrong service was passed: $serivce was not $mockTransactonalService")
    }
    val error = new RuntimeException("Failed Commit")
    when(mockTransacton.commit()).thenThrow(error)
    val transactionalOperation = DatastoreService.transactionally(operation)
    transactionalOperation.op(mockDatastoreService) shouldBe DatastoreError.exception[Object](SuppressedStackTrace("Could not commit transaction", error))
  }

  it should "rollback the transaction if there is a failure" in {
    val error = DatastoreError.deserialisationError[Object]("Failed operation")
    val operation = DatastoreOperation{ serivce =>
      if(serivce == mockTransactonalService)
        error
      else
        sys.error(s"Wrong service was passed: $serivce was not $mockTransactonalService")
    }
    doNothing().when(mockTransacton).rollback()
    val transactionalOperation = DatastoreService.transactionally(operation)
    transactionalOperation.op(mockDatastoreService) should be('Left)
  }

  it should "return an error if the transaction cannot be rolled back" in {
    val error = DatastoreError.deserialisationError[Object]("Failed operation")
    val operation = DatastoreOperation{ serivce =>
      if(serivce == mockTransactonalService)
        error
      else
        sys.error(s"Wrong service was passed: $serivce was not $mockTransactonalService")
    }
    doThrow(new RuntimeException("Failed Rollback")).when(mockTransacton).rollback()

    val transactionalOperation = DatastoreService.transactionally(operation)
    transactionalOperation.op(mockDatastoreService) should be('Left)
  }

}
