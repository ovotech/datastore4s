package com.datastore4s.core

import com.google.cloud.datastore.ProjectionEntity

import scala.reflect.macros.blackbox.Context
import scala.language.experimental.macros

trait FromProjection[A] {
  // TODO is there a way to get around this existing? It seems unecessary to have this just for the sake of having Projections?
  // Perhaps split out FromEntity[A] { def fromEntity[E <: BaseEntity[_}](entity:E) } and EntityFormat[E, K] extends FromEntity[E] Then have this be from entity and EntityFormat.apply references this?
  def fromProjection(entity: ProjectionEntity): A
}

object FromProjection {

  def apply[A](): FromProjection[A] = macro applyImpl[A]

  def applyImpl[A: context.WeakTypeTag](context: Context)(): context.Expr[FromProjection[A]] = {
    import context.universe._

    val projectionType = weakTypeTag[A].tpe
    require(projectionType.typeSymbol.asClass.isCaseClass, s"Projection classes must be a case class but $projectionType is not")

    // TODO can we store the implicit format?
    val constructionExpressions = projectionType.typeSymbol.asClass.primaryConstructor.typeSignature.paramLists.flatten.map { field =>
      (q"implicitly[com.datastore4s.core.FieldFormat[${field.typeSignature.typeSymbol}]].fromField(entity, ${field.asTerm.name.toString})", field)
    }

    val args = constructionExpressions.map {
      case (expression, field) => AssignOrNamedArg(Ident(field.name), expression)
    }

    val companion = projectionType.typeSymbol.companion

    val expression =
      q"""new com.datastore4s.core.FromProjection[$projectionType] {
            override def fromProjection(entity: com.google.cloud.datastore.ProjectionEntity): $projectionType = {
              $companion.apply(..$args)
            }
          }
        """
    println(expression)
    context.Expr[FromProjection[A]](
      expression
    )
  }

}
