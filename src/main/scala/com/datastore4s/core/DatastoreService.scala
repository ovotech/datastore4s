package com.datastore4s.core

import com.google.cloud.datastore.{Datastore, DatastoreOptions, Entity}

case class DataStoreConfiguration(projectId: String, namespace: String)

case class Persisted[A](inputObject: A, entity: Entity)

object DatastoreService {

  def createDatastore(dataStoreConfiguration: DataStoreConfiguration): Datastore = {
    DatastoreOptions.newBuilder()
      .setProjectId(dataStoreConfiguration.projectId)
      .setNamespace(dataStoreConfiguration.namespace)
      .build()
      .getService
  }

  def put[E <: DatastoreEntity[_]](entity: E)(implicit keyFactorySupplier: () => com.google.cloud.datastore.KeyFactory, format: EntityFormat[E, _], datastore: Datastore): Persisted[E] = {
    Persisted(entity, datastore.put(format.toEntity(entity)))
  }

  def list[E <: DatastoreEntity[_]](implicit format: EntityFormat[E, _], datastore: Datastore): Query[E] = {
    val kind = format.kind.name
    val queryBuilder =  com.google.cloud.datastore.Query.newEntityQueryBuilder().setKind(kind)
    DatastoreQuery(queryBuilder)
  }

}
