package com.ovoenergy.datastore4s

import scala.reflect.macros.blackbox.Context

private[datastore4s] class MacroHelper[C <: Context](val context: C) {

  def requireCaseClass(tpe: context.universe.Type) = {
    if (!tpe.typeSymbol.asClass.isCaseClass) {
      context.abort(context.enclosingPosition, s"Required case class but $tpe is not a case class")
    }
  }

  def caseClassFieldList(tpe: context.universe.Type) = {
    tpe.typeSymbol.asClass.primaryConstructor.typeSignature.paramLists.flatten
  }

  def isSealedTrait(tpe: context.universe.Type) = {
    val classType = tpe.typeSymbol.asClass
    classType.isTrait && classType.isSealed
  }

  def subTypes(tpe: context.universe.Type) = {
    tpe.typeSymbol.asClass.knownDirectSubclasses
  }

}

private[datastore4s] object MacroHelper {
  def apply[C <: Context](c: C): MacroHelper[c.type] = new MacroHelper[c.type](c)
}

