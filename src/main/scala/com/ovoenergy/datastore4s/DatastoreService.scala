package com.ovoenergy.datastore4s

import com.google.cloud.datastore.{
  Datastore,
  DatastoreOptions,
  Entity,
  ReadOption
}
import com.ovoenergy.datastore4s.internal.{DatastoreError, WrappedEntity}

case class DataStoreConfiguration(projectId: String, namespace: String)

case class Persisted[A](inputObject: A, entity: Entity)

object DatastoreService {

  def createDatastore(
      dataStoreConfiguration: DataStoreConfiguration): Datastore = {
    DatastoreOptions
      .newBuilder()
      .setProjectId(dataStoreConfiguration.projectId)
      .setNamespace(dataStoreConfiguration.namespace)
      .build()
      .getService
  }

  // TODO Unit and Integration tests for below functions
  def findOne[E, K](key: K)(
      implicit format: EntityFormat[E, K],
      toKey: ToKey[K],
      datastore: Datastore): Option[Either[DatastoreError, E]] = {
    val keyFactory = KeyFactoryFacade(datastore, format.kind)
    val entityKey = toKey.toKey(key, keyFactory)
    Option(datastore.get(entityKey, Seq.empty[ReadOption]: _*))
      .map(WrappedEntity(_))
      .map(format.fromEntity)
  }

  def put[E](entityObject: E)(implicit format: EntityFormat[E, _],
                              datastore: Datastore): Persisted[E] = {
    implicit val keyFactorySupplier = () => datastore.newKeyFactory()
    val entity = format.toEntity(entityObject) match {
      case WrappedEntity(e: Entity) => e
    }
    Persisted(entityObject, datastore.put(entity))
  }

  def list[E](implicit format: EntityFormat[E, _],
              datastore: Datastore): Query[E] = {
    val kind = format.kind.name
    val queryBuilder =
      com.google.cloud.datastore.Query.newEntityQueryBuilder().setKind(kind)
    DatastoreQuery(queryBuilder)
  }

  def project[E]()(implicit format: EntityFormat[E, _],
                   datastore: Datastore): Project[E] = Project()

}
