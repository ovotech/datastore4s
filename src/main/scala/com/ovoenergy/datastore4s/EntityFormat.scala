package com.ovoenergy.datastore4s

import scala.language.experimental.macros
import scala.reflect.macros.blackbox

trait EntityFormat[EntityType, KeyType] extends FromEntity[EntityType] with ToEntityComponents[EntityType, KeyType] {

  /** The kind under which to store entities */
  val kind: Kind
  // TODO remove old methods and change macro

  /** Create the datastore key for the entity */
  @deprecated(message = "Replaced with toEntityComponents for composability. Will be removed in 0.3.0", since = "0.2.1")
  def key(record: EntityType): KeyType

  @deprecated(message = "Replaced with toEntityComponents for composability. Will be removed in 0.3.0", since = "0.2.1")
  def toEntity(record: EntityType, builder: EntityBuilder): Entity

  override def toEntityComponents(record: EntityType): EntityComponents[EntityType, KeyType] =
    new EntityComponents(kind, key(record), toEntity(record, _))

}

// TODO in the future should builderFunction just be entityFields?
class EntityComponents[E, K](val kind: Kind, val key: K, val builderFunction: EntityBuilder => Entity)

trait ToEntityComponents[E, K] {
  def toEntityComponents(record: E): EntityComponents[E, K]
}

object EntityFormat {

  def apply[EntityType, KeyType](kind: String)(keyFunction: EntityType => KeyType): EntityFormat[EntityType, KeyType] =
    macro deriveFormatWithAllIndexes[EntityType, KeyType]

  def ignoreIndexes[EntityType, KeyType](
    ignoredIndexes: String*
  )(kind: String)(keyFunction: EntityType => KeyType): EntityFormat[EntityType, KeyType] =
    macro deriveFormatWithIgnoredIndexes[EntityType, KeyType]

  def onlyIndex[EntityType, KeyType](
    onlyIndex: String*
  )(kind: String)(keyFunction: EntityType => KeyType): EntityFormat[EntityType, KeyType] =
    macro deriveFormatOnlyIndexing[EntityType, KeyType]

  def deriveFormatWithAllIndexes[EntityType: context.WeakTypeTag, KeyType: context.WeakTypeTag](
    context: blackbox.Context
  )(kind: context.Expr[String])(keyFunction: context.Expr[EntityType => KeyType]): context.Expr[EntityFormat[EntityType, KeyType]] =
    deriveFormat(context)(kind)(keyFunction)(Set.empty) { subType =>
      import context.universe._
      val keyType = weakTypeTag[KeyType].tpe
      q"EntityFormat[$subType, $keyType]($kind)($keyFunction)"
    }

  def deriveFormatWithIgnoredIndexes[EntityType: context.WeakTypeTag, KeyType: context.WeakTypeTag](context: blackbox.Context)(
    ignoredIndexes: context.Expr[String]*
  )(kind: context.Expr[String])(keyFunction: context.Expr[EntityType => KeyType]): context.Expr[EntityFormat[EntityType, KeyType]] = {
    import context.universe._
    val helper = MacroHelper(context)
    val indexes = ignoredIndexes.map(helper.requireLiteral(_, "ignoredIndexes")).toSet
    val entityType = weakTypeTag[EntityType].tpe
    helper.fieldsMustExistInHierarchy(entityType, indexes)
    deriveFormat(context)(kind)(keyFunction)(indexes) { subType =>
      import context.universe._
      val keyType = weakTypeTag[KeyType].tpe
      val subTypeIndexes = helper.indexesForSubtype(subType, indexes)
      q"EntityFormat.ignoreIndexes[$subType, $keyType](..$subTypeIndexes)($kind)($keyFunction)"
    }
  }

  def deriveFormatOnlyIndexing[EntityType: context.WeakTypeTag, KeyType: context.WeakTypeTag](context: blackbox.Context)(
    onlyIndex: context.Expr[String]*
  )(kind: context.Expr[String])(keyFunction: context.Expr[EntityType => KeyType]): context.Expr[EntityFormat[EntityType, KeyType]] = {
    import context.universe._
    val helper = MacroHelper(context)
    val indexedFields = onlyIndex.map(helper.requireLiteral(_, "onlyIndex")).toSet
    val entityType = weakTypeTag[EntityType].tpe
    helper.fieldsMustExistInHierarchy(entityType, indexedFields)
    deriveFormat(context)(kind)(keyFunction)(helper.allFieldNamesExcept(entityType, indexedFields)) { subType =>
      import context.universe._
      val keyType = weakTypeTag[KeyType].tpe
      val subTypeIndexes = helper.indexesForSubtype(subType, indexedFields)
      q"EntityFormat.onlyIndex[$subType, $keyType](..$subTypeIndexes)($kind)($keyFunction)"
    }
  }

  def deriveFormat[EntityType: context.WeakTypeTag, KeyType: context.WeakTypeTag](
    context: blackbox.Context
  )(kind: context.Expr[String])(keyFunction: context.Expr[EntityType => KeyType])(
    ignoredIndexes: Set[String]
  )(subTypeFormatProvider: context.universe.Symbol => context.universe.Tree): context.Expr[EntityFormat[EntityType, KeyType]] = {
    import context.universe._
    val helper = MacroHelper(context)
    helper.requireLiteral(kind, "kind")
    val entityType = weakTypeTag[EntityType].tpe

    helper.sealedTraitCaseClassOrAbort[EntityFormat[EntityType, KeyType]](
      entityType,
      sealedTraitFormat(context)(helper)(kind, keyFunction, ignoredIndexes, subTypeFormatProvider),
      caseClassFormat(context)(helper)(kind, keyFunction, ignoredIndexes)
    )
  }

  private def sealedTraitFormat[EntityType: context.WeakTypeTag, KeyType: context.WeakTypeTag](context: blackbox.Context)(
    helper: MacroHelper[context.type]
  )(kind: context.Expr[String],
    keyFunction: context.Expr[EntityType => KeyType],
    ignoredIndexes: Set[String],
    subTypeFormatProvider: context.universe.Symbol => context.universe.Tree): context.Expr[EntityFormat[EntityType, KeyType]] = {
    import context.universe._
    val entityType = weakTypeTag[EntityType].tpe
    val keyType = weakTypeTag[KeyType].tpe
    val subTypes = helper.subTypes(entityType)

    val cases = subTypes.map { subType =>
      cq"""e: ${subType.asClass} => ${subTypeFormatProvider(subType)}.toEntity(e, builder.addField(stringFormat.toEntityField("type", ${helper
        .subTypeName(subType)})))"""
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

    val fieldExpressions = helper.caseClassFieldList(entityType).map { field =>
      val (fieldName, name) = helper.fieldName(field)
      val fieldExpression = q"""implicitly[FieldFormat[${field.typeSignature}]].toEntityField($name, value.$fieldName)"""
      if (ignoredIndexes.contains(name)) {
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
    val cases = subTypes.map { subType =>
      cq"""Right(${helper.subTypeName(subType)}) => FromEntity[$subType].fromEntity(entity)"""
    }
    context.Expr[FromEntity[A]](q"""import com.ovoenergy.datastore4s._

          new FromEntity[$entityType] {
            private val stringFormat = implicitly[FieldFormat[String]]
            override def fromEntity(entity: Entity): Either[DatastoreError, $entityType] = stringFormat.fromEntityFieldWithContext("type", entity) match {
              case ..$cases
              case Right(other) => DatastoreError.deserialisationError(s"Unknown subtype found: $$other")
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
      val (_, name) = helper.fieldName(field)
      fq"""${field.name} <- implicitly[FieldFormat[${field.typeSignature}]].fromEntityFieldWithContext($name, entity)"""
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
