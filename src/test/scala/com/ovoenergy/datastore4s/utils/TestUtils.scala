package com.ovoenergy.datastore4s.utils

import com.google.cloud.datastore.{Datastore, DatastoreOptions}
import com.ovoenergy.datastore4s.internal.{DatastoreValue, Entity, EntityBuilder}
import com.ovoenergy.datastore4s.{KeyFactory, KeyFactoryFacade}

object TestDatastore {
  def apply(): Datastore = DatastoreOptions.newBuilder()
    .setProjectId("test-project")
    .setNamespace("test-namespace")
    .build().getService
}

object TestKeyFactory {
  def apply(testDatastore: Datastore): KeyFactory = new KeyFactoryFacade(testDatastore.newKeyFactory().setKind("test-kind"))
}

case class StubEntity(fields: Map[String, DatastoreValue]) extends Entity {
  override def field(name: String) = fields.get(name)

  override def rawEntity = ???
}

case class StubEntityBuilder(fields: Map[String, DatastoreValue] = Map()) extends EntityBuilder {
  override def addField(name: String, value: DatastoreValue) = StubEntityBuilder(fields + (name -> value))

  override def build() = StubEntity(fields)
}