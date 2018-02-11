package com.ovoenergy.datastore4s.internal

import java.time.Instant

import com.google.cloud.Timestamp
import com.google.cloud.datastore.{Blob, LatLng}

import scala.util.{Failure, Success, Try}

trait ValueFormat[A] {

  def toValue(scalaValue: A): DatastoreValue

  def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, A]

  def wrongType(expectedType: DsType, datastoreValue: DatastoreValue): Either[DatastoreError, A] = Left(new DatastoreError {
    override def toString: String = s"Expected a $expectedType but got $datastoreValue"
  })

}

object ValueFormat {

  implicit object StringValueFormat extends ValueFormat[String] {
    override def toValue(scalaValue: String): DatastoreValue = StringValue(scalaValue)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, String] = datastoreValue match {
      case StringValue(string) => Right(string)
      case other => wrongType(StringValue, other)
    }
  }

  implicit object LongValueFormat extends ValueFormat[Long] {
    override def toValue(scalaValue: Long): DatastoreValue = LongValue(scalaValue)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Long] = datastoreValue match {
      case LongValue(long) => Right(long)
      case other => wrongType(LongValue, other)
    }
  }

  implicit object DoubleValueFormat extends ValueFormat[Double] {
    override def toValue(scalaValue: Double): DatastoreValue = DoubleValue(scalaValue)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Double] = datastoreValue match {
      case DoubleValue(long) => Right(long)
      case other => wrongType(DoubleValue, other)
    }
  }

  implicit object BooleanValueFormat extends ValueFormat[Boolean] {
    override def toValue(scalaValue: Boolean): DatastoreValue = BooleanValue(scalaValue)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Boolean] = datastoreValue match {
      case BooleanValue(bool) => Right(bool)
      case other => wrongType(BooleanValue, other)
    }
  }

  implicit object BlobValueFormat extends ValueFormat[Blob] {
    override def toValue(scalaValue: Blob): DatastoreValue = BlobValue(scalaValue)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Blob] = datastoreValue match {
      case BlobValue(blob) => Right(blob)
      case other => wrongType(BlobValue, other)
    }
  }

  implicit object TimestampValueFormat extends ValueFormat[Timestamp] {
    override def toValue(scalaValue: Timestamp): DatastoreValue = TimestampValue(scalaValue)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Timestamp] = datastoreValue match {
      case TimestampValue(timestamp) => Right(timestamp)
      case other => wrongType(TimestampValue, other)
    }
  }

  implicit object LatLngValueFormat extends ValueFormat[LatLng] {
    override def toValue(scalaValue: LatLng): DatastoreValue = LatLngValue(scalaValue)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, LatLng] = datastoreValue match {
      case LatLngValue(latlng) => Right(latlng)
      case other => wrongType(LatLngValue, other)
    }
  }

  // The following formats will have to be brought into implicit scope to be used
  object ByteArrayValueFormat extends ValueFormat[Array[Byte]] {
    override def toValue(scalaValue: Array[Byte]): DatastoreValue = BlobValueFormat.toValue(Blob.copyFrom(scalaValue))

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Array[Byte]] =
      BlobValueFormat.fromValue(datastoreValue).map(_.toByteArray)
  }

  object InstantEpochMillisValueFormat extends ValueFormat[Instant] {
    override def toValue(scalaValue: Instant): DatastoreValue = LongValueFormat.toValue(scalaValue.toEpochMilli)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Instant] =
      LongValueFormat.fromValue(datastoreValue).map(Instant.ofEpochMilli)

  }

  object BigDecimalStringValueFormat extends ValueFormat[BigDecimal] {
    override def toValue(scalaValue: BigDecimal): DatastoreValue = StringValueFormat.toValue(scalaValue.toString())

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, BigDecimal] =
      StringValueFormat.fromValue(datastoreValue).flatMap { str =>
        Try(BigDecimal(str)) match {
          case Success(bd) => Right(bd)
          case Failure(exception) => Left(new DatastoreError {
            override def toString: String = s"Could not parse BigDecimal from $str. Error: ${exception.getMessage}"
          })
        }
      }
  }

  // TODO implicit def for list
  // TODO implicit def for Entity? So that we can have nested entities? Or should that go in field format so we can pass in key factory????

}