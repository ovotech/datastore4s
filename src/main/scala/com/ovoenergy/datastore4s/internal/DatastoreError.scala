package com.ovoenergy.datastore4s.internal

trait DatastoreError

// TODO tidy this up
object DatastoreError {
  def missingField[A](fieldName: String, entity: Entity): Either[DatastoreError, A] =
    Left(new DatastoreError {
      override def toString: String =
        s"Field $fieldName could not be found on entity $entity"
    })

  def wrongType[A](expectedType: DsType, datastoreValue: DatastoreValue): Either[DatastoreError, A] =
    Left(new DatastoreError {
      override def toString: String =
        s"Expected a $expectedType but got $datastoreValue"
    })

  def error[A](error: String): Either[DatastoreError, A] =
    Left(new DatastoreError {
      override def toString: String = error
    })

  def sequence[A](values: Seq[Either[DatastoreError, A]]): Either[DatastoreError, Seq[A]] =
    values.foldLeft(Right(Seq.empty): Either[DatastoreError, Seq[A]]) {
      case (Right(acc), Right(value)) => Right(value +: acc)
      case (Left(errorAcc), Left(error)) =>
        Left(new DatastoreError {
          override def toString: String = s"$errorAcc\n$error"
        })
      case (Right(_), Left(error))    => Left(error)
      case (Left(errorAcc), Right(_)) => Left(errorAcc)
    }

}

trait DatastoreErrors {
  def missingField[A](fieldName: String, entity: Entity): Either[DatastoreError, A] =
    DatastoreError.missingField(fieldName, entity)

  def wrongType[A](expectedType: DsType, datastoreValue: DatastoreValue): Either[DatastoreError, A] =
    DatastoreError.wrongType(expectedType, datastoreValue)

  def error[A](error: String): Either[DatastoreError, A] =
    DatastoreError.error(error)
}
