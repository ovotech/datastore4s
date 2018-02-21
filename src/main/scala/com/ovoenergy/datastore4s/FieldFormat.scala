package com.ovoenergy.datastore4s

import scala.reflect.macros.blackbox.Context

trait FieldFormat[A] {

  def addField(value: A, fieldName: String, builder: EntityBuilder): EntityBuilder

  def fromField(entity: Entity, fieldName: String): Either[DatastoreError, A]

}

object FieldFormat {

  implicit def fieldFormatFromValueFormat[A](implicit valueFormat: ValueFormat[A]): FieldFormat[A] =
    new FieldFormat[A] {
      override def addField(value: A, fieldName: String, builder: EntityBuilder): EntityBuilder =
        builder.addField(fieldName, valueFormat.toValue(value))

      override def fromField(entity: Entity, fieldName: String): Either[DatastoreError, A] =
        entity
          .field(fieldName)
          .map(valueFormat.fromValue)
          .getOrElse(DatastoreError.missingField(fieldName, entity))
    }

  private val eitherField = "either_side"
  implicit def fieldFormatFromEither[L, R](implicit leftFormat: FieldFormat[L], rightFormat: FieldFormat[R]): FieldFormat[Either[L, R]] =
    new FieldFormat[Either[L, R]] {
      override def addField(value: Either[L, R], fieldName: String, builder: EntityBuilder) = value match {
        case Left(l)  => leftFormat.addField(l, fieldName, builder.addField(s"$fieldName.$eitherField", StringValue("Left")))
        case Right(r) => rightFormat.addField(r, fieldName, builder.addField(s"$fieldName.$eitherField", StringValue("Right")))
      }

      override def fromField(entity: Entity, fieldName: String) = entity.field(s"$fieldName.$eitherField") match {
        case Some(StringValue("Left"))  => leftFormat.fromField(entity, fieldName).map(Left(_))
        case Some(StringValue("Right")) => rightFormat.fromField(entity, fieldName).map(Right(_))
        case Some(other)                => DatastoreError.error(s"Either field should be either 'Left' or 'Right' but was $other.")
        case None                       => DatastoreError.missingField(eitherField, entity)
      }
    }

  import scala.language.experimental.macros

  def apply[A](): FieldFormat[A] = macro applyImpl[A]

  def applyImpl[A: context.WeakTypeTag](context: Context)(): context.Expr[FieldFormat[A]] = {
    val helper = MacroHelper(context)
    import context.universe._
    val fieldType = weakTypeTag[A].tpe
    if(helper.isSealedTrait(fieldType)) {
      sealedTratitFormat(context)(helper)
    } else if(helper.isCaseClass(fieldType)) {
      caseClassFormat(context)(helper)
    } else{
      context.abort(context.enclosingPosition, s"Type must either be a sealed trait or a case class but $fieldType is not")
    }
  }

  private def sealedTratitFormat[A: context.WeakTypeTag](context: Context)(helper: MacroHelper[context.type]): context.Expr[FieldFormat[A]] = {
    import context.universe._
    val fieldType = weakTypeTag[A].tpe
    val subTypes = helper.subTypes(fieldType)

    val addCases = subTypes.map { subType =>
      cq"""f: ${subType.asClass} =>
          val withType = stringFormat.addField(${subType.name.toString}, fieldName+ ".type", entityBuilder)
          FieldFormat[$subType].addField(f, fieldName, withType)"""
    }

    val fromCases = subTypes.map { subType =>
      cq"""Right(${subType.name.toString}) => FieldFormat[$subType].fromField(entity, fieldName)"""
    }

    context.Expr[FieldFormat[A]](q"""import com.ovoenergy.datastore4s._

          new FieldFormat[$fieldType] {
            private val stringFormat = implicitly[FieldFormat[String]]
            override def addField(value: $fieldType, fieldName: String, entityBuilder: EntityBuilder): EntityBuilder = value match {
              case ..$addCases
            }

            override def fromField(entity: Entity, fieldName: String): Either[DatastoreError, $fieldType] = stringFormat.fromField(entity, fieldName + ".type") match {
              case ..$fromCases
              case Right(other) => DatastoreError.error(s"Unknown subtype found: $$other")
              case Left(error) => Left(error)
            }
          }
        """)
  }

  private def caseClassFormat[A: context.WeakTypeTag](context: Context)(helper: MacroHelper[context.type]): context.Expr[FieldFormat[A]] = {
    import context.universe._

    val fieldType = weakTypeTag[A].tpe

    val fields = helper.caseClassFieldList(fieldType)

    // TODO Two more abstractables here
    val builderExpressions = fields.map { field =>
      val fieldName = field.asTerm.name
      q"""implicitly[FieldFormat[${field.typeSignature.typeSymbol}]].addField(value.${fieldName}, fieldName + "." + ${fieldName.toString}, entityBuilder)"""
    }

    val companion = fieldType.typeSymbol.companion
    val companionNamedArguments = fields.map(field => AssignOrNamedArg(Ident(field.name), q"${field.asTerm.name}"))

    val fieldFormats = fields.map { field =>
      val fieldName = field.asTerm.name
      fq"""${field.name} <- implicitly[FieldFormat[${field.typeSignature.typeSymbol}]].fromField(entity, fieldName + "." + ${fieldName.toString})"""
    }

    context.Expr[FieldFormat[A]](q"""import com.ovoenergy.datastore4s._

          new FieldFormat[$fieldType] {
            override def addField(value: $fieldType, fieldName: String, entityBuilder: EntityBuilder): EntityBuilder = {
              ..$builderExpressions
              entityBuilder
            }

            override def fromField(entity: Entity, fieldName: String): Either[DatastoreError, $fieldType] = {
              for (
                ..$fieldFormats
              ) yield $companion.apply(..$companionNamedArguments)
            }
          }
        """)
  }

}
