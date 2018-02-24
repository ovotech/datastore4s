package com.ovoenergy.datastore4s.utils

import com.google.cloud.datastore.Datastore
import com.ovoenergy.datastore4s._

object TestKeyFactory {
  def apply(testDatastore: Datastore): KeyFactory = new KeyFactoryFacade(testDatastore.newKeyFactory().setKind("test-kind"))
}

case class StubEntity(fields: Map[String, DatastoreValue]) extends Entity {
  override def field(name: String) = fields.get(name)

  override def rawEntity = ???
}

case class StubEntityBuilder(fields: Map[String, DatastoreValue] = Map()) extends EntityBuilder {
  override def addField(field: Field) = StubEntityBuilder(field.values.toMap ++ fields)

  override def build() = StubEntity(fields)
}
