package com.datastore4s.core

import com.google.cloud.datastore.{Key, PathElement}

trait ToKey[A] {
  def toKey(value: A, keyFactory: KeyFactory): Key
}

object ToKey {

  implicit object StringToKey extends ToKey[String] {
    override def toKey(value: String, keyFactory: KeyFactory): Key = keyFactory.buildWithName(value)
  }

  implicit object LongToKey extends ToKey[Long] {
    override def toKey(value: Long, keyFactory: KeyFactory): Key = keyFactory.buildWithId(value)
  }

}


trait KeyFactory {

  def addAncestor(ancestor: Ancestor): KeyFactory

  def addAncestor[A](value: A)(implicit toAncestor: ToAncestor[A]): KeyFactory

  def buildWithName(name: String): Key

  def buildWithId(id: Long): Key

}

class KeyFactoryFacade(val factory: com.google.cloud.datastore.KeyFactory) extends KeyFactory {
  override def buildWithName(name: String) = factory.newKey(name)

  override def buildWithId(id: Long) = factory.newKey(id)

  override def addAncestor(ancestor: Ancestor) = ancestor match {
    case StringAncestor(kind, name) => new KeyFactoryFacade(factory.addAncestor(PathElement.of(kind.kind, name)))
    case LongAncestor(kind, id) => new KeyFactoryFacade(factory.addAncestor(PathElement.of(kind.kind, id)))
  }

  override def addAncestor[A](value: A)(implicit toAncestor: ToAncestor[A]) = addAncestor(toAncestor(value))
}

sealed trait Ancestor

case class StringAncestor(kind: Kind, name: String) extends Ancestor

case class LongAncestor(kind: Kind, id: Long) extends Ancestor

trait ToAncestor[A] extends (A => Ancestor)
