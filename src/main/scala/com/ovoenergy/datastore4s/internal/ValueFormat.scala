package com.ovoenergy.datastore4s.internal

trait ValueFormat[A] {

  def toValue(a: A): DatastoreValue

  def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, A]

  def wrongType(expectedType: DsType, datastoreValue: DatastoreValue): Either[DatastoreError, A] = Left(new DatastoreError {})

}

object ValueFormat {

  implicit object StringValueFormat extends ValueFormat[String] {
    override def toValue(a: String): DatastoreValue = StringValue(a)

    override def fromValue(datastoreValue: DatastoreValue): Either[DatastoreError, String] = datastoreValue match {
      case StringValue(string) => Right(string)
      case other => wrongType(StringValue, other)
    }
  }

}