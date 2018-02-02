package com.datastore4s.core

import com.google.cloud.datastore.{Datastore, DatastoreOptions, Entity, ReadOption}

import scala.util.Try

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

  def findOne[E <: DatastoreEntity[K], K](key: K)(implicit format: EntityFormat[E, K], toKey: ToKey[K], datastore: Datastore): Option[Try[E]] = {
    val keyFactory = new KeyFactoryFacade(datastore.newKeyFactory().setKind(format.kind.name))
    val entityKey = toKey.toKey(key, keyFactory)
    Option(datastore.get(entityKey, Seq.empty[ReadOption]: _*)).map(format.fromEntity)
  }

  def put[E <: DatastoreEntity[_]](entity: E)(implicit keyFactorySupplier: () => com.google.cloud.datastore.KeyFactory, format: EntityFormat[E, _], datastore: Datastore): Persisted[E] = {
    Persisted(entity, datastore.put(format.toEntity(entity)))
  }

  def list[E <: DatastoreEntity[_]](implicit format: EntityFormat[E, _], datastore: Datastore): Query[E] = {
    val kind = format.kind.name
    val queryBuilder = com.google.cloud.datastore.Query.newEntityQueryBuilder().setKind(kind)
    DatastoreQuery(queryBuilder)
  }

  def project[E <: DatastoreEntity[_]](firstField: String, remainingFields: String*)(implicit format: EntityFormat[E, _], datastore: Datastore): Project = {
    val kind = format.kind.name
    val queryBuilder = com.google.cloud.datastore.Query.newProjectionEntityQueryBuilder().setKind(kind).setProjection(firstField, remainingFields: _*)
    Project(queryBuilder)
  }

}
