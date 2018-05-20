package com.ovoenergy.datastore4s

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/** Representation of an action to be performed by a datastore service */
final case class DatastoreOperation[+A](op: DatastoreService => Either[DatastoreError, A]) {

  def map[B](f: A => B): DatastoreOperation[B] = DatastoreOperation(ds => op(ds).map(f))

  def flatMapEither[B](f: A => Either[DatastoreError, B]): DatastoreOperation[B] = DatastoreOperation(ds => op(ds).flatMap(f))

  def flatMap[B](f: A => DatastoreOperation[B]): DatastoreOperation[B] = DatastoreOperation(ds => op(ds).map(f).flatMap(_.op(ds)))

}

object DatastoreOperation extends DatastoreErrors {
  def pure[A](value: A): DatastoreOperation[A] = DatastoreOperation(_ => Right(value))
  def failure[A](error: String): DatastoreOperation[A] = DatastoreOperation(_ => exception(new RuntimeException(error)))
}

object DatastoreOperationInterpreter {

  def run[A](operation: DatastoreOperation[A])(implicit datastoreService: DatastoreService): Either[DatastoreError, A] =
    operation.op(datastoreService)

  def runF[A](operation: DatastoreOperation[A])(implicit datastoreService: DatastoreService): Try[A] = run(operation) match {
    case Right(a)    => Success(a)
    case Left(error) => Failure(DatastoreError.asThrowable(error))
  }

  def runAsync[A](operation: DatastoreOperation[A])(implicit executionContext: ExecutionContext,
                                                    datastoreService: DatastoreService): Future[Either[DatastoreError, A]] =
    Future(run(operation))

  def runAsyncF[A](operation: DatastoreOperation[A])(implicit executionContext: ExecutionContext,
                                                     datastoreService: DatastoreService): Future[A] =
    runAsync(operation).flatMap {
      case Right(a)    => Future.successful(a)
      case Left(error) => Future.failed(DatastoreError.asThrowable(error))
    }
}
