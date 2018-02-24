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

class KeyFactoryFacade(val factory: com.google.cloud.datastore.KeyFactory) extends KeyFactory {
  import com.ovoenergy.datastore4s.ToAncestor.{LongAncestor, StringAncestor}
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

trait ToAncestor[A] {
  def toAncestor(value: A): Ancestor
}

object ToAncestor {

  def toStringAncestor[A](kind: String)(f: A => String): ToAncestor[A] =
    a => new StringAncestor(Kind(kind), f(a))

  def toLongAncestor[A](kind: String)(f: A => Long): ToAncestor[A] =
    a => new LongAncestor(Kind(kind), f(a))

  private[datastore4s] class StringAncestor(val kind: Kind, val name: String) extends Ancestor {
    override def equals(obj: scala.Any): Boolean = obj match {
      case StringAncestor(thatKind, thatName) => thatKind == kind && thatName == name
      case _                                  => false
    }
  }

  private[datastore4s] object StringAncestor {
    def unapply(arg: StringAncestor): Option[(Kind, String)] = Some(arg.kind, arg.name)
  }

  private[datastore4s] class LongAncestor(val kind: Kind, val id: Long) extends Ancestor {
    override def equals(obj: scala.Any): Boolean = obj match {
      case LongAncestor(thatKind, thatId) => thatKind == kind && thatId == id
      case _                              => false
    }
  }

  private[datastore4s] object LongAncestor {
    def unapply(arg: LongAncestor): Option[(Kind, Long)] = Some(arg.kind, arg.id)
  }
}
