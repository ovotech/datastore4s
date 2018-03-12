package com.ovoenergy.datastore4s

import java.util.concurrent.ThreadLocalRandom

import com.google.cloud.datastore.Key
import org.scalatest.{FeatureSpec, Inside, Matchers}

case class SomeEntityType(id: String, parent: EntityParent, possibleInt: Option[Int], compositeField: CompositeField)

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
  override def dataStoreConfiguration = DataStoreConfiguration("datastore4s-project", "datastore4s")

  implicit val parentToAncestor = toLongAncestor[EntityParent]("parent")(_.id)
  implicit val parentFormat = formatFromFunctions(EntityParent.apply)(_.id)
  implicit val compositeFieldFormat = FieldFormat[CompositeField]
  implicit val entityFormat = EntityFormat[SomeEntityType, ComplexKey]("entity-kind")(entity => ComplexKey(entity.id, entity.parent))
  implicit val projectedFromEntity = FromEntity[ProjectedRow]

  implicit object ComplexKeyToKey extends ToKey[ComplexKey] {
    override def toKey(value: ComplexKey, keyFactory: KeyFactory): Key = {
      keyFactory.addAncestor(value.parent).buildWithName(value.id)
    }
  }

}

class DatastoreServiceITSpec extends FeatureSpec with Matchers with Inside with TestDatastoreRepository {

  feature("Datastore support for persistence") {
    scenario("Put single entity") {
      val entity = randomEntityWithId("PutEntity")
      val result = run(put(entity))
      result match {
        case Right(persisted) =>
          persisted.inputObject shouldBe entity
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
        case Right(persisted) =>
          persisted shouldBe Some(replacementEntity)
        case Left(error) => fail(s"There was an error: $error")
      }
    }
    scenario("Save single entity") {
      val entity = randomEntityWithId("SaveEntity")
      val result = run(save(entity))
      result match {
        case Right(persisted) =>
          persisted.inputObject shouldBe entity
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
    scenario("Entity with key does not exist") {
      val key = ComplexKey("Non Existant Entity", EntityParent(10))
      val result = run(delete[SomeEntityType, ComplexKey](key))
      result shouldBe Right(key) // TODO should this be Right?? This gives the impression something was deleted. Can datastore actually help here??
    }
    scenario("Entity with a key that exists") {
      val entity = randomEntityWithId("Entity That Exists")
      val key = ComplexKey(entity.id, entity.parent)
      val result = run(for {
        _ <- put(entity)
        before <- findOne[SomeEntityType, ComplexKey](key)
        deleted <- delete[SomeEntityType, ComplexKey](key)
        after <- findOne[SomeEntityType, ComplexKey](key)
      } yield (before, deleted, after))
      result shouldBe Right((Some(entity), key, None))
    }
  }

  feature("Datastore support for listing entities of a type") {
    scenario("Sequence a type of entity") {
      val (entity1, entity2, entity3) = (randomEntityWithId("Entity1"), randomEntityWithId("Entity2"), randomEntityWithId("Entity3"))
      val result = run(for {
        _ <- put(entity1)
        _ <- put(entity2)
        sequence <- list[SomeEntityType].sequenced()
        _ <- put(entity3)
      } yield sequence)
      result match {
        case Right(seq) =>
          seq should contain(entity1)
          seq should contain(entity2)
          seq should not contain entity3
        case Left(error) => fail(s"There was an error: $error")
      }
    }
    scenario("Stream a type of entity") {
      val (entity1, entity2, entity3) = (randomEntityWithId("StreamEntity1"), randomEntityWithId("StreamEntity2"), randomEntityWithId("StreamEntity3"))
      val result = run(for {
        _ <- put(entity1)
        _ <- put(entity2)
        stream <- list[SomeEntityType].stream()
        _ <- put(entity3)
      } yield stream)
      result match {
        case Right(stream) =>
          stream should contain(Right(entity1))
          stream should contain(Right(entity2))
          stream should not contain Right(entity3)
        case Left(error) => fail(s"There was an error: $error")
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
      val (entity1, entity2, entity3) = (randomEntityWithId("Entity1").copy(possibleInt = Option(-20)), randomEntityWithId("Entity2").copy(possibleInt = Option(-20)), randomEntityWithId("Entity3"))
      val result = run(for {
        _ <- put(entity1)
        _ <- put(entity2)
        _ <- put(entity3.copy(possibleInt = None))
        sequence <- list[SomeEntityType].withPropertyEq("possibleInt", Option(-20)).sequenced()
      } yield sequence)
      result match {
        case Right(seq) =>
          seq should contain(entity1)
          seq should contain(entity2)
          seq should not contain entity3
        case Left(error) => fail(s"There was an error: $error")
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
        case Right(seq) =>
          seq shouldBe Seq(entity1)
        case Left(error) => fail(s"There was an error: $error")
      }
    }
  }

  feature("Datastore support for projections") {
    scenario("Project a seqence of entities into a row format") {
      // TODO Note here that the type of 'parent' is different, but the internal datastore type is still LongValue. I don't know if we want to allow this.
      val entity = randomEntityWithId("ProjectedEntity")
      val expectedProjection = ProjectedRow(entity.id, entity.compositeField.someBoolean, entity.parent.id)
      val result = run(for {
        _ <- put(entity)
        projections <- projectInto[SomeEntityType, ProjectedRow]("id" -> "entityId", "compositeField.someBoolean" -> "boolean", "parent" -> "parentAsLong").sequenced()
      } yield projections)
      result match {
        case Right(seq) =>
          seq should contain(expectedProjection)
        case Left(error) => fail(s"There was an error: $error")
      }
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
