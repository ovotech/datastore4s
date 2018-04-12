package com.ovoenergy.datastore4s
import java.io.{PrintStream, PrintWriter}

sealed trait DatastoreError

final case class DatastoreException(exception: Throwable) extends DatastoreError

final case class DeserialisationError(error: String) extends DatastoreError

final case class ComposedError(errors: Seq[DatastoreError]) extends DatastoreError

object DatastoreError { // TODO custom flatmapping that will concat all errors together. Possibly a DatastoreResult? Kind of like Validation[A]. Need to be able to add all errors together or return (A,B)
  def missingField[A](fieldName: String, entity: Entity): Either[DatastoreError, A] =
    Left(DeserialisationError(s"Field $fieldName could not be found on entity $entity"))

  def wrongType[A](expectedType: DsType, datastoreValue: DatastoreValue): Either[DatastoreError, A] =
    Left(DeserialisationError(s"Expected a $expectedType but got $datastoreValue"))

  def error[A](error: String): Either[DatastoreError, A] =
    Left(DeserialisationError(error))

  def exception[A](exception: Throwable): Either[DatastoreError, A] =
    Left(DatastoreException(exception))

  def sequence[A](values: Seq[Either[DatastoreError, A]]): Either[DatastoreError, Seq[A]] =
    values.reverse.foldLeft(Right(Seq.empty): Either[ComposedError, Seq[A]]) {
      case (Right(acc), Right(value))    => Right(value +: acc)
      case (Left(errorAcc), Left(error)) => Left(ComposedError(error +: errorAcc.errors))
      case (Right(_), Left(error))       => Left(ComposedError(Seq(error)))
      case (Left(errorAcc), Right(_))    => Left(errorAcc)
    }

  def asException(error: DatastoreError): Throwable = error match {
    case DatastoreException(exception) => exception
    case DeserialisationError(error)   => new RuntimeException(error)
    case ComposedError(errors)         => ComposedException(errors.map(asException))
  }

  final case class ComposedException(throwables: Seq[Throwable]) extends Exception {

    override def getMessage: String = throwables.map(_.getMessage).mkString("\n\n")

    override def printStackTrace(s: PrintWriter): Unit = throwables.foreach(_.printStackTrace(s))

    override def printStackTrace(s: PrintStream): Unit = throwables.foreach(_.printStackTrace(s))
  }

}

trait DatastoreErrors {
  def missingField[A](fieldName: String, entity: Entity): Either[DatastoreError, A] =
    DatastoreError.missingField(fieldName, entity)

  def wrongType[A](expectedType: DsType, datastoreValue: DatastoreValue): Either[DatastoreError, A] =
    DatastoreError.wrongType(expectedType, datastoreValue)

  def error[A](error: String): Either[DatastoreError, A] =
    DatastoreError.error(error)

  def exception[A](exception: Throwable): Either[DatastoreError, A] = DatastoreError.exception(exception)
}
