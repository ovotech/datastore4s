package com.ovoenergy.datastore4s

import com.ovoenergy.datastore4s.internal.{DatastoreError, Entity}

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

case class Kind(name: String)

trait EntityFormat[EntityType, KeyType] extends FromEntity[EntityType] {
  val kind: Kind

  // TODO split out create key??

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
      q"""val keyFactory = new KeyFactoryFacade(keyFactorySupplier().setKind(kind.name))
          val key = implicitly[ToKey[${keyType.typeSymbol}]].toKey($keyFunction(value), keyFactory)"""

    // TODO One more abstractable here
    val builderExpressions = helper.caseClassFieldList(entityType).map { field =>
      val fieldName = field.asTerm.name
      q"""implicitly[FieldFormat[${field.typeSignature.typeSymbol}]].addField(value.${fieldName}, ${fieldName.toString}, builder)"""
    }

    // TODO Change builder to be immutable. Maybe put all values in Seq[DataStoreValue} and fold?
    val toExpression =
      q"""override def toEntity(value: $entityType)(implicit keyFactorySupplier: () => com.google.cloud.datastore.KeyFactory): internal.Entity = {
            ..$keyExpression
            val builder = internal.WrappedBuilder(Entity.newBuilder(key))
            ..$builderExpressions
            builder.build()
          }
        """

    val expression =
      q"""import com.ovoenergy.datastore4s._
          import com.google.cloud.datastore.Entity

          new EntityFormat[$entityType, $keyType] {

            val kind = Kind($kind)

            private val fromEntity = ${FromEntity.applyImpl[EntityType](context)}

            override def fromEntity(entity: internal.Entity): Either[internal.DatastoreError, $entityType] = {
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
  def fromEntity(entity: Entity): Either[DatastoreError, A]
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
    val fields = helper.caseClassFieldList(entityType)
    val companionNamedArguments = fields.map(field =>AssignOrNamedArg(Ident(field.name), q"${field.asTerm.name}"))

    val fieldFormats = fields.map { field =>
      val fieldName = field.asTerm.name
      fq"""${field.name} <- implicitly[FieldFormat[${field.typeSignature.typeSymbol}]].fromField(entity, ${fieldName.toString})"""
    }

    val expression =
      q"""import com.ovoenergy.datastore4s._

          new FromEntity[$entityType] {
            override def fromEntity(entity: internal.Entity): Either[internal.DatastoreError, $entityType] = {
              for (
                ..$fieldFormats
              ) yield $companion.apply(..$companionNamedArguments)
            }
          }
        """
    context.info(context.enclosingPosition, expression.toString, false)
    context.Expr[FromEntity[A]](
      expression
    )
  }

}
