package com.ovoenergy.datastore4s

import com.google.cloud.datastore.{Datastore, Key, PathElement}

trait ToKey[A] {
  def toKey(value: A, keyFactory: KeyFactory): Key
}

object ToKey {

  implicit object StringToKey extends ToKey[String] {
    override def toKey(value: String, keyFactory: KeyFactory): Key =
      keyFactory.buildWithName(value)
  }

  // TODO move to pakage
  type JavaLong = java.lang.Long
  implicit object LongToKey extends ToKey[JavaLong] {
    override def toKey(value: JavaLong, keyFactory: KeyFactory): Key =
      keyFactory.buildWithId(value)
  }

}

trait KeyFactory {

  def addAncestor[A](value: A)(implicit toAncestor: ToAncestor[A]): KeyFactory

  def buildWithName(name: String): Key

  def buildWithId(id: Long): Key

}

class KeyFactoryFacade(val factory: com.google.cloud.datastore.KeyFactory) extends KeyFactory {
  override def buildWithName(name: String) = factory.newKey(name)

  override def buildWithId(id: Long) = factory.newKey(id)

  override def addAncestor[A](value: A)(implicit toAncestor: ToAncestor[A]): KeyFactory =
    toAncestor.toAncestor(value) match {
      case StringAncestor(kind, name) =>
        new KeyFactoryFacade(factory.addAncestor(PathElement.of(kind.name, name)))
      case LongAncestor(kind, id) =>
        new KeyFactoryFacade(factory.addAncestor(PathElement.of(kind.name, id)))
    }
}

object KeyFactoryFacade {
  def apply(datastore: Datastore, kind: Kind): KeyFactoryFacade =
    new KeyFactoryFacade(datastore.newKeyFactory().setKind(kind.name))
}

sealed trait Ancestor

private case class StringAncestor(kind: Kind, name: String) extends Ancestor

private case class LongAncestor(kind: Kind, id: Long) extends Ancestor

trait ToAncestor[A] {
  def toAncestor(value: A): Ancestor
}

object ToAncestor {
  // TODO should these calls be macros to allow kind validation?
  def toStringAncestor[A](kind: String)(f: A => String): ToAncestor[A] =
    a => StringAncestor(Kind(kind), f(a))

  def toLongAncestor[A](kind: String)(f: A => Long): ToAncestor[A] =
    a => LongAncestor(Kind(kind), f(a))
}
