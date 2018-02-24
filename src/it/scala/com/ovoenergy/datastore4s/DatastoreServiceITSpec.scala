package com.ovoenergy.datastore4s

import java.util.concurrent.ThreadLocalRandom

import com.google.cloud.datastore.Key
import org.scalatest.{FeatureSpec, Matchers}

case class SomeEntityType(id: String, parent: EntityParent, possibleInt: Option[Int], compositeField: CompositeField)

case class CompositeField(doubles: Seq[Double], someBoolean: Boolean)

case class EntityParent(id: Long)

case class ComplexKey(id: String, parent: EntityParent)

trait TestDatastoreSupport extends DefaultDatastoreSupport {
  override def dataStoreConfiguration = DataStoreConfiguration("datastore4s-project", "datastore4s")

  implicit val parentToAncestor = toLongAncestor[EntityParent]("parent")(_.id)
  implicit val parentFormat = formatFromFunctions(EntityParent.apply)(_.id)
  implicit val compositeFieldFormat = FieldFormat[CompositeField]
  implicit val entityFormat = EntityFormat[SomeEntityType, ComplexKey]("entity-kind")(entity => ComplexKey(entity.id, entity.parent))
  implicit object ComplexKeyToKey extends ToKey[ComplexKey] {
    override def toKey(value: ComplexKey, keyFactory: KeyFactory): Key = {
      keyFactory.addAncestor(value.parent).buildWithName(value.id)
    }
  }

}

class DatastoreServiceITSpec extends FeatureSpec with Matchers with TestDatastoreSupport {

  feature("Datastore support for persistence") {
    scenario("Persist single entity") {
      val entity = randomEntityWithId("Entity1")
      val result = run(put(entity))
      result match {
        case Right(persisted) =>
          persisted.inputObject shouldBe entity
        case Left(error) => fail(s"There was an error: $error")
      }
    }
  }

  feature("Datastore support for finding single entities") {
    scenario("Entity with key does not exist") {
      pending
    }
    scenario("Entity with a key that exists") {
      pending
    }
  }

  feature("Datastore support for deleting entities") {
    scenario("Entity with key does not exist") {
      pending
    }
    scenario("Entity with a key that exists") {
      pending
    }
  }

  feature("Datastore support for listing entities of a kind") {
    scenario("Sequence a type of entity") {
      pending
    }
    scenario("Stream a type of entity") {
      pending
    }
  }

  feature("Datastore support for projections") {
    scenario("Project a seqence of entities into a row format") {
      // TODO Note here that the type of parent is different. But the internal datastore type is still string. Don't know if we want to allow this.
      case class ProjectedRow(entityId: String, boolean: Boolean, parentAsString: String)
      pending
    }
  }

  private def randomEntityWithId(id: String) = {
    val random = ThreadLocalRandom.current()
    val doubles = random.doubles().limit(random.nextInt(10)).toArray.toSeq
    SomeEntityType(
      id,
      EntityParent(random.nextLong()),
      if (random.nextBoolean()) Some(random.nextInt()) else None,
      CompositeField(doubles, random.nextBoolean())
    )
  }
}
