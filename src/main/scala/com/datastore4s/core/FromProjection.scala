package com.datastore4s.core

import com.google.cloud.datastore.ProjectionEntity

import scala.reflect.macros.blackbox.Context
import scala.language.experimental.macros
import scala.util.Try

trait FromProjection[A] { // TODO is there a way to get around this existing?
  def fromProjection(entity: ProjectionEntity): Try[A]
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
            override def fromEntity(entity: com.google.cloud.datastore.ProjectionEntity): scala.util.Try[$projectionType] = scala.util.Try {
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
