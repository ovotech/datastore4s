package com.ovoenergy.datastore4s.utils

import com.datastore4s.core.{KeyFactory, KeyFactoryFacade}
import com.google.cloud.datastore.{Datastore, DatastoreOptions}

object TestDatastore {
  def apply(): Datastore = DatastoreOptions.newBuilder()
    .setProjectId("test-project")
    .setNamespace("test-namespace")
    .build().getService
}

object TestKeyFactory {
  def apply(testDatastore: Datastore): KeyFactory = new KeyFactoryFacade(testDatastore.newKeyFactory().setKind("test-kind"))
}