package com.ovoenergy.datastore4s

import com.google.cloud.datastore.DatastoreOptions

sealed trait DatastoreConfiguration

final case class ManualDatastoreConfiguration(projectId: String, namespace: Option[String] = None) extends DatastoreConfiguration
final case class EmulatorConfiguration(projectId: String, emulatorHost: String, namespace: Option[String]) extends DatastoreConfiguration
final case class Options(datastoreOptions: DatastoreOptions) extends DatastoreConfiguration
final case object FromEnvironmentVariables extends DatastoreConfiguration

object DatastoreConfiguration {
  def apply(datastoreOptions: DatastoreOptions): DatastoreConfiguration = Options(datastoreOptions)
  def apply(projectId: String): DatastoreConfiguration = ManualDatastoreConfiguration(projectId)
  def apply(projectId: String, namespace: String): DatastoreConfiguration = ManualDatastoreConfiguration(projectId, Some(namespace))
  def apply(projectId: String, emulatorHost: String, namespace: Option[String]): DatastoreConfiguration =
    EmulatorConfiguration(projectId, emulatorHost, namespace)

  import scala.language.implicitConversions
  implicit def fromOptions(datastoreOptions: DatastoreOptions): DatastoreConfiguration = DatastoreConfiguration(datastoreOptions)
}
