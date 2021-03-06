package com.ovoenergy.datastore4s

import java.util.concurrent.ThreadLocalRandom

import com.google.cloud.datastore.Key
import org.scalatest.concurrent.Eventually
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FeatureSpec, Inside, Matchers}

case class SubEntityType(id: Long, aBoolean: Boolean)

case class SomeEntityType(id: String, parent: EntityParent, possibleInt: Option[Int], compositeField: CompositeField, subEntityField: Option[SubEntityType] = None)

case class CompositeField(doubles: Seq[Double], someBoolean: Boolean) {
  override def equals(obj: scala.Any): Boolean = obj match {
    case CompositeField(thatDoubles, thatBoolean) =>
      thatBoolean == someBoolean &&
        thatDoubles.size == doubles.size &&
        thatDoubles.forall(doubles.contains(_))
    case _ => false
  }
}

case class EntityParent(id: Long)

case class ComplexKey(id: String, parent: EntityParent)

case class ProjectedRow(entityId: String, boolean: Boolean, parentAsLong: Long)

trait TestDatastoreRepository extends DatastoreRepository {
  override def datastoreConfiguration = FromEnvironmentVariables

  implicit val parentToAncestor = toLongAncestor[EntityParent]("parent")(_.id)
  implicit val parentFormat = formatFrom(EntityParent.apply)(_.id)

  implicit val subEntityFormat = EntityFormat[SubEntityType, java.lang.Long]("sub-entity-kind")(_.id)

  implicit val compositeFieldFormat = FieldFormat[CompositeField]
  implicit val entityFormat = EntityFormat[SomeEntityType, ComplexKey]("entity-kind")(entity => ComplexKey(entity.id, entity.parent))
  implicit val projectedFromEntity = FromEntity[ProjectedRow]

  implicit object ComplexKeyToKey extends ToKey[ComplexKey] {
    override def toKey(value: ComplexKey, keyFactory: KeyFactory): Key = {
      keyFactory.addAncestor(value.parent).buildWithName(value.id)
    }
  }

}

class DatastoreServiceITSpec extends FeatureSpec with Matchers with Inside with Eventually with TestDatastoreRepository {

  override implicit val patienceConfig =
    PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(100, Millis)))

  feature("Datastore support for persistence") {
    scenario("Put single entity") {
      val entity = randomEntityWithId("PutEntity")
      val result = run(put(entity))
      result match {
        case Right(persisted) => persisted.inputObject shouldBe entity
        case Left(error) => fail(s"There was an error: $error")
      }
    }
    scenario("Put single entity with entity subtype") {
      val subEntityId = 123456789L
      val subEntity = SubEntityType(subEntityId, true)
      val entity = randomEntityWithId("PutEntityWithSubType").copy(subEntityField = Some(subEntity))
      val result = run {
        for {
          _ <- put(entity)
          found <- findOne[SomeEntityType, ComplexKey](ComplexKey(entity.id, entity.parent))
        } yield found
      }
      result match {
        case Right(found) => found shouldBe Some(entity)
        case Left(error) => fail(s"There was an error: $error")
      }

    }
    scenario("Put entity with the same key as an entity in the database") {
      val key = ComplexKey("Key that already exists", EntityParent(230))
      val entity = randomEntityWithKey(key)
      val replacementEntity = randomEntityWithKey(key)
      val result = run(for {
        _ <- put(entity)
        _ <- put(replacementEntity)
        retrieved <- findOne[SomeEntityType, ComplexKey](key)
      } yield retrieved)
      result match {
        case Right(persisted) => persisted shouldBe Some(replacementEntity)
        case Left(error) => fail(s"There was an error: $error")
      }
    }
    scenario("Save single entity") {
      val entity = randomEntityWithId("SaveEntity")
      val result = run(save(entity))
      result match {
        case Right(persisted) => persisted.inputObject shouldBe entity
        case Left(error) => fail(s"There was an error: $error")
      }
    }
    scenario("Save entity that for a key that already exists") {
      val key = ComplexKey("Key for saving with error", EntityParent(240))
      val entity = randomEntityWithKey(key)
      val failingEntity = randomEntityWithKey(key)
      val result = run(for {
        _ <- save(entity)
        _ <- save(failingEntity)
      } yield ())
      result should be('Left)
    }
  }

  feature("Datastore support for finding single entities") {
    scenario("Entity with key does not exist") {
      val result = run(findOne[SomeEntityType, ComplexKey](ComplexKey("Non Existent Entity", EntityParent(10))))
      result shouldBe Right(None)
    }
    scenario("Entity with a key that exists") {
      val entity = randomEntityWithId("Entity That Exists")
      val result = run(for {
        _ <- put(entity)
        retrieved <- findOne[SomeEntityType, ComplexKey](ComplexKey(entity.id, entity.parent))
      } yield retrieved)
      result shouldBe Right(Some(entity))
    }
  }

  feature("Datastore support for deleting entities") {
    scenario("Entity with key does not exist by key") {
      val key = ComplexKey("Non Existant Entity By Key", EntityParent(10))
      val result = run(delete[SomeEntityType, ComplexKey](key))
      result shouldBe Right(key)
    }
    scenario("Entity with a key that exists by key") {
      val entity = randomEntityWithId("Entity That Exists By Key")
      val key = ComplexKey(entity.id, entity.parent)
      val result = run(for {
        _ <- put(entity)
        before <- findOne[SomeEntityType, ComplexKey](key)
        deleted <- delete[SomeEntityType, ComplexKey](key)
        after <- findOne[SomeEntityType, ComplexKey](key)
      } yield (before, deleted, after))
      result shouldBe Right((Some(entity), key, None))
    }
    scenario("Entity with key does not exist") {
      val entity = randomEntityWithId("Non Existant Entity")
      val result = run(deleteEntity[SomeEntityType, ComplexKey](entity))
      result shouldBe Right(ComplexKey(entity.id, entity.parent))
    }
    scenario("Entity with a key that exists") {
      val entity = randomEntityWithId("Entity That Exists")
      val key = ComplexKey(entity.id, entity.parent)
      val result = run(for {
        _ <- put(entity)
        before <- findOne[SomeEntityType, ComplexKey](key)
        deleted <- deleteEntity[SomeEntityType, ComplexKey](entity)
        after <- findOne[SomeEntityType, ComplexKey](key)
      } yield (before, deleted, after))
      result shouldBe Right((Some(entity), key, None))
    }
    scenario("Safe Delete an entity that does not exist by key") {
      val key = ComplexKey("Non Existant Safe Entity By Key", EntityParent(10))
      val result = run(safeDelete[SomeEntityType, ComplexKey](key))
      result should be('Left)
    }
    scenario("Safe Delete an entity that does exist by key") {
      val entity = randomEntityWithId("Safe Entity That Exists By Key")
      val key = ComplexKey(entity.id, entity.parent)
      val result = run(for {
        _ <- put(entity)
        before <- findOne[SomeEntityType, ComplexKey](key)
        deleted <- safeDelete[SomeEntityType, ComplexKey](key)
        after <- findOne[SomeEntityType, ComplexKey](key)
      } yield (before, deleted, after))
      result shouldBe Right((Some(entity), key, None))
    }
    scenario("Safe Delete an entity that does not exist") {
      val entity = randomEntityWithId("Non Existant Safe Entity")
      val result = run(safeDeleteEntity[SomeEntityType, ComplexKey](entity))
      result should be('Left)
    }
    scenario("Safe Delete an entity that does exist") {
      val entity = randomEntityWithId("Safe Entity That Exists")
      val key = ComplexKey(entity.id, entity.parent)
      val result = run(for {
        _ <- put(entity)
        before <- findOne[SomeEntityType, ComplexKey](key)
        deleted <- safeDeleteEntity[SomeEntityType, ComplexKey](entity)
        after <- findOne[SomeEntityType, ComplexKey](key)
      } yield (before, deleted, after))
      result shouldBe Right((Some(entity), key, None))
    }
  }

  feature("Datastore support for listing entities of a type") {
    scenario("Sequence a type of entity") {
      val (entity1, entity2) = (randomEntityWithId("Entity1"), randomEntityWithId("Entity2"))
      run(put(entity1))
      run(put(entity2))
      // NOTE: Eventually consistent query
      eventually {
        run(list[SomeEntityType].sequenced()) match {
          case Right(seq) =>
            seq should contain(entity1)
            seq should contain(entity2)
          case Left(error) => fail(s"There was an error: $error")
        }
      }
    }
    scenario("Stream a type of entity") {
      val (entity1, entity2) = (randomEntityWithId("StreamEntity1"), randomEntityWithId("StreamEntity2"))
      run(put(entity1))
      run(put(entity2))
      // NOTE: Eventually consistent query
      eventually {
        run(list[SomeEntityType].stream()) match {
          case Right(stream) =>
            stream should contain(Right(entity1))
            stream should contain(Right(entity2))
          case Left(error) => fail(s"There was an error: $error")
        }
      }
    }
    scenario("Sequence all entities with a certain ancestor") {
      val ancestor = EntityParent(10000)
      val entity1 = randomEntityWithKey(ComplexKey("AncestorEntity1", ancestor))
      val entity2 = randomEntityWithKey(ComplexKey("AncestorEntity2", ancestor))
      val entity3 = randomEntityWithKey(ComplexKey("EntityWithDifferenceAncestor", EntityParent(20000)))
      val result = run(for {
        _ <- put(entity1)
        _ <- put(entity2)
        _ <- put(entity3)
        stream <- list[SomeEntityType].withAncestor(ancestor).stream()
      } yield stream)
      result match {
        case Right(stream) =>
          stream should contain(Right(entity1))
          stream should contain(Right(entity2))
          stream should not contain entity3
        case Left(error) => fail(s"There was an error: $error")
      }
    }
    scenario("Sequence all entities with a certain property value") {
      val expectedPossibleInt = Option(-20)
      val (entity1, entity2, entity3) = (randomEntityWithId("Entity1"), randomEntityWithId("Entity2"), randomEntityWithId("Entity3"))
      val expectedEntity1 = entity1.copy(possibleInt = expectedPossibleInt)
      val expectedEntity2 = entity2.copy(possibleInt = expectedPossibleInt)
      val unexpectedEntity = entity3.copy(possibleInt = None)
      run(put(expectedEntity1))
      run(put(expectedEntity2))
      run(put(unexpectedEntity))
      // NOTE: Eventually consistent query
      eventually {
        run(list[SomeEntityType].withPropertyEq("possibleInt", expectedPossibleInt).sequenced()) match {
          case Right(seq) =>
            seq should contain(expectedEntity1)
            seq should contain(expectedEntity2)
            seq should not contain unexpectedEntity
          case Left(error) => fail(s"There was an error: $error")
        }
      }
    }
    scenario("Sequence all entities with multiple properties") {
      val ancestor = EntityParent(40000)
      val (entity1, entity2, entity3, entity4) = (randomEntityWithKey(ComplexKey("MultiFilterEntity1", ancestor)).copy(possibleInt = Option(20)),
        randomEntityWithKey(ComplexKey("MultiFilterEntity2", ancestor)).copy(possibleInt = Option(100)),
        randomEntityWithKey(ComplexKey("MultiFilterEntity3", ancestor)).copy(possibleInt = None),
        randomEntityWithKey(ComplexKey("MultiFilterEntity3", EntityParent(1))).copy(possibleInt = Option(30)))
      val result = run(for {
        _ <- put(entity1)
        _ <- put(entity2)
        _ <- put(entity3)
        _ <- put(entity4)
        sequence <- list[SomeEntityType]
          .withAncestor(ancestor)
          .withPropertyGreaterThanEq("possibleInt", Option(20))
          .withPropertyLessThan("possibleInt", Option(100))
          .sequenced()
      } yield sequence)
      result match {
        case Right(seq) => seq shouldBe Seq(entity1)
        case Left(error) => fail(s"There was an error: $error")
      }
    }
    scenario("Limit the number of results") {
      val ancestor = EntityParent(12345)
      val entity1 = randomEntityWithKey(ComplexKey("LimitedEntity1", ancestor))
      val entity2 = randomEntityWithKey(ComplexKey("LimitedEntity2", ancestor))
      val result = run(for {
        _ <- putAll(Seq(entity1, entity2))
        results <- list[SomeEntityType].withAncestor(ancestor).limit(1).sequenced()
      } yield results)
      result match {
        case Right(sequence) =>
          sequence should have size 1
          sequence.head should (be(entity1) or be(entity2))
        case Left(error) => fail(s"There was an error: $error")
      }
    }
    scenario("Offset the results") {
      val ancestor = EntityParent(12346)
      val entity1 = randomEntityWithKey(ComplexKey("OffsetEntity1", ancestor))
      val entity2 = randomEntityWithKey(ComplexKey("OffsetEntity2", ancestor))
      val result = run(for {
        _ <- putAll(Seq(entity1, entity2))
        results <- list[SomeEntityType].withAncestor(ancestor).offset(1).sequenced()
      } yield results)
      result match {
        case Right(sequence) =>
          sequence should have size 1
          sequence.head should (be(entity1) or be(entity2))
        case Left(error) => fail(s"There was an error: $error")
      }
    }
    scenario("Order the results by a property in ascending order") {
      val ancestor = EntityParent(54321)
      val smallest = randomEntityWithKey(ComplexKey("OrderByAscEntity1", ancestor)).copy(possibleInt = None)
      val medium = randomEntityWithKey(ComplexKey("OrderByAscEntity2", ancestor)).copy(possibleInt = Some(50))
      val largest = randomEntityWithKey(ComplexKey("OrderByAscEntity3", ancestor)).copy(possibleInt = Some(100))
      val result = run(for {
        _ <- putAll(Seq(smallest, medium, largest))
        results <- list[SomeEntityType].withAncestor(ancestor).orderByAscending("possibleInt").sequenced()
      } yield results)
      result match {
        case Right(sequence) => sequence should contain theSameElementsInOrderAs Seq(smallest, medium, largest)
        case Left(error) => fail(s"There was an error: $error")
      }
    }
    scenario("Order the results by a property in descending order") {
      val ancestor = EntityParent(654321)
      val smallest = randomEntityWithKey(ComplexKey("OrderByDescEntity1", ancestor)).copy(possibleInt = None)
      val medium = randomEntityWithKey(ComplexKey("OrderByDescEntity2", ancestor)).copy(possibleInt = Some(50))
      val largest = randomEntityWithKey(ComplexKey("OrderByDescEntity3", ancestor)).copy(possibleInt = Some(100))
      val result = run(for {
        _ <- putAll(Seq(smallest, medium, largest))
        results <- list[SomeEntityType].withAncestor(ancestor).orderByDescending("possibleInt").sequenced()
      } yield results)
      result match {
        case Right(sequence) => sequence should contain theSameElementsInOrderAs Seq(largest, medium, smallest)
        case Left(error) => fail(s"There was an error: $error")
      }
    }
  }

  feature("Datastore support for projections") {
    scenario("Project a sequence of entities into a row format") {
      val entity = randomEntityWithId("ProjectedEntity")
      val expectedProjection = ProjectedRow(entity.id, entity.compositeField.someBoolean, entity.parent.id)
      run(put(entity))
      // NOTE: Eventually consistent query
      eventually {
        // TODO Note here that the type of 'parent' is different, but the internal datastore type is still LongValue. I don't know if we want to allow this.
        val projection = projectInto[SomeEntityType, ProjectedRow]("id" -> "entityId",
          "compositeField.someBoolean" -> "boolean",
          "parent" -> "parentAsLong")
          .sequenced()
        run(projection) match {
          case Right(seq) => seq should contain(expectedProjection)
          case Left(error) => fail(s"There was an error: $error")
        }
      }
    }
  }

  feature("Datastore support for batch operations") {
    scenario("PutAll entities") {
      val entities = Seq(randomEntityWithId("PutAllEntity1"), randomEntityWithId("PutAllEntity2"), randomEntityWithId("PutAllEntity3"))
      run(putAll(entities))

      eventually {
        run(list[SomeEntityType].sequenced()) match {
          case Right(seq) => seq should contain allElementsOf entities
          case Left(error) => fail(s"There was an error: $error")
        }
      }
    }
    scenario("PutAll entities where some entities with the same key already exist") {
      val key = ComplexKey("Key that already exists for putAll", EntityParent(400))
      val replaced = randomEntityWithKey(key)
      val entities = Seq(randomEntityWithId("PutAllReplaceEntity1"), randomEntityWithKey(key), randomEntityWithId("PutAllReplaceEntity2"))
      run(for {
        _ <- put(replaced)
        _ <- putAll(entities)
      } yield ())

      eventually {
        run(list[SomeEntityType].sequenced()) match {
          case Right(seq) =>
            seq should contain allElementsOf entities
            seq should not contain replaced
          case Left(error) => fail(s"There was an error: $error")
        }
      }
    }

    scenario("SaveAll entities") {
      val entities = Seq(randomEntityWithId("SaveAllEntity1"), randomEntityWithId("SaveAllEntity2"), randomEntityWithId("SaveAllEntity3"))
      run(saveAll(entities))

      eventually {
        run(list[SomeEntityType].sequenced()) match {
          case Right(seq) => seq should contain allElementsOf entities
          case Left(error) => fail(s"There was an error: $error")
        }
      }
    }
    scenario("SaveAll entities where some entities with the same key already exist") {
      val key = ComplexKey("Key that already exists for saveAll", EntityParent(310))
      val existing = randomEntityWithKey(key)
      val (entity1, failedEntity, entity3) = (randomEntityWithId("SaveAllFailEntity1"), randomEntityWithKey(key), randomEntityWithId("SaveAllFailEntity2"))
      val result = run(for {
        _ <- put(existing)
        _ <- saveAll(Seq(entity1, failedEntity, entity3))
      } yield ())
      result should be('Left)
      eventually {
        run(list[SomeEntityType].sequenced()) match {
          case Right(seq) =>
            seq should contain(existing)
            seq should not contain failedEntity
          case Left(error) => fail(s"There was an error: $error")
        }
      }
    }

    scenario("DeleteAll entities by key") {
      val parent = EntityParent(500) // Use combined parent so that we can have strong consistency in the queries
      val entities = Seq(randomEntityWithKey(ComplexKey("DeleteAllByKeyEntity1", parent)), randomEntityWithKey(ComplexKey("DeleteAllByKeyEntity2", parent)), randomEntityWithKey(ComplexKey("DeleteAllByKeyEntity3", parent)))
      val keys = entities.map(e => ComplexKey(e.id, e.parent))
      val result = run(for {
        _ <- putAll(entities)
        before <- list[SomeEntityType].withAncestor(parent).sequenced()
        deleted <- deleteAll[SomeEntityType, ComplexKey](keys)
        after <- list[SomeEntityType].withAncestor(parent).sequenced()
      } yield (before, deleted, after))
      result match {
        case Right((before, deleted, after)) =>
          before should contain allElementsOf entities
          deleted should contain theSameElementsAs keys
          for (entity <- entities) {
            after should not contain entity
          }
        case Left(error) => fail(s"There was an error: $error")
      }
    }

    scenario("DeleteAll entities") {
      val parent = EntityParent(500) // Use combined parent so that we can have strong consistency in the queries
      val entities = Seq(randomEntityWithKey(ComplexKey("DeleteAllEntity1", parent)), randomEntityWithKey(ComplexKey("DeleteAllEntity2", parent)), randomEntityWithKey(ComplexKey("DeleteAllEntity3", parent)))
      val keys = entities.map(e => ComplexKey(e.id, e.parent))
      val result = run(for {
        _ <- putAll(entities)
        before <- list[SomeEntityType].withAncestor(parent).sequenced()
        deleted <- deleteAllEntities[SomeEntityType, ComplexKey](entities)
        after <- list[SomeEntityType].withAncestor(parent).sequenced()
      } yield (before, deleted, after))
      result match {
        case Right((before, deleted, after)) =>
          before should contain allElementsOf entities
          deleted should contain theSameElementsAs keys
          for (entity <- entities) {
            after should not contain entity
          }
        case Left(error) => fail(s"There was an error: $error")
      }
    }
  }
  feature("Support for Datastore Transactions") {
    scenario("SaveAll in transaction that fails") {
      val key = ComplexKey("EntityToRollback", EntityParent(1230))
      val entityThatShouldRollback = randomEntityWithKey(key)
      val entityToCauseAFailure = randomEntityWithKey(key)
      val result = run(transactionally(for {
        _ <- put(entityThatShouldRollback)
        _ <- save(entityToCauseAFailure)
      } yield ()))
      result should be('Left)
      run(findOne[SomeEntityType, ComplexKey](key)) shouldBe Right(None)
    }
    scenario("Save and delete in a transaction that fails") {
      val key = ComplexKey("EntityToDeleteThatShouldRemain", EntityParent(1234))
      val entityThatShouldRemain = randomEntityWithKey(key)
      run(put(entityThatShouldRemain)).map(_.inputObject) shouldBe Right(entityThatShouldRemain)
      val entityToCauseAFailure = randomEntityWithId("EntityToCauseAFailureAfterDelete")
      val result = run(transactionally(for {
        _ <- delete[SomeEntityType, ComplexKey](key)
        _ <- save(entityToCauseAFailure)
        _ <- save(entityToCauseAFailure)
      } yield ()))
      result should be('Left)
      run(findOne[SomeEntityType, ComplexKey](key)) shouldBe Right(Some(entityThatShouldRemain))
    }
  }

  private val random = ThreadLocalRandom.current()

  private def randomEntityWithId(id: String) = randomEntityWithKey(ComplexKey(id, EntityParent(random.nextLong(1, Long.MaxValue))))

  private def randomEntityWithKey(complexKey: ComplexKey) = {
    val doubles = random.doubles().limit(random.nextInt(10)).toArray.toSeq
    SomeEntityType(
      complexKey.id,
      complexKey.parent,
      if (random.nextBoolean()) Some(random.nextInt()) else None,
      CompositeField(doubles, random.nextBoolean())
    )
  }
}
