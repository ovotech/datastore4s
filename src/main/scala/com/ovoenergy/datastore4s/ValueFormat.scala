package com.ovoenergy.datastore4s

import java.time.Instant

import com.google.cloud.Timestamp
import com.google.cloud.datastore.{Blob, LatLng}

import scala.util.{Failure, Success, Try}

sealed trait ValueFormat[A] {

  def toValue(scalaValue: A): DatastoreValue

  def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, A]

  def ignoreIndex: ValueFormat[A] = ValueFormat.ignoreIndex(this)

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

  implicit val intValueFormat: ValueFormat[Int] = formatFrom[Int, Long](_.toInt)(_.toLong)

  implicit object DoubleValueFormat extends ValueFormat[Double] with DatastoreErrors {
    override def toValue(scalaValue: Double): DatastoreValue =
      DoubleValue(scalaValue)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Double] =
      datastoreValue match {
        case DoubleValue(double) => Right(double)
        case other               => wrongType(DoubleValue, other)
      }
  }

  implicit val floatValueFormat: ValueFormat[Float] = formatFrom[Float, Double](_.toFloat)(_.toDouble)

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
  val byteArrayValueFormat: ValueFormat[Array[Byte]] = formatFrom[Array[Byte], Blob](_.toByteArray)(Blob.copyFrom)

  val instantEpochMillisValueFormat: ValueFormat[Instant] = formatFrom(Instant.ofEpochMilli)(_.toEpochMilli)

  val bigDecimalDoubleValueFormat: ValueFormat[BigDecimal] = formatFrom(BigDecimal.valueOf(_: Double))(_.doubleValue())

  object BigDecimalStringValueFormat extends ValueFormat[BigDecimal] with DatastoreErrors {
    override def toValue(scalaValue: BigDecimal): DatastoreValue =
      StringValueFormat.toValue(scalaValue.toString())

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, BigDecimal] =
      StringValueFormat.fromValue(datastoreValue).flatMap { str =>
        Try(BigDecimal(str)) match {
          case Success(bd) => Right(bd)
          case Failure(exception) =>
            deserialisationError(s"Could not parse BigDecimal from $str. Error: ${exception.getMessage}")
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

  implicit def setValueFormat[A](implicit seqFormat: ValueFormat[Seq[A]]) = formatFrom[Set[A], Seq[A]](_.toSet)(_.toList)


  implicit def entityValueFormat[E, K](implicit entityFormat: EntityFormat[E, K], datastoreService: DatastoreService, toKey: ToKey[K]): ValueFormat[E] =
    new ValueFormat[E] {
      override def toValue(scalaValue: E): DatastoreValue = EntityValue(DatastoreService.toEntity(scalaValue, entityFormat, datastoreService))

      override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, E] = datastoreValue match {
        case EntityValue(entity) => entityFormat.fromEntity(entity)
        case other => DatastoreError.wrongType(EntityValue, other)
      }
    }

  def formatFrom[A, B](constructor: B => A)(extractor: A => B)(implicit format: ValueFormat[B]): ValueFormat[A] =
    formatFromFunctionsWithError(constructor andThen (a => Right(a)))(extractor)

  def failableFormatFrom[A, B](constructor: B => Either[String, A])(extractor: A => B)(implicit format: ValueFormat[B]): ValueFormat[A] = {
    val newConstructor = constructor andThen {
      case Left(error) => DatastoreError.deserialisationError(error)
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

  def ignoreIndex[A](existingFormat: ValueFormat[A]): ValueFormat[A] = new ValueFormat[A] {
    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, A] = existingFormat.fromValue(datastoreValue)

    override def toValue(scalaValue: A): DatastoreValue = existingFormat.toValue(scalaValue).ignoreIndex
  }

}

trait DefaultFormats {
  implicit val bigDecimalFormat = ValueFormat.BigDecimalStringValueFormat
  implicit val instantFormat = ValueFormat.instantEpochMillisValueFormat
  implicit val byteArrayFormat = ValueFormat.byteArrayValueFormat
}
