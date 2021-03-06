package com.ovoenergy.datastore4s

import com.google.cloud.Timestamp
import com.google.cloud.datastore._
import com.google.cloud.{datastore => ds}

sealed trait DatastoreValue {
  def ignoreIndex: DatastoreValue
}

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
    case EntityValue(e)    => s"EntityValue($e)"
    case NullValue(_)      => "NullValue"
  }

  override def ignoreIndex =
    new WrappedValue(
      dsValue.toBuilder
        .setExcludeFromIndexes(true)
        .asInstanceOf[ValueBuilder[_, _, _]] // TODO these casts are a hack to get around the poor return type of .toBuilder
        .build()
        .asInstanceOf[Value[_]]
    )

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
    new WrappedValue(ds.StringValue.of(string))

  def unapply(value: DatastoreValue): Option[String] = value match {
    case WrappedValue(s: ds.StringValue) => Some(s.get())
    case _                               => None
  }
}

case object LongValue extends DsType {
  def apply(long: Long): DatastoreValue =
    new WrappedValue(ds.LongValue.of(long))

  def unapply(value: DatastoreValue): Option[Long] = value match {
    case WrappedValue(l: ds.LongValue) => Some(l.get())
    case _                             => None
  }
}

case object DoubleValue extends DsType {
  def apply(double: Double): DatastoreValue =
    new WrappedValue(ds.DoubleValue.of(double))

  def unapply(value: DatastoreValue): Option[Double] = value match {
    case WrappedValue(d: ds.DoubleValue) => Some(d.get())
    case _                               => None
  }
}

case object BooleanValue extends DsType {
  def apply(boolean: Boolean): DatastoreValue =
    new WrappedValue(ds.BooleanValue.of(boolean))

  def unapply(value: DatastoreValue): Option[Boolean] = value match {
    case WrappedValue(b: ds.BooleanValue) => Some(b.get())
    case _                                => None
  }
}

case object TimestampValue extends DsType {
  def apply(timeStamp: Timestamp): DatastoreValue =
    new WrappedValue(ds.TimestampValue.of(timeStamp))

  def unapply(value: DatastoreValue): Option[Timestamp] = value match {
    case WrappedValue(t: ds.TimestampValue) => Some(t.get())
    case _                                  => None
  }
}

case object BlobValue extends DsType {
  def apply(blob: Blob): DatastoreValue =
    new WrappedValue(ds.BlobValue.of(blob))

  def unapply(value: DatastoreValue): Option[Blob] = value match {
    case WrappedValue(b: ds.BlobValue) => Some(b.get())
    case _                             => None
  }
}

case object LatLngValue extends DsType {
  def apply(latlng: LatLng): DatastoreValue =
    new WrappedValue(ds.LatLngValue.of(latlng))

  def unapply(value: DatastoreValue): Option[LatLng] = value match {
    case WrappedValue(l: ds.LatLngValue) => Some(l.get())
    case _                               => None
  }
}

case object ListValue extends DsType {

  import scala.collection.JavaConverters._

  def apply(values: Seq[DatastoreValue]): DatastoreValue =
    new WrappedValue(ds.ListValue.of(values.map { case WrappedValue(v) => v }.asJava))

  def unapply(value: DatastoreValue): Option[Seq[DatastoreValue]] =
    value match {
      case WrappedValue(l: ds.ListValue) =>
        Some(l.get().asScala.map(new WrappedValue(_)))
      case _ => None
    }
}

private[datastore4s] case object EntityValue extends DsType {

  def apply(entity: Entity): DatastoreValue = entity match {
    case e: WrappedEntity => new WrappedValue(ds.EntityValue.newBuilder(e.entity).setExcludeFromIndexes(true).build())
    case p: ProjectionEntity =>
      sys.error(s"Project entity was passed to EntityValue.apply. This should never happen. Projection entity was: $p")
  }

  def unapply(value: DatastoreValue): Option[Entity] =
    value match {
      case WrappedValue(e: ds.EntityValue) =>
        Some(new WrappedEntity(e.get().asInstanceOf[ds.FullEntity[ds.Key]])) // TODO can we avoid cast??
      case _ => None
    }

}

private[datastore4s] case object NullValue {
  def apply(): DatastoreValue =
    new WrappedValue(ds.NullValue.of())

  def unapply(value: DatastoreValue): Option[Null] = value match {
    case WrappedValue(_: ds.NullValue) => Some(null)
    case _                             => None
  }
}
