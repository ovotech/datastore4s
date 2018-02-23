package com.ovoenergy.datastore4s

import scala.reflect.macros.blackbox.Context

private[datastore4s] class MacroHelper[C <: Context](val context: C) {
  import context.universe._


  def requireCaseClass(tpe: context.universe.Type) =
    if (!isCaseClass(tpe)) {
      context.abort(context.enclosingPosition, s"Required case class but $tpe is not a case class")
    }

  def caseClassFieldList(tpe: context.universe.Type) =
    tpe.typeSymbol.asClass.primaryConstructor.typeSignature.paramLists.flatten

  def isCaseClass(tpe: context.universe.Type) = tpe.typeSymbol.asClass.isCaseClass

  def isSealedTrait(tpe: context.universe.Type) = {
    val classType = tpe.typeSymbol.asClass
    classType.isTrait && classType.isSealed
  }

  def subTypes(tpe: context.universe.Type) =
    tpe.typeSymbol.asClass.knownDirectSubclasses

  def requireLiteral[A](expression: context.Expr[A], parameter: String) = expression.tree match {
    case Literal(Constant(_)) => ()
    case _ => context.abort(context.enclosingPosition, s"$parameter must be a literal")
  }

}

private[datastore4s] object MacroHelper {
  def apply[C <: Context](c: C): MacroHelper[c.type] =
    new MacroHelper[c.type](c)
}
