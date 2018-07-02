package com.ovoenergy.datastore4s

import scala.annotation.StaticAnnotation

final case class SubTypeName(name: String) extends StaticAnnotation

final case class DatastoreFieldName(name: String) extends StaticAnnotation
