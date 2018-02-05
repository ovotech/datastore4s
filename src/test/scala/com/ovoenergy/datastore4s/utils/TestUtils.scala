package com.ovoenergy.datastore4s.utils

import com.google.cloud.datastore.{Datastore, DatastoreOptions}
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