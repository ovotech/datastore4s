package com.ovoenergy.datastore4s

import com.ovoenergy.datastore4s.internal.{DatastoreError, Entity}

import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

case class Kind(name: String)

object Kind {
  val validationError: String = "A kind must not start with '__' or contain '/'"

  def isValid(kind: String): Boolean =
    !(kind.contains('/') || kind.startsWith("__"))

}

// TODO is it possible to extract and store types as fields rather than applying macros in macros??
trait EntityFormat[EntityType, KeyType] extends FromEntity[EntityType] {
  val kind: Kind

  // TODO split out create key??

  def toEntity(record: EntityType)(implicit keyFactorySupplier: () => com.google.cloud.datastore.KeyFactory): Entity

}

object EntityFormat {
  def apply[EntityType, KeyType](kind: String)(keyFunction: EntityType => KeyType): EntityFormat[EntityType, KeyType] =
    macro applyImpl[EntityType, KeyType]

  def applyImpl[EntityType: context.WeakTypeTag, KeyType: context.WeakTypeTag](
    context: Context
  )(kind: context.Expr[String])(keyFunction: context.Expr[EntityType => KeyType]): context.Expr[EntityFormat[EntityType, KeyType]] = {
    import context.universe._
    val helper = MacroHelper(context)

    val kindString = helper.literal(kind, "kind")
    if (!Kind.isValid(kindString)) {
      context.abort(context.enclosingPosition, Kind.validationError)
    }

    val entityType = weakTypeTag[EntityType].tpe
    val keyType = weakTypeTag[KeyType].tpe
    if (helper.isSealedTrait(entityType)) {
      val subTypes = helper.subTypes(entityType)

      val cases = subTypes.map { subType =>
        cq"""e: ${subType.asClass} => internal.WrappedBuilder(EntityFormat[$subType, $keyType]($kind)($keyFunction).toEntity(e)).addField("type", stringFormat.toValue(${subType.name.toString})).build()"""
      }

      val toExpression =
        q"""override def toEntity(value: $entityType)(implicit keyFactorySupplier: () => com.google.cloud.datastore.KeyFactory): internal.Entity = value match {
             case ..$cases
           }
        """

      context.Expr[EntityFormat[EntityType, KeyType]](q"""import com.ovoenergy.datastore4s._
          import com.ovoenergy.datastore4s.internal.ValueFormat
          import com.google.cloud.datastore.Entity

          new EntityFormat[$entityType, $keyType] {

            val kind = Kind($kind)

            private val stringFormat = implicitly[ValueFormat[String]]
            private val fromEntity = ${FromEntity
        .applyImpl[EntityType](context)}

            override def fromEntity(entity: internal.Entity): Either[internal.DatastoreError, $entityType] = {
              fromEntity.fromEntity(entity)
            }

            $toExpression
          }
        """)
    } else {

      helper.requireCaseClass(entityType)
      val keyExpression =
        q"""val keyFactory = new KeyFactoryFacade(keyFactorySupplier().setKind(kind.name))
          val key = implicitly[ToKey[${keyType.typeSymbol}]].toKey($keyFunction(value), keyFactory)"""

      // TODO One more abstractable here
      val builderExpressions = helper.caseClassFieldList(entityType).map { field =>
        val fieldName = field.asTerm.name
        q"""implicitly[FieldFormat[${field.typeSignature.typeSymbol}]].addField(value.${fieldName}, ${fieldName.toString}, builder)"""
      }

      // TODO Change builder to be immutable. Maybe put all values in Seq[DataStoreValue} and fold? Passing in a builder to the function would be nice
      val toExpression =
        q"""override def toEntity(value: $entityType)(implicit keyFactorySupplier: () => com.google.cloud.datastore.KeyFactory): internal.Entity = {
            ..$keyExpression
            val builder = internal.WrappedBuilder(Entity.newBuilder(key))
            ..$builderExpressions
            builder.build()
          }
        """

      context.Expr[EntityFormat[EntityType, KeyType]](q"""import com.ovoenergy.datastore4s._
          import com.google.cloud.datastore.Entity

          new EntityFormat[$entityType, $keyType] {

            val kind = Kind($kind)

            private val fromEntity = ${FromEntity
        .applyImpl[EntityType](context)}

            override def fromEntity(entity: internal.Entity): Either[internal.DatastoreError, $entityType] = {
              fromEntity.fromEntity(entity)
            }

            $toExpression
          }
        """)
    }
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
    if (helper.isSealedTrait(entityType)) {
      val subTypes = helper.subTypes(entityType)
      val cases = subTypes.map { subType =>
        cq"""Right(${subType.name.toString}) => FromEntity[$subType].fromEntity(entity)"""
      }
      context.Expr[FromEntity[A]](q"""import com.ovoenergy.datastore4s._

          new FromEntity[$entityType] {
            private val stringFormat = implicitly[FieldFormat[String]]
            override def fromEntity(entity: internal.Entity): Either[internal.DatastoreError, $entityType] = stringFormat.fromField(entity, "type") match {
              case ..$cases
              case Right(other) => internal.DatastoreError.error(s"Unknown subtype found: $$other")
              case Left(error) => Left(error)
            }
          }""")
    } else {
      helper.requireCaseClass(entityType)

      // TODO One more abstractable here
      val companion = entityType.typeSymbol.companion
      val fields = helper.caseClassFieldList(entityType)
      val companionNamedArguments = fields.map(field => AssignOrNamedArg(Ident(field.name), q"${field.asTerm.name}"))

      val fieldFormats = fields.map { field =>
        val fieldName = field.asTerm.name
        fq"""${field.name} <- implicitly[FieldFormat[${field.typeSignature.typeSymbol}]].fromField(entity, ${fieldName.toString})"""
      }

      context.Expr[FromEntity[A]](q"""import com.ovoenergy.datastore4s._

          new FromEntity[$entityType] {
            override def fromEntity(entity: internal.Entity): Either[internal.DatastoreError, $entityType] = {
              for (
                ..$fieldFormats
              ) yield $companion.apply(..$companionNamedArguments)
            }
          }
        """)
    }
  }

}
