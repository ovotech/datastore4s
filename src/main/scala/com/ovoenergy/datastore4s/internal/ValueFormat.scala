package com.ovoenergy.datastore4s.internal

trait ValueFormat[A] {

  def toValue(a: A): DatastoreValue

  def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, A]

  def wrongType(expectedType: DsType, datastoreValue: DatastoreValue): Either[DatastoreError, A] = Left(new DatastoreError {
    override def toString: String = s"Expected a $expectedType but got $datastoreValue"
  })

}

object ValueFormat {

  implicit object StringValueFormat extends ValueFormat[String] {
    override def toValue(a: String): DatastoreValue = StringValue(a)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, String] = datastoreValue match {
      case StringValue(string) => Right(string)
      case other => wrongType(StringValue, other)
    }
  }

  implicit object LongValueFormat extends ValueFormat[Long] {
    override def toValue(a: Long): DatastoreValue = LongValue(a)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Long] = datastoreValue match {
      case LongValue(long) => Right(long)
      case other => wrongType(LongValue, other)
    }
  }

  implicit object DoubleValueFormat extends ValueFormat[Double] {
    override def toValue(a: Double): DatastoreValue = DoubleValue(a)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Double] = datastoreValue match {
      case DoubleValue(long) => Right(long)
      case other => wrongType(DoubleValue, other)
    }
  }

  implicit object BooleanValueFormat extends ValueFormat[Boolean] {
    override def toValue(a: Boolean): DatastoreValue = BooleanValue(a)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, Boolean] = datastoreValue match {
      case BooleanValue(bool) => Right(bool)
      case other => wrongType(BooleanValue, other)
    }
  }

}