package com.datastore4s.core

import com.google.cloud.datastore.{Entity, Key, PathElement}

trait AsKey[A] {

  def toKey(value: A, keyFactory: KeyFactory): Key

  def fromKey(key: Key): A

}

object AsKey {

  implicit object StringAsKey extends AsKey[String] {
    override def toKey(value: String, keyFactory: KeyFactory): Key = keyFactory.buildWithName(value)

    override def fromKey(key: Key): String = key.getName
  }

  implicit object LongAsKey extends AsKey[Long] {
    override def toKey(value: Long, keyFactory: KeyFactory): Key = keyFactory.buildWithId(value)

    override def fromKey(key: Key): Long = key.getId
  }

}


trait KeyFactory {

  def addStringAncestor(value: String, kind: String): KeyFactory

  def addLongAncestor(value: Long, kind: String): KeyFactory

  def buildWithName(name: String): Key

  def buildWithId(id: Long): Key

}

class KeyFactoryFacade(val factory: com.google.cloud.datastore.KeyFactory) extends KeyFactory {
  override def buildWithName(name: String) = factory.newKey(name)

  override def buildWithId(id: Long) = factory.newKey(id)

  def addStringAncestor(value: String, kind: String) = {
    new KeyFactoryFacade(factory.addAncestor(PathElement.of(kind, value)))
  }

  def addLongAncestor(value: Long, kind: String) = {
    new KeyFactoryFacade(factory.addAncestor(PathElement.of(kind, value)))
  }
}

object Ancestors {
  import scala.collection.JavaConverters._
  def stringAncestorFrom(kind: String, key: Key): Option[String] = {
    ancestorsOfKind(kind, key).headOption.map(_.getName)
  }

  def mandatoryStringAncestorFrom(kind: String, key: Key): String = stringAncestorFrom(kind, key) match {
    case Some(s) => s
    case None => sys.error(s"Could not find mandatory string ancestor of kind $kind from key $key")
  }

  def longAncestorFrom(kind: String, key: Key): Option[Long] = {
    ancestorsOfKind(kind, key).headOption.map(_.getId)
  }

  def mandatoryLongAncestorFrom(kind: String, key: Key): Long = longAncestorFrom(kind, key) match {
    case Some(l) => l
    case None => sys.error(s"Could not find mandatory long ancestor of kind $kind from key $key")
  }

  private def ancestorsOfKind(kind: String, key: Key) = {
    key.getAncestors.asScala.filter(_.getKind == kind)
  }
}
