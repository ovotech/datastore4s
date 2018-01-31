package com.datastore4s.core

import java.time.Instant

import com.google.cloud.Timestamp
import com.google.cloud.datastore.{Blob, Entity, LatLng}

trait FieldFormat[A] {

  def addField(value: A, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder

  def fromField(entity: Entity, fieldName: String): A

}

object FieldFormat {

  implicit object StringFieldFormat extends FieldFormat[String] {
    override def addField(value: String, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField(entity: Entity, fieldName: String): String = entity.getString(fieldName)
  }

  implicit object LongFieldFormat extends FieldFormat[Long] {
    override def addField(value: Long, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField(entity: Entity, fieldName: String): Long = entity.getLong(fieldName)
  }

  implicit object BooleanFieldFormat extends FieldFormat[Boolean] {
    override def addField(value: Boolean, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField(entity: Entity, fieldName: String): Boolean = entity.getBoolean(fieldName)
  }

  implicit object DoubleFieldFormat extends FieldFormat[Double] {
    override def addField(value: Double, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField(entity: Entity, fieldName: String): Double = entity.getDouble(fieldName)
  }

  implicit object IntFieldFormat extends FieldFormat[Int] {
    override def addField(value: Int, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField(entity: Entity, fieldName: String): Int = entity.getLong(fieldName).toInt
  }

  implicit object ByteArrayBlobFieldFormat extends FieldFormat[Array[Byte]] {
    override def addField(value: Array[Byte], fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, Blob.copyFrom(value))

    override def fromField(entity: Entity, fieldName: String): Array[Byte] = entity.getBlob(fieldName).toByteArray
  }

  implicit object TimestampFieldFormat extends FieldFormat[Timestamp] {
    override def addField(value: Timestamp, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField(entity: Entity, fieldName: String): Timestamp = entity.getTimestamp(fieldName)
  }

  implicit object LatLngFieldFormat extends FieldFormat[LatLng] {
    override def addField(value: LatLng, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value)

    override def fromField(entity: Entity, fieldName: String): LatLng = entity.getLatLng(fieldName)
  }

  implicit object InstantEpochMilliFormat extends FieldFormat[Instant] {
    override def addField(value: Instant, fieldName: String, entityBuilder: Entity.Builder): Entity.Builder = entityBuilder.set(fieldName, value.toEpochMilli)

    override def fromField(entity: Entity, fieldName: String): Instant = Instant.ofEpochMilli(entity.getLong(fieldName))
  }

}
