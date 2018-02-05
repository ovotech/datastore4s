package com.datastore4s.core

import com.google.cloud.datastore.Entity

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

case class Kind(name: String)

trait EntityFormat[EntityType, KeyType] {
  val kind: Kind

  def toEntity(record: EntityType)(implicit keyFactorySupplier: () => com.google.cloud.datastore.KeyFactory): Entity

  def fromEntity(entity: Entity): EntityType
}

object EntityFormat {
  def apply[EntityType, KeyType](kind: String)(keyFunction: EntityType => KeyType): EntityFormat[EntityType, KeyType] = macro applyImpl[EntityType, KeyType]

  def applyImpl[EntityType: context.WeakTypeTag, KeyType: context.WeakTypeTag](context: Context)(kind: context.Expr[String])(keyFunction: context.Expr[EntityType => KeyType]): context.Expr[EntityFormat[EntityType, KeyType]] = {
    import context.universe._

    val entityType = weakTypeTag[EntityType].tpe
    if(!entityType.typeSymbol.asClass.isCaseClass){ context.abort(context.enclosingPosition, s"Entity classes must be a case class but $entityType is not") }

    val keyType = weakTypeTag[KeyType].tpe

    val keyExpression = // TODO figure
      q"""val keyFactory = new com.datastore4s.core.KeyFactoryFacade(keyFactorySupplier().setKind(kind.name))
               implicitly[com.datastore4s.core.ToKey[${keyType.typeSymbol}]].toKey($keyFunction(value), keyFactory)"""

    // TODO this relies on entity mutation. Is this avoidable? If not is it acceptable??
    // TODO is there some way to store the format as val ${fieldName}Format = implicitly[FieldFormat[A]]
    // TODO can we remove the empty q"" in fold left?
    // TODO when wrapped in a monad for failures maybe replace with a for comprehension? Does it even matter? Does the generated code need to be nice to read? I would say so but not sure.
    val builderExpression = entityType.typeSymbol.asClass.primaryConstructor.typeSignature.paramLists.flatten.foldLeft(q"": context.universe.Tree) {
      case (expression, field) =>
        val fieldName = field.asTerm.name
        q"""$expression
            implicitly[com.datastore4s.core.FieldFormat[${field.typeSignature.typeSymbol}]].addField(value.${fieldName}, ${fieldName.toString}, builder)
          """
    }

    // TODO why does builder expression open a new scope??
    val toExpression =
      q"""override def toEntity(value: $entityType)(implicit keyFactorySupplier: () => com.google.cloud.datastore.KeyFactory): com.google.cloud.datastore.Entity = {
            val key = $keyExpression
            val builder = com.google.cloud.datastore.Entity.newBuilder(key)
            $builderExpression
            builder.build()
          }
        """

    // TODO can we store the implicit format?
    val constructionExpressions = entityType.typeSymbol.asClass.primaryConstructor.typeSignature.paramLists.flatten.map { field =>
      (q"implicitly[com.datastore4s.core.FieldFormat[${field.typeSignature.typeSymbol}]].fromField(entity, ${field.asTerm.name.toString})", field)
    }

    val args = constructionExpressions.map {
      case (expression, field) => AssignOrNamedArg(Ident(field.name), expression)
    }

    val companion = entityType.typeSymbol.companion

    val fromExpression =
      q"""override def fromEntity(entity: com.google.cloud.datastore.Entity): $entityType = {
            $companion.apply(..$args)
          }
        """

    val expression =
      q"""new com.datastore4s.core.EntityFormat[$entityType, $keyType] {

            val kind = Kind($kind)

            $toExpression

            $fromExpression
          }
        """
    context.info(context.enclosingPosition, expression.toString, false)
    context.Expr[EntityFormat[EntityType, KeyType]](
      expression
    )
  }
}
