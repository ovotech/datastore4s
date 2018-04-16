package com.ovoenergy.datastore4s

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait EntityFormat[EntityType, KeyType] extends FromEntity[EntityType] {
  val kind: Kind

  def key(record: EntityType): KeyType

  def toEntity(record: EntityType, builder: EntityBuilder): Entity

}

object EntityFormat {
  def apply[EntityType, KeyType](kind: String)(keyFunction: EntityType => KeyType): EntityFormat[EntityType, KeyType] =
    macro deriveFormatWithAllIndexes[EntityType, KeyType]

  def deriveFormatWithAllIndexes[EntityType: context.WeakTypeTag, KeyType: context.WeakTypeTag](
    context: blackbox.Context
  )(kind: context.Expr[String])(keyFunction: context.Expr[EntityType => KeyType]): context.Expr[EntityFormat[EntityType, KeyType]] =
    deriveFormat(context)(kind)(keyFunction)(Set.empty)

  def apply[EntityType, KeyType](kind: String,
                                 ignoredIndexes: String*)(keyFunction: EntityType => KeyType): EntityFormat[EntityType, KeyType] =
    macro deriveFormatWithIgnoredIndexes[EntityType, KeyType]

  def deriveFormatWithIgnoredIndexes[EntityType: context.WeakTypeTag, KeyType: context.WeakTypeTag](context: blackbox.Context)(
    kind: context.Expr[String],
    ignoredIndexes: context.Expr[String]*
  )(keyFunction: context.Expr[EntityType => KeyType]): context.Expr[EntityFormat[EntityType, KeyType]] =
    deriveFormat(context)(kind)(keyFunction)(ignoredIndexes.map(MacroHelper(context).requireLiteral(_, "ignoredIndexes")).toSet)

  def deriveFormat[EntityType: context.WeakTypeTag, KeyType: context.WeakTypeTag](context: blackbox.Context)(
    kind: context.Expr[String]
  )(keyFunction: context.Expr[EntityType => KeyType])(ignoredIndexes: Set[String]): context.Expr[EntityFormat[EntityType, KeyType]] = {
    import context.universe._
    val helper = MacroHelper(context)
    helper.requireLiteral(kind, "kind")
    val entityType = weakTypeTag[EntityType].tpe
    helper.fieldsMustExistInHierarchy(entityType, ignoredIndexes)

    helper.sealedTraitCaseClassOrAbort[EntityFormat[EntityType, KeyType]](
      entityType,
      sealedTraitFormat(context)(helper)(kind, keyFunction, ignoredIndexes),
      caseClassFormat(context)(helper)(kind, keyFunction, ignoredIndexes)
    )
  }

  private def sealedTraitFormat[EntityType: context.WeakTypeTag, KeyType: context.WeakTypeTag](
    context: blackbox.Context
  )(helper: MacroHelper[context.type])(kind: context.Expr[String],
                                       keyFunction: context.Expr[EntityType => KeyType],
                                       ignoredIndexes: Set[String]): context.Expr[EntityFormat[EntityType, KeyType]] = {
    import context.universe._
    val entityType = weakTypeTag[EntityType].tpe
    val keyType = weakTypeTag[KeyType].tpe
    val subTypes = helper.subTypes(entityType)

    val cases = subTypes.map { subType =>
      val indexes = helper.indexesForSubtype(subType, ignoredIndexes)
      cq"""e: ${subType.asClass} => EntityFormat[$subType, $keyType]($kind, ..$indexes)($keyFunction).toEntity(e, builder.addField(stringFormat.toEntityField("type", ${subType.name.toString})))"""
    }

    val toEntityExpression =
      q"""override def toEntity(value: $entityType, builder: EntityBuilder): Entity = value match {
             case ..$cases
           }
        """

    context.Expr[EntityFormat[EntityType, KeyType]](q"""import com.ovoenergy.datastore4s._

          new EntityFormat[$entityType, $keyType] {

            val kind = Kind($kind)

            private val stringFormat = implicitly[FieldFormat[String]]

            override def key(record: $entityType) = $keyFunction(record)

            override def fromEntity(entity: Entity): Either[DatastoreError, $entityType] = FromEntity[$entityType].fromEntity(entity)

            $toEntityExpression
          }
        """)
  }

  private def caseClassFormat[EntityType: context.WeakTypeTag, KeyType: context.WeakTypeTag](
    context: blackbox.Context
  )(helper: MacroHelper[context.type])(kind: context.Expr[String],
                                       keyFunction: context.Expr[EntityType => KeyType],
                                       ignoredIndexes: Set[String]): context.Expr[EntityFormat[EntityType, KeyType]] = {
    import context.universe._
    val entityType = weakTypeTag[EntityType].tpe
    val keyType = weakTypeTag[KeyType].tpe

    val fieldExpressions = helper.caseClassFieldList(entityType).map { field => // TODO fold
      val fieldName = field.asTerm.name
      val fieldExpression = q"""implicitly[FieldFormat[${field.typeSignature}]].toEntityField(${fieldName.toString}, value.$fieldName)"""
      if (ignoredIndexes.contains(fieldName.toString)) {
        q"$fieldExpression.ignoreIndexes"
      } else {
        fieldExpression
      }
    }

    val toEntityExpression =
      q"""override def toEntity(value: $entityType, builder: EntityBuilder): Entity = {
            Seq(..$fieldExpressions).foldLeft(builder){case (b, field) => b.addField(field)}.build()
          }
        """

    context.Expr[EntityFormat[EntityType, KeyType]](q"""import com.ovoenergy.datastore4s._

          new EntityFormat[$entityType, $keyType] {

            val kind = Kind($kind)

            override def key(record: $entityType) = $keyFunction(record)

            override def fromEntity(entity: Entity): Either[DatastoreError, $entityType] = FromEntity[$entityType].fromEntity(entity)

            $toEntityExpression
          }
        """)
  }
}

trait FromEntity[A] {
  def fromEntity(entity: Entity): Either[DatastoreError, A]
}

object FromEntity {

  def apply[A]: FromEntity[A] = macro applyImpl[A]

  def applyImpl[A: context.WeakTypeTag](context: blackbox.Context): context.Expr[FromEntity[A]] = {
    import context.universe._
    val helper = MacroHelper(context)

    val entityType = weakTypeTag[A].tpe
    helper.sealedTraitCaseClassOrAbort[FromEntity[A]](entityType, sealedTraitFormat(context)(helper), caseClassFormat(context)(helper))
  }

  private def sealedTraitFormat[A: context.WeakTypeTag](
    context: blackbox.Context
  )(helper: MacroHelper[context.type]): context.Expr[FromEntity[A]] = {
    import context.universe._
    val entityType = weakTypeTag[A].tpe
    val subTypes = helper.subTypes(entityType)
    val cases = subTypes.map { subType => // TODO should we allow objects here too one day?
      cq"""Right(${subType.name.toString}) => FromEntity[$subType].fromEntity(entity)"""
    }
    context.Expr[FromEntity[A]](q"""import com.ovoenergy.datastore4s._

          new FromEntity[$entityType] {
            private val stringFormat = implicitly[FieldFormat[String]]
            override def fromEntity(entity: Entity): Either[DatastoreError, $entityType] = stringFormat.fromEntityField("type", entity) match {
              case ..$cases
              case Right(other) => DatastoreError.error(s"Unknown subtype found: $$other")
              case Left(error) => Left(error)
            }
          }""")
  }

  private def caseClassFormat[A: context.WeakTypeTag](
    context: blackbox.Context
  )(helper: MacroHelper[context.type]): context.Expr[FromEntity[A]] = {
    import context.universe._
    val entityType = weakTypeTag[A].tpe
    val companion = entityType.typeSymbol.companion
    val fields = helper.caseClassFieldList(entityType)
    val companionNamedArguments = fields.map(field => AssignOrNamedArg(Ident(field.name), q"${field.asTerm.name}"))

    val fieldFormats = fields.map { field =>
      val fieldName = field.asTerm.name
      fq"""${field.name} <- implicitly[FieldFormat[${field.typeSignature}]].fromEntityField(${fieldName.toString}, entity)"""
    }

    context.Expr[FromEntity[A]](q"""import com.ovoenergy.datastore4s._

          new FromEntity[$entityType] {
            override def fromEntity(entity: Entity): Either[DatastoreError, $entityType] = {
              for (
                ..$fieldFormats
              ) yield $companion.apply(..$companionNamedArguments)
            }
          }
        """)
  }

}
