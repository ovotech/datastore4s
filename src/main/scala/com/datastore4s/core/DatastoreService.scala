package com.datastore4s.core

import com.google.cloud.datastore.{Datastore, DatastoreOptions}

case class DataStoreConfiguration(projectId: String, namespace: String)

object DatastoreService {

  def createDatastore(dataStoreConfiguration: DataStoreConfiguration): Datastore =
    DatastoreOptions.newBuilder().setProjectId(dataStoreConfiguration.projectId).setNamespace(dataStoreConfiguration.namespace).build().getService

  def put[E <: DatastoreEntity[_]](entity: E)(implicit datastore: Datastore, format: EntityFormat[E, _]) = {
    implicit val kfs = () => datastore.newKeyFactory()
    println(s"Would have put: ${format.toEntity(entity)}")
  }

}
