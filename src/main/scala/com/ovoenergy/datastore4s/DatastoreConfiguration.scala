package com.ovoenergy.datastore4s

import com.google.cloud.datastore.DatastoreOptions

sealed trait DatastoreConfiguration

final case class ManualDatastoreConfiguration(projectId: String, namespace: Option[String] = None) extends DatastoreConfiguration
final case class EmulatorConfiguration(projectId: String, emulatorHost: String, namespace: Option[String]) extends DatastoreConfiguration
final case class Options(datastoreOptions: DatastoreOptions) extends DatastoreConfiguration

/** Configures the datastore service using the environment variables:
  *   - DATASTORE_PROJECT_ID: The ID of the GCP project to connect to.
  *   - DATASTORE_NAMESPACE: An optional namespace to store your entities under.
  *   - DATASTORE_EMULATOR_HOST: An optional host if the datastore emulator is being used.
  */
final case object FromEnvironmentVariables extends DatastoreConfiguration

object DatastoreConfiguration {

  /** Configures the datastore service using the given options, can be overridden to use the datastore emulator by using
    * the DATASTORE_EMULATOR_HOST environment variable
    */
  def apply(datastoreOptions: DatastoreOptions): DatastoreConfiguration = Options(datastoreOptions)

  /** Configures the datastore service to connect to the default namespace in the given project, can be overridden to
    * use the datastore emulator by using the DATASTORE_EMULATOR_HOST environment variable
    */
  def apply(projectId: String): DatastoreConfiguration = ManualDatastoreConfiguration(projectId)

  /** Configures the datastore service to connect to the given namespace in the given project, can be overridden to
    * use the datastore emulator by using the DATASTORE_EMULATOR_HOST environment variable
    */
  def apply(projectId: String, namespace: String): DatastoreConfiguration = ManualDatastoreConfiguration(projectId, Some(namespace))

  /** Configures the datastore service to connect to the datastore emulator at the given host using the given projectId
    * and namespace if provided.
    */
  def apply(projectId: String, emulatorHost: String, namespace: Option[String]): DatastoreConfiguration =
    EmulatorConfiguration(projectId, emulatorHost, namespace)

  import scala.language.implicitConversions
  implicit def fromOptions(datastoreOptions: DatastoreOptions): DatastoreConfiguration = DatastoreConfiguration(datastoreOptions)
}
