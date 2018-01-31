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


trait KeyFactory { // TODO keyfactory tests.

  def addStringAncestor(value: String, kind: Kind): KeyFactory

  def addLongAncestor(value: Long, kind: Kind): KeyFactory

  def buildWithName(name: String): Key

  def buildWithId(id: Long): Key

}

class KeyFactoryFacade(val factory: com.google.cloud.datastore.KeyFactory) extends KeyFactory {
  override def buildWithName(name: String) = factory.newKey(name)

  override def buildWithId(id: Long) = factory.newKey(id)

  def addStringAncestor(value: String, kind: Kind) = {
    new KeyFactoryFacade(factory.addAncestor(PathElement.of(kind.kind, value)))
  }

  def addLongAncestor(value: Long, kind: Kind) = {
    new KeyFactoryFacade(factory.addAncestor(PathElement.of(kind.kind, value)))
  }
}
