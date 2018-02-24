package com.ovoenergy.datastore4s

import com.google.cloud.Timestamp
import com.google.cloud.datastore.{Blob, LatLng, Value}

sealed trait DatastoreValue

private[datastore4s] class WrappedValue(val dsValue: Value[_]) extends DatastoreValue {
  override def toString: String = this match {
    case StringValue(s)    => s"""StringValue("$s")"""
    case LongValue(l)      => s"LongValue($l)"
    case DoubleValue(d)    => s"DoubleValue($d)"
    case BooleanValue(d)   => s"BooleanValue($d)"
    case BlobValue(b)      => s"BlobValue($b)"
    case TimestampValue(t) => s"TimestampValue($t)"
    case LatLngValue(ll)   => s"LatLngValue($ll)"
    case ListValue(values) => s"ListValue(${values.mkString(", ")})"
    case NullValue(_)      => "NullValue"
  }

  override def equals(obj: scala.Any): Boolean = obj match {
    case v: WrappedValue => v.dsValue == dsValue
    case _               => false
  }
}

private[datastore4s] object WrappedValue {
  def unapply(value: WrappedValue): Option[Value[_]] = Some(value.dsValue)
}

private[datastore4s] sealed trait DsType

case object StringValue extends DsType {
  def apply(string: String): DatastoreValue =
    new WrappedValue(new com.google.cloud.datastore.StringValue(string))

  def unapply(value: DatastoreValue): Option[String] = value match {
    case WrappedValue(s: com.google.cloud.datastore.StringValue) => Some(s.get())
    case _                                         => None
  }
}

case object LongValue extends DsType {
  def apply(long: Long): DatastoreValue =
    new WrappedValue(new com.google.cloud.datastore.LongValue(long))

  def unapply(value: DatastoreValue): Option[Long] = value match {
    case WrappedValue(l: com.google.cloud.datastore.LongValue) => Some(l.get())
    case _                                       => None
  }
}

case object DoubleValue extends DsType {
  def apply(double: Double): DatastoreValue =
    new WrappedValue(new com.google.cloud.datastore.DoubleValue(double))

  def unapply(value: DatastoreValue): Option[Double] = value match {
    case WrappedValue(d: com.google.cloud.datastore.DoubleValue) => Some(d.get())
    case _                                         => None
  }
}

case object BooleanValue extends DsType {
  def apply(boolean: Boolean): DatastoreValue =
    new WrappedValue(new com.google.cloud.datastore.BooleanValue(boolean))

  def unapply(value: DatastoreValue): Option[Boolean] = value match {
    case WrappedValue(b: com.google.cloud.datastore.BooleanValue) => Some(b.get())
    case _                                          => None
  }
}

case object TimestampValue extends DsType {
  def apply(timeStamp: Timestamp): DatastoreValue =
    new WrappedValue(new com.google.cloud.datastore.TimestampValue(timeStamp))

  def unapply(value: DatastoreValue): Option[Timestamp] = value match {
    case WrappedValue(t: com.google.cloud.datastore.TimestampValue) => Some(t.get())
    case _                                            => None
  }
}

case object BlobValue extends DsType {
  def apply(blob: Blob): DatastoreValue =
    new WrappedValue(new com.google.cloud.datastore.BlobValue(blob))

  def unapply(value: DatastoreValue): Option[Blob] = value match {
    case WrappedValue(b: com.google.cloud.datastore.BlobValue) => Some(b.get())
    case _                                       => None
  }
}

case object LatLngValue extends DsType {
  def apply(latlng: LatLng): DatastoreValue =
    new WrappedValue(new com.google.cloud.datastore.LatLngValue(latlng))

  def unapply(value: DatastoreValue): Option[LatLng] = value match {
    case WrappedValue(l: com.google.cloud.datastore.LatLngValue) => Some(l.get())
    case _                                         => None
  }
}

case object ListValue extends DsType {

  import scala.collection.JavaConverters._

  def apply(values: Seq[DatastoreValue]): DatastoreValue =
    new WrappedValue(new com.google.cloud.datastore.ListValue(values.map{ case WrappedValue(v) => v }.asJava))

  def unapply(value: DatastoreValue): Option[Seq[DatastoreValue]] =
    value match {
      case WrappedValue(l: com.google.cloud.datastore.ListValue) =>
        Some(l.get().asScala.map(new WrappedValue(_)))
      case _ => None
    }
}

private[datastore4s] case object NullValue {
  def apply(): DatastoreValue =
    new WrappedValue(new com.google.cloud.datastore.NullValue())

  def unapply(value: DatastoreValue): Option[Null] = value match {
    case WrappedValue(_: com.google.cloud.datastore.NullValue) => Some(null)
    case _                                       => None
  }
}
