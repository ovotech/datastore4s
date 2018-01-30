package com.datastore4s.core

import scala.annotation.Annotation

final case class Kind(kind:String) extends Annotation

final case class EntityKey() extends Annotation
