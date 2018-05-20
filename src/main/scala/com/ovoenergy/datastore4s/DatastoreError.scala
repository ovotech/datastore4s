package com.ovoenergy.datastore4s
import java.io.{PrintStream, PrintWriter}

/** ADT representing a failure in a datastore operation, can be turned into a Throwable using asThrowable */
sealed trait DatastoreError {
  def asThrowable = DatastoreError.asThrowable(this)
}

final case class DatastoreException(exception: Throwable) extends DatastoreError

final case class DeserialisationError(error: String) extends DatastoreError

final case class FieldError(fieldName: String, error: DatastoreError) extends DatastoreError

final case class ComposedError(errors: Seq[DatastoreError]) extends DatastoreError

object DatastoreError { // TODO Refactor to use Validation[A] style code instead of Either[Error, A] so that we can add all errors together
  def missingField[A](fieldName: String, entity: Entity): Either[DatastoreError, A] =
    Left(DeserialisationError(s"Field $fieldName could not be found on entity $entity"))

  def wrongType[A](expectedType: DsType, datastoreValue: DatastoreValue): Either[DatastoreError, A] =
    Left(DeserialisationError(s"Expected a $expectedType but got $datastoreValue"))

  def deserialisationError[A](error: String): Either[DatastoreError, A] =
    Left(DeserialisationError(error))

  def exception[A](exception: Throwable): Either[DatastoreError, A] =
    Left(DatastoreException(exception))

  def errorInField(fieldName: String)(existingError: DatastoreError): DatastoreError = FieldError(fieldName, existingError)

  def sequence[A](values: Seq[Either[DatastoreError, A]]): Either[DatastoreError, Seq[A]] =
    values.reverse.foldLeft(Right(Seq.empty): Either[ComposedError, Seq[A]]) {
      case (Right(acc), Right(value))    => Right(value +: acc)
      case (Left(errorAcc), Left(error)) => Left(ComposedError(error +: errorAcc.errors))
      case (Right(_), Left(error))       => Left(ComposedError(Seq(error)))
      case (Left(errorAcc), Right(_))    => Left(errorAcc)
    }

  def asThrowable(error: DatastoreError): Throwable = error match {
    case DatastoreException(exception)      => exception
    case FieldError(fieldName, cause)       => SuppressedStackTrace(s"Could not read the field: $fieldName", asThrowable(cause))
    case DeserialisationError(errorMessage) => new RuntimeException(errorMessage)
    case ComposedError(errors)              => ComposedException(errors.map(asThrowable))
  }

  final case class ComposedException(throwables: Seq[Throwable]) extends RuntimeException {

    override def getMessage: String = throwables.map(_.getMessage).mkString("\n\n")

    override def printStackTrace(s: PrintWriter): Unit = throwables.foreach(_.printStackTrace(s))

    override def printStackTrace(s: PrintStream): Unit = throwables.foreach(_.printStackTrace(s))
  }

  final case class SuppressedStackTrace(error: String, cause: Throwable)
      extends RuntimeException(s"$error\n${cause.getMessage}", cause, false, false)

}

trait DatastoreErrors {
  def missingField[A](fieldName: String, entity: Entity): Either[DatastoreError, A] =
    DatastoreError.missingField(fieldName, entity)

  def wrongType[A](expectedType: DsType, datastoreValue: DatastoreValue): Either[DatastoreError, A] =
    DatastoreError.wrongType(expectedType, datastoreValue)

  def deserialisationError[A](error: String): Either[DatastoreError, A] =
    DatastoreError.deserialisationError(error)

  def exception[A](exception: Throwable): Either[DatastoreError, A] = DatastoreError.exception(exception)
}
