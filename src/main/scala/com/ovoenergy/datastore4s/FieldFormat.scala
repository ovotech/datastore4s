package com.ovoenergy.datastore4s

import scala.reflect.macros.blackbox

trait FieldFormat[A] {

  def toEntityField(fieldName: String, value: A): Field

  def fromEntityFieldWithContext(fieldName: String, entity: Entity): Either[DatastoreError, A] =
    fromEntityField(fieldName, entity).left.map(DatastoreError.errorInField(fieldName))

  def fromEntityField(fieldName: String, entity: Entity): Either[DatastoreError, A]

  def toValue[B](scalaValue: B)(implicit valueFormat: ValueFormat[B]): DatastoreValue = valueFormat.toValue(scalaValue)

  def ignoreIndexes: FieldFormat[A] = FieldFormat.ignoreIndexes(this)

}

final case class Field private (values: Map[String, DatastoreValue]) {
  def +(name: String, value: DatastoreValue) = Field(values + (name -> value))

  def +(other: Field) = Field(other.values ++ values) // Composite field

  def ignoreIndexes = Field(values.map { case (key, value) => (key, value.ignoreIndex) })
}

object Field {
  def apply(name: String, value: DatastoreValue): Field = new Field(Map(name -> value))
  def apply(fields: (String, DatastoreValue)*): Field = new Field(fields.toMap)
}

object FieldFormat {

  implicit def fieldFormatFromValueFormat[A](implicit valueFormat: ValueFormat[A]): FieldFormat[A] =
    new FieldFormat[A] {
      override def toEntityField(fieldName: String, value: A) =
        Field(fieldName, valueFormat.toValue(value))

      override def fromEntityField(fieldName: String, entity: Entity): Either[DatastoreError, A] =
        entity
          .field(fieldName)
          .map(valueFormat.fromValue)
          .getOrElse(DatastoreError.missingField(fieldName, entity))
    }

  private val eitherField = "either_side"

  implicit def fieldFormatFromEither[L, R](implicit leftFormat: FieldFormat[L], rightFormat: FieldFormat[R]): FieldFormat[Either[L, R]] =
    new FieldFormat[Either[L, R]] {
      override def toEntityField(fieldName: String, value: Either[L, R]): Field = value match {
        case Left(l)  => leftFormat.toEntityField(fieldName, l) + (s"$fieldName.$eitherField", StringValue("Left"))
        case Right(r) => rightFormat.toEntityField(fieldName, r) + (s"$fieldName.$eitherField", StringValue("Right"))
      }

      override def fromEntityField(fieldName: String, entity: Entity): Either[DatastoreError, Either[L, R]] =
        entity.field(s"$fieldName.$eitherField") match {
          case Some(StringValue("Left"))  => leftFormat.fromEntityFieldWithContext(fieldName, entity).map(Left(_))
          case Some(StringValue("Right")) => rightFormat.fromEntityFieldWithContext(fieldName, entity).map(Right(_))
          case Some(other)                => DatastoreError.error(s"Either field should be either 'Left' or 'Right' but was $other.")
          case None                       => DatastoreError.missingField(eitherField, entity)
        }
    }

  def ignoreIndexes[A](existingFormat: FieldFormat[A]): FieldFormat[A] = new FieldFormat[A] {
    override def toEntityField(fieldName: String, value: A) = existingFormat.toEntityField(fieldName, value).ignoreIndexes

    override def fromEntityField(fieldName: String, entity: Entity) = existingFormat.fromEntityField(fieldName, entity)
  }

  import scala.language.experimental.macros

  def apply[A]: FieldFormat[A] = macro applyImpl[A]

  def applyImpl[A: context.WeakTypeTag](context: blackbox.Context): context.Expr[FieldFormat[A]] = {
    val helper = MacroHelper(context)
    import context.universe._
    val fieldType = weakTypeTag[A].tpe
    helper.sealedTraitCaseClassOrAbort[FieldFormat[A]](fieldType, sealedTraitFormat(context)(helper), caseClassFormat(context)(helper))
  }

  private def sealedTraitFormat[A: context.WeakTypeTag](
    context: blackbox.Context
  )(helper: MacroHelper[context.type]): context.Expr[FieldFormat[A]] = {
    import context.universe._
    val fieldType = weakTypeTag[A].tpe
    val subTypes = helper.subTypes(fieldType)

    val toCases = subTypes.map { subType =>
      if (helper.isObject(subType)) {
        cq"""f: ${subType.asClass} => stringFormat.toEntityField(fieldName + ".type", ${helper.subTypeName(subType)})"""
      } else {
        cq"""f: ${subType.asClass} => FieldFormat[$subType].toEntityField(fieldName, f) + stringFormat.toEntityField(fieldName + ".type", ${helper
          .subTypeName(subType)})"""
      }
    }

    val fromCases = subTypes.map { subType =>
      if (helper.isObject(subType)) {
        cq"""Right(${helper.subTypeName(subType)}) => Right(${helper.singletonObject(subType)})"""
      } else {
        cq"""Right(${helper.subTypeName(subType)}) => FieldFormat[$subType].fromEntityFieldWithContext(fieldName, entity)"""
      }
    }

    context.Expr[FieldFormat[A]](q"""import com.ovoenergy.datastore4s._

          new FieldFormat[$fieldType] {
            private val stringFormat = implicitly[FieldFormat[String]]
            override def toEntityField(fieldName: String, value: $fieldType): Field = value match {
              case ..$toCases
            }

            override def fromEntityField(fieldName: String, entity: Entity): Either[DatastoreError, $fieldType] = stringFormat.fromEntityFieldWithContext(fieldName + ".type", entity) match {
              case ..$fromCases
              case Right(other) => DatastoreError.error(s"Unknown subtype found: $$other")
              case Left(error) => Left(error)
            }
          }
        """)
  }

  private def caseClassFormat[A: context.WeakTypeTag](
    context: blackbox.Context
  )(helper: MacroHelper[context.type]): context.Expr[FieldFormat[A]] = {
    import context.universe._

    val fieldType = weakTypeTag[A].tpe

    val fields = helper.caseClassFieldList(fieldType)

    val fieldExpressions = fields.map { field =>
      val fieldName = field.asTerm.name
      q"""implicitly[FieldFormat[${field.typeSignature}]].toEntityField(fieldName + "." + ${fieldName.toString}, value.$fieldName)"""
    }

    val companion = fieldType.typeSymbol.companion
    val companionNamedArguments = fields.map(field => AssignOrNamedArg(Ident(field.name), q"${field.asTerm.name}"))

    val fieldFormats = fields.map { field =>
      val fieldName = field.asTerm.name
      fq"""${field.name} <- implicitly[FieldFormat[${field.typeSignature}]].fromEntityFieldWithContext(fieldName + "." + ${fieldName.toString}, entity)"""
    }

    context.Expr[FieldFormat[A]](q"""import com.ovoenergy.datastore4s._

          new FieldFormat[$fieldType] {
            override def toEntityField(fieldName: String, value: $fieldType): Field = {
              ${fieldExpressions.reduce(concatFieldExpressionsWithAdd(context)(_, _))}
            }

            override def fromEntityField(fieldName: String, entity: Entity): Either[DatastoreError, $fieldType] = {
              for (
                ..$fieldFormats
              ) yield $companion.apply(..$companionNamedArguments)
            }
          }
        """)
  }

  private def concatFieldExpressionsWithAdd(context: blackbox.Context)(fieldExpression1: context.universe.Tree,
                                                                       fieldExpression2: context.universe.Tree): context.universe.Tree = {
    import context.universe._
    q"$fieldExpression1 + $fieldExpression2"
  }

}
