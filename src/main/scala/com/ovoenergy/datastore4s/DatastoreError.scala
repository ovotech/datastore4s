package com.ovoenergy.datastore4s

sealed trait DatastoreError

private[datastore4s] class DatastoreException(val exception: Throwable) extends DatastoreError

private[datastore4s] class DeserialisationError(val error: String) extends DatastoreError

private[datastore4s] class ComposedError(val errors: Seq[DatastoreError]) extends DatastoreError

object DatastoreError {
  def missingField[A](fieldName: String, entity: Entity): Either[DatastoreError, A] =
    Left(new DeserialisationError(s"Field $fieldName could not be found on entity $entity")) // TODO should this contain the whole entity as a string???

  def wrongType[A](expectedType: DsType, datastoreValue: DatastoreValue): Either[DatastoreError, A] =
    Left(new DeserialisationError(s"Expected a $expectedType but got $datastoreValue"))

  def error[A](error: String): Either[DatastoreError, A] =
    Left(new DeserialisationError(error))

  def exception[A](exception: Throwable): Either[DatastoreError, A] =
    Left(new DatastoreException(exception))

  def sequence[A](values: Seq[Either[DatastoreError, A]]): Either[DatastoreError, Seq[A]] =
    values.foldLeft(Right(Seq.empty): Either[ComposedError, Seq[A]]) {
      case (Right(acc), Right(value))    => Right(value +: acc)
      case (Left(errorAcc), Left(error)) => Left(new ComposedError(error +: errorAcc.errors))
      case (Right(_), Left(error))       => Left(new ComposedError(Seq(error)))
      case (Left(errorAcc), Right(_))    => Left(errorAcc)
    }

  def asException(error: DatastoreError): Throwable = error match {
    case e: DatastoreException   => e.exception
    case e: DeserialisationError => new RuntimeException(e.error)
    case e: ComposedError        => ComposedException(e.errors.map(asException))
  }

  case class ComposedException(throwables: Seq[Throwable]) extends Exception

}

trait DatastoreErrors {
  def missingField[A](fieldName: String, entity: Entity): Either[DatastoreError, A] =
    DatastoreError.missingField(fieldName, entity)

  def wrongType[A](expectedType: DsType, datastoreValue: DatastoreValue): Either[DatastoreError, A] =
    DatastoreError.wrongType(expectedType, datastoreValue)

  def error[A](error: String): Either[DatastoreError, A] =
    DatastoreError.error(error)

  def exception(exception: Throwable) = DatastoreError.exception(exception)
}
