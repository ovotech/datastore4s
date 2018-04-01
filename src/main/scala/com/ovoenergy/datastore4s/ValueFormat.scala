package com.ovoenergy.datastore4s

import java.time.Instant

import com.google.cloud.Timestamp
import com.google.cloud.datastore.{Blob, LatLng}

import scala.util.{Failure, Success, Try}

trait ValueFormat[A] {
  // TODO Remove ValueFormat, not needed, just have Fields. Can we do the same for Entity? Just store it is root level FieldFormat? Have field as a trait and combine them with dotting?

  def toValue(scalaValue: A): DatastoreValue

  def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, A]

}

object ValueFormat {

  implicit object StringValueFormat extends ValueFormat[String] with DatastoreErrors {
    override def toValue(scalaValue: String): DatastoreValue =
      StringValue(scalaValue)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, String] =
      datastoreValue match {
        case StringValue(string) => Right(string)
        case other               => wrongType(StringValue, other)
      }
  }

  implicit object LongValueFormat extends ValueFormat[Long] with DatastoreErrors {
    override def toValue(scalaValue: Long): DatastoreValue =
      LongValue(scalaValue)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Long] =
      datastoreValue match {
        case LongValue(long) => Right(long)
        case other           => wrongType(LongValue, other)
      }
  }

  implicit object IntValueFormat extends ValueFormat[Int] with DatastoreErrors {
    override def toValue(scalaValue: Int): DatastoreValue =
      LongValue(scalaValue)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Int] =
      datastoreValue match {
        case LongValue(long) => Right(long.toInt)
        case other           => wrongType(LongValue, other)
      }
  }

  implicit object DoubleValueFormat extends ValueFormat[Double] with DatastoreErrors {
    override def toValue(scalaValue: Double): DatastoreValue =
      DoubleValue(scalaValue)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Double] =
      datastoreValue match {
        case DoubleValue(double) => Right(double)
        case other               => wrongType(DoubleValue, other)
      }
  }

  implicit object BooleanValueFormat extends ValueFormat[Boolean] with DatastoreErrors {
    override def toValue(scalaValue: Boolean): DatastoreValue =
      BooleanValue(scalaValue)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Boolean] =
      datastoreValue match {
        case BooleanValue(bool) => Right(bool)
        case other              => wrongType(BooleanValue, other)
      }
  }

  implicit object BlobValueFormat extends ValueFormat[Blob] with DatastoreErrors {
    override def toValue(scalaValue: Blob): DatastoreValue =
      BlobValue(scalaValue)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Blob] =
      datastoreValue match {
        case BlobValue(blob) => Right(blob)
        case other           => wrongType(BlobValue, other)
      }
  }

  implicit object TimestampValueFormat extends ValueFormat[Timestamp] with DatastoreErrors {
    override def toValue(scalaValue: Timestamp): DatastoreValue =
      TimestampValue(scalaValue)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Timestamp] =
      datastoreValue match {
        case TimestampValue(timestamp) => Right(timestamp)
        case other                     => wrongType(TimestampValue, other)
      }
  }

  implicit object LatLngValueFormat extends ValueFormat[LatLng] with DatastoreErrors {
    override def toValue(scalaValue: LatLng): DatastoreValue =
      LatLngValue(scalaValue)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, LatLng] =
      datastoreValue match {
        case LatLngValue(latlng) => Right(latlng)
        case other               => wrongType(LatLngValue, other)
      }
  }

  // The following formats will have to be brought into implicit scope to be used
  object ByteArrayValueFormat extends ValueFormat[Array[Byte]] {
    override def toValue(scalaValue: Array[Byte]): DatastoreValue =
      BlobValueFormat.toValue(Blob.copyFrom(scalaValue))

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Array[Byte]] =
      BlobValueFormat.fromValue(datastoreValue).map(_.toByteArray)
  }

  object InstantEpochMillisValueFormat extends ValueFormat[Instant] {
    override def toValue(scalaValue: Instant): DatastoreValue =
      LongValueFormat.toValue(scalaValue.toEpochMilli)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Instant] =
      LongValueFormat.fromValue(datastoreValue).map(Instant.ofEpochMilli)

  }

  object BigDecimalStringValueFormat extends ValueFormat[BigDecimal] with DatastoreErrors {
    override def toValue(scalaValue: BigDecimal): DatastoreValue =
      StringValueFormat.toValue(scalaValue.toString())

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, BigDecimal] =
      StringValueFormat.fromValue(datastoreValue).flatMap { str =>
        Try(BigDecimal(str)) match {
          case Success(bd) => Right(bd)
          case Failure(exception) =>
            error(s"Could not parse BigDecimal from $str. Error: ${exception.getMessage}")
        }
      }
  }

  implicit def optionValueFormat[A](implicit elementFormat: ValueFormat[A]): ValueFormat[Option[A]] =
    new ValueFormat[Option[A]] {
      override def toValue(scalaValue: Option[A]): DatastoreValue =
        scalaValue match {
          case Some(a) => elementFormat.toValue(a)
          case None    => NullValue()
        }

      override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Option[A]] =
        datastoreValue match {
          case NullValue(_) => Right(None)
          case value        => elementFormat.fromValue(value).map(Some(_))
        }
    }

  implicit def listValueFormat[A](implicit elementFormat: ValueFormat[A]): ValueFormat[Seq[A]] =
    new ValueFormat[Seq[A]] with DatastoreErrors {
      override def toValue(scalaValue: Seq[A]): DatastoreValue =
        ListValue(scalaValue.map(elementFormat.toValue))

      override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Seq[A]] =
        datastoreValue match {
          case ListValue(values) =>
            DatastoreError.sequence(values.map(elementFormat.fromValue))
          case other => wrongType(ListValue, other)
        }
    }

  def formatFromFunctions[A, B](constructor: B => A)(extractor: A => B)(implicit format: ValueFormat[B]): ValueFormat[A] =
    formatFromFunctionsWithError(constructor andThen (a => Right(a)))(extractor)

  def formatFromFunctionsEither[A, B](
    constructor: B => Either[String, A]
  )(extractor: A => B)(implicit format: ValueFormat[B]): ValueFormat[A] = {
    val newConstructor = constructor andThen {
      case Left(error) => DatastoreError.error(error)
      case Right(r)    => Right(r)
    }
    formatFromFunctionsWithError(newConstructor)(extractor)
  }

  private def formatFromFunctionsWithError[A, B](
    constructor: B => Either[DatastoreError, A]
  )(extractor: A => B)(implicit format: ValueFormat[B]): ValueFormat[A] = new ValueFormat[A] {
    override def toValue(scalaValue: A): DatastoreValue = format.toValue(extractor(scalaValue))

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, A] =
      format.fromValue(datastoreValue).flatMap(constructor)
  }

}
