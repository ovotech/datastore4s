package com.ovoenergy.datastore4s

import com.google.cloud.datastore.{BaseEntity, Entity}

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

case class Kind(name: String)

// TODO should ToEntity also be split out?? Does that make sense?
trait EntityFormat[EntityType, KeyType] extends FromEntity[EntityType] {
  val kind: Kind

  def toEntity(record: EntityType)(implicit keyFactorySupplier: () => com.google.cloud.datastore.KeyFactory): Entity

}

object EntityFormat {
  def apply[EntityType, KeyType](kind: String)(keyFunction: EntityType => KeyType): EntityFormat[EntityType, KeyType] = macro applyImpl[EntityType, KeyType]

  def applyImpl[EntityType: context.WeakTypeTag, KeyType: context.WeakTypeTag](context: Context)(kind: context.Expr[String])(keyFunction: context.Expr[EntityType => KeyType]): context.Expr[EntityFormat[EntityType, KeyType]] = {
    import context.universe._
    val helper = MacroHelper(context)

    val entityType = weakTypeTag[EntityType].tpe
    helper.requireCaseClass(entityType)

    val keyType = weakTypeTag[KeyType].tpe

    val keyExpression = // TODO figure out what is going on with the scala Long implicitness?? NoSuchMethod??? Have to use java.lang.Long temporarily.
      q"""val keyFactory = new com.ovoenergy.datastore4s.KeyFactoryFacade(keyFactorySupplier().setKind(kind.name))
          val key = implicitly[com.ovoenergy.datastore4s.ToKey[${keyType.typeSymbol}]].toKey($keyFunction(value), keyFactory)"""

    // TODO when wrapped in a monad for failures maybe replace with a for comprehension?
    // TODO One more abstractable here
    val builderExpressions = helper.caseClassFieldList(entityType).map { field =>
      val fieldName = field.asTerm.name
      q"""implicitly[com.ovoenergy.datastore4s.FieldFormat[${field.typeSignature.typeSymbol}]].addField(value.${fieldName}, ${fieldName.toString}, builder)"""
    }

    val toExpression =
      q"""override def toEntity(value: $entityType)(implicit keyFactorySupplier: () => com.google.cloud.datastore.KeyFactory): com.google.cloud.datastore.Entity = {
            ..$keyExpression
            val builder = com.google.cloud.datastore.Entity.newBuilder(key)
            ..$builderExpressions
            builder.build()
          }
        """

    val expression =
      q"""new com.ovoenergy.datastore4s.EntityFormat[$entityType, $keyType] {

            val kind = Kind($kind)

            private val fromEntity = ${FromEntity.applyImpl[EntityType](context)}

            override def fromEntity[E <:  com.google.cloud.datastore.BaseEntity[_]](entity: E): $entityType = {
              fromEntity.fromEntity(entity)
            }

            $toExpression
          }
        """
    context.info(context.enclosingPosition, expression.toString, false)
    context.Expr[EntityFormat[EntityType, KeyType]](
      expression
    )
  }
}

trait FromEntity[A] {
  def fromEntity[E <: BaseEntity[_]](entity: E): A
}

// TODO Should We write separate tests for FromEntity? Rather than relying implicitly to EntityFormat tests
object FromEntity {

  def apply[A](): FromEntity[A] = macro applyImpl[A]

  def applyImpl[A: context.WeakTypeTag](context: Context)(): context.Expr[FromEntity[A]] = {
    import context.universe._
    val helper = MacroHelper(context)

    val entityType = weakTypeTag[A].tpe
    helper.requireCaseClass(entityType)

    // TODO One more abstractable here
    val companion = entityType.typeSymbol.companion
    val companionNamedArguments = helper.caseClassFieldList(entityType).map { field =>
      AssignOrNamedArg(Ident(field.name), q"implicitly[com.ovoenergy.datastore4s.FieldFormat[${field.typeSignature.typeSymbol}]].fromField(entity, ${field.asTerm.name.toString})")
    }

    val expression =
      q"""new com.ovoenergy.datastore4s.FromEntity[$entityType] {
            override def fromEntity[E <:  com.google.cloud.datastore.BaseEntity[_]](entity: E): $entityType = {
              $companion.apply(
                ..$companionNamedArguments
              )
            }
          }
        """
    context.info(context.enclosingPosition, expression.toString, false)
    context.Expr[FromEntity[A]](
      expression
    )
  }

}
