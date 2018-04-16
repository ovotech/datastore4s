package com.ovoenergy.datastore4s

import scala.reflect.macros.blackbox

private[datastore4s] class MacroHelper[C <: blackbox.Context](val context: C) {

  import context.universe._
  import context.universe.{Type => Type}
  import context.universe.{Symbol => Symbol}
  import context.{Expr => Expr}

  def caseClassFieldList(tpe: Type): List[Symbol] = {
    val fields = tpe.typeSymbol.asClass.primaryConstructor.typeSignature.paramLists.flatten
    if (fields.isEmpty) {
      abort(s"Case class must have at least one field but $tpe did not contain any")
    }
    fields
  }

  def isObject(typeSymbol: Symbol): Boolean = typeSymbol.asClass.selfType.termSymbol.isModule

  def isCaseClass(tpe: Type): Boolean = tpe.typeSymbol.asClass.isCaseClass

  def isSealedTrait(tpe: Type): Boolean = {
    val classType = tpe.typeSymbol.asClass
    classType.isTrait && classType.isSealed
  }

  def subTypes(tpe: Type): Set[Symbol] =
    tpe.typeSymbol.asClass.knownDirectSubclasses

  def requireLiteral[A](expression: Expr[A], parameter: String): A = expression.tree match {
    case Literal(Constant(value)) => value.asInstanceOf[A] // Doesn't type check without cast.
    case _                        => abort(s"$parameter must be a literal")
  }

  def singletonObject(typeSymbol: Symbol) = typeSymbol.asClass.selfType.termSymbol.asModule

  def abort[A](error: String): A = context.abort(context.enclosingPosition, error)

  def sealedTraitCaseClassOrAbort[A](tpe: Type, sealedTraitExpression: => Expr[A], caseClassExpression: => Expr[A]): Expr[A] =
    if (isSealedTrait(tpe)) {
      sealedTraitExpression
    } else if (isCaseClass(tpe)) {
      caseClassExpression
    } else {
      abort(s"Type must either be a sealed trait or a case class but $tpe is not")
    }

  def fieldsMustExistInHierarchy(entityType: Type, ignoredIndexes: Set[String]): Unit =
    if (isSealedTrait(entityType)) {
      val missingFieldNames = subTypes(entityType)
        .map(_.typeSignature)
        .filter(isCaseClass)
        .map(missingFields(_, ignoredIndexes))
        .reduce(_ intersect _)
      errorIfFieldsMissing(entityType, missingFieldNames)
    } else if (isCaseClass(entityType)) {
      errorIfFieldsMissing(entityType, missingFields(entityType, ignoredIndexes))
    }

  private def missingFields(entityType: Type, ignoredIndexes: Set[String]): Set[String] = {
    val fieldNames = caseClassFieldList(entityType).map(_.name.toString)
    ignoredIndexes.foldLeft(Set.empty[String]) {
      case (missingFields, fieldName) => if (fieldNames.contains(fieldName)) missingFields else missingFields + fieldName
    }
  }

  private def errorIfFieldsMissing(entityType: Type, fields: Set[String]) =
    if (!fields.isEmpty) abort(s"Could not find fields: ${fields.mkString(", ")} in type hierarchy for $entityType")

  def indexesForSubtype(subType: Symbol, ignoredIndexes: Set[String]) = {
    val subTypeAsType = subType.typeSignature
    val fieldNames = if (isCaseClass(subTypeAsType)) caseClassFieldList(subTypeAsType).map(_.name.toString) else Seq.empty
    ignoredIndexes.filter(fieldNames.contains(_)).map(property => context.Expr[String](Literal(Constant(property))))
  }

}

private[datastore4s] object MacroHelper {
  def apply[C <: blackbox.Context](c: C): MacroHelper[c.type] =
    new MacroHelper[c.type](c)
}
