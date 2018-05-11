package com.ovoenergy.datastore4s

trait ToKey[A, K <: Key] {
  def toKey(value: A): K
}

trait ToNamedKey[A] extends ToKey[A, NamedKey]
trait ToIdKey[A] extends ToKey[A, IdKey]

object ToKey {

  implicit object StringToKey extends ToNamedKey[String] {
    override def toKey(value: String) = NamedKey(value)
  }

  type JavaLong = java.lang.Long

  implicit object LongToKey extends ToIdKey[JavaLong] {
    override def toKey(value: JavaLong) = IdKey(value)
  }

}

sealed trait Key

case class NamedKey(name: String, ancestor: Option[Ancestor] = None) extends Key
object NamedKey {
  def apply[A](name: String, ancestor: A)(implicit toAncestor: ToAncestor[A]): NamedKey =
    NamedKey(name, Some(toAncestor.toAncestor(ancestor)))
}

case class IdKey(id: Long, ancestor: Option[Ancestor] = None) extends Key
object IdKey {
  def apply[A](id: Long, ancestor: A)(implicit toAncestor: ToAncestor[A]): IdKey =
    IdKey(id, Some(toAncestor.toAncestor(ancestor)))
}


sealed trait ToAncestor[A] {
  def toAncestor(value: A): Ancestor
}

object ToAncestor {

  def toStringAncestor[A](kind: String)(f: A => String): ToAncestor[A] = new ToAncestor[A] {
    private val validKind = Kind(kind)
    override def toAncestor(value: A) = new StringAncestor(validKind, f(value))
  }

  def toLongAncestor[A](kind: String)(f: A => Long): ToAncestor[A] = new ToAncestor[A] {
    private val validKind = Kind(kind)
    override def toAncestor(value: A) = new LongAncestor(validKind, f(value))
  }

}

sealed trait Ancestor

final case class StringAncestor(kind: Kind, name: String) extends Ancestor

final case class LongAncestor(kind: Kind, id: Long) extends Ancestor
