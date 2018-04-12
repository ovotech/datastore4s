package com.ovoenergy.datastore4s

import com.google.cloud.datastore.{Key, PathElement}

trait ToKey[A] {
  def toKey(value: A, keyFactory: KeyFactory): Key
}

object ToKey {

  implicit object StringToKey extends ToKey[String] {
    override def toKey(value: String, keyFactory: KeyFactory): Key =
      keyFactory.buildWithName(value)
  }

  type JavaLong = java.lang.Long

  implicit object LongToKey extends ToKey[JavaLong] {
    override def toKey(value: JavaLong, keyFactory: KeyFactory): Key =
      keyFactory.buildWithId(value)
  }

}

sealed trait KeyFactory {

  def addAncestor[A](value: A)(implicit toAncestor: ToAncestor[A]): KeyFactory

  def buildWithName(name: String): Key

  def buildWithId(id: Long): Key

}

private[datastore4s] class KeyFactoryFacade(private val factory: com.google.cloud.datastore.KeyFactory) extends KeyFactory {

  override def buildWithName(name: String): Key = factory.newKey(name)

  override def buildWithId(id: Long): Key = factory.newKey(id)

  override def addAncestor[A](value: A)(implicit toAncestor: ToAncestor[A]): KeyFactory =
    toAncestor.toAncestor(value) match {
      case StringAncestor(kind, name) =>
        new KeyFactoryFacade(factory.addAncestor(PathElement.of(kind.name, name)))
      case LongAncestor(kind, id) =>
        new KeyFactoryFacade(factory.addAncestor(PathElement.of(kind.name, id)))
    }
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
