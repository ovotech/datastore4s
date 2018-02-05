package com.datastore4s.core

import com.google.cloud.datastore.{Datastore, Key, PathElement}

trait ToKey[A] {
  def toKey(value: A, keyFactory: KeyFactory): Key
}

object ToKey {

  implicit object StringToKey extends ToKey[String] {
    override def toKey(value: String, keyFactory: KeyFactory): Key = keyFactory.buildWithName(value)
  }

  // TODO find out why resolving just normal Long does not work
  implicit object LongToKey extends ToKey[java.lang.Long] {
    override def toKey(value: java.lang.Long, keyFactory: KeyFactory): Key = keyFactory.buildWithId(value)
  }

}


trait KeyFactory {

  def addAncestor(ancestor: Ancestor): KeyFactory

  def addAncestor[A](value: A)(implicit toAncestor: ToAncestor[A]): KeyFactory = addAncestor(toAncestor.toAncestor(value))

  def buildWithName(name: String): Key

  def buildWithId(id: Long): Key

}

class KeyFactoryFacade(val factory: com.google.cloud.datastore.KeyFactory) extends KeyFactory {
  override def buildWithName(name: String) = factory.newKey(name)

  override def buildWithId(id: Long) = factory.newKey(id)

  override def addAncestor(ancestor: Ancestor) = ancestor match {
    case StringAncestor(kind, name) => new KeyFactoryFacade(factory.addAncestor(PathElement.of(kind.name, name)))
    case LongAncestor(kind, id) => new KeyFactoryFacade(factory.addAncestor(PathElement.of(kind.name, id)))
  }
}

object KeyFactoryFacade {
  def apply(datastore: Datastore, kind: Kind): KeyFactoryFacade = new KeyFactoryFacade(datastore.newKeyFactory().setKind(kind.name))
}

sealed trait Ancestor

case class StringAncestor(kind: Kind, name: String) extends Ancestor

case class LongAncestor(kind: Kind, id: Long) extends Ancestor

trait ToAncestor[A] {
  def toAncestor(value: A): Ancestor
}

object ToAncestor {
  def toStringAncestor[A](kind: String)(f: A => String): ToAncestor[A] = a => StringAncestor(Kind(kind), f(a))

  def toLongAncestor[A](kind: String)(f: A => Long): ToAncestor[A] = a => LongAncestor(Kind(kind), f(a))
}

