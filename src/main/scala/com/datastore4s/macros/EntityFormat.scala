package com.datastore4s.macros

import com.datastore4s.core.{EntityKey, Kind}
import com.google.cloud.datastore.{Entity, KeyFactory}

import scala.util.Try
import scala.language.experimental.macros
import scala.reflect.macros.blackbox.Context

trait ToEntity[EntityType, KeyType] {
  def toEntity(record: EntityType)(implicit keyFactorySupplier: () => KeyFactory): Entity
}

object ToEntity {

  def apply[EntityType, KeyType](): ToEntity[EntityType, KeyType] = macro applyImpl[EntityType, KeyType]

  def applyImpl[EntityType: context.WeakTypeTag, KeyType: context.WeakTypeTag](context: Context)(): context.Expr[ToEntity[EntityType, KeyType]] = {
    import context.universe._

    val entityType = weakTypeTag[EntityType].tpe
    require(entityType.typeSymbol.asClass.isCaseClass, s"Entity classes must be a case class but $entityType is not")

    val kind = entityType.typeSymbol.annotations.collect {
      case annotation if annotation.tree.tpe <:< context.weakTypeOf[Kind] =>
        annotation.tree.children.tail match {
          case Literal(Constant(kind: String)) :: Nil => Kind(kind)
        }
    } match {
      case Nil => context.abort(context.enclosingPosition, "Entity case class must be annotated with @Kind")
      case ann :: Nil => TermName(ann.kind)
      case more => context.abort(context.enclosingPosition, s"Entity case class must be annotated with @Kind precisely once, but annotations were: $more")
    }

    val keyType = weakTypeTag[KeyType].tpe

    val keyFields: List[context.universe.Symbol] = entityType.typeSymbol.asClass.primaryConstructor.typeSignature.paramLists.flatten.flatMap { m =>
      val keyAnnotations = m.annotations.collect {
        case annotation if annotation.tree.tpe <:< context.weakTypeOf[EntityKey] => m
      }
      if (keyAnnotations.size > 1) {
        context.abort(context.enclosingPosition, s"The key field must only be annotated with @EntityKey precisely once, but annotations were: $keyAnnotations")
      }
      keyAnnotations
    }

    val (keyExpression, keyMember) = keyFields match {
      case Nil => context.abort(context.enclosingPosition, s"There must be a field annotated with @EntityKey precisely once")
      case m :: Nil =>
        if (!(m.typeSignature =:= keyType)) {
          context.abort(context.enclosingPosition, s"Annotated Key field ${m.name.toString} was of the wrong type. Expected $keyType but was ${m.typeSignature}")
        } else {
          (
            q"""val keyFactory = new com.datastore4s.core.KeyFactoryFacade(keyFactorySupplier().setKind(${kind.decodedName.toString}))
               implicitly[com.datastore4s.core.AsKey[${keyType.typeSymbol}]].toKey(value.${m.asTerm.name}, keyFactory)""", m) // TODO implicit newkeyFactory: KeyFactorySupplier (type of () => KeyBuilder) instead of datastore:Datastore
        }
      case more => context.abort(context.enclosingPosition, s"Entity case class must be annotated with @EntityKey precisely once, but annotations were: $more")
    }

    // TODO this relies on entity mutation. Is this avoidable? If not is it acceptable??
    // TODO is there some way to store the format as val ${fieldName}Format = implicitly[FieldFormat[A]]
    // TODO can we remove the empty q"" in fold left?
    val builderExpression = entityType.typeSymbol.asClass.primaryConstructor.typeSignature.paramLists.flatten.filter(_ != keyMember).foldLeft(q"": context.universe.Tree) {
      case (expression, field) =>
        val fieldName = field.asTerm.name
        q"""$expression
            implicitly[com.datastore4s.core.FieldFormat[${field.typeSignature.typeSymbol}]].addField(value.${fieldName}, ${fieldName.toString}, builder)
          """
    }

    // TODO why does builder expression open a new scope??
    val expression =
      q"""new com.datastore4s.macros.ToEntity[$entityType, $keyType] {
            override def toEntity(value: $entityType)(implicit keyFactorySupplier: () => com.google.cloud.datastore.KeyFactory): com.google.cloud.datastore.Entity = {
              val key = $keyExpression
              val builder = Entity.newBuilder(key)
              $builderExpression
              builder.build()
            }
          }
        """

    println(expression)

    context.Expr[ToEntity[EntityType, KeyType]](
      expression
    )
  }
}

trait FromEntity[EntityType, KeyType] {
  def fromEntity(entity: Entity): Try[EntityType]
}

object FromEntity {
  def apply[EntityType, KeyType](): FromEntity[EntityType, KeyType] = macro applyImpl[EntityType, KeyType]

  def applyImpl[EntityType: context.WeakTypeTag, KeyType: context.WeakTypeTag](context: Context)(): context.Expr[FromEntity[EntityType, KeyType]] = {
    import context.universe._

    val entityType = weakTypeTag[EntityType].tpe
    require(entityType.typeSymbol.asClass.isCaseClass, s"Entity classes must be a case class but $entityType is not")

    val keyType = weakTypeTag[KeyType].tpe

    val keyFields: List[context.universe.Symbol] = entityType.typeSymbol.asClass.primaryConstructor.typeSignature.paramLists.flatten.flatMap { m =>
      val keyAnnotations = m.annotations.collect {
        case annotation if annotation.tree.tpe <:< context.weakTypeOf[EntityKey] => m
      }
      if (keyAnnotations.size > 1) {
        context.abort(context.enclosingPosition, s"The key field must only be annotated with @EntityKey precisely once, but annotations were: $keyAnnotations")
      }
      keyAnnotations
    }

    val key: (context.universe.Tree, context.universe.Symbol) = keyFields match {
      case Nil => context.abort(context.enclosingPosition, s"There must be a field annotated with @SimpleKey or @ComplexKey precisely once")
      case m :: Nil =>
        if (!(m.typeSignature =:= keyType)) {
          context.abort(context.enclosingPosition, s"Annotated Key field ${m.name.toString} was of the wrong type. Expected $keyType but was ${m.typeSignature}")
        } else {
          (q"""implicitly[com.datastore4s.core.AsKey[${keyType.typeSymbol}]].fromKey(key)""", m)
        }
      case other => throw new RuntimeException(s"Not yet implemented the case where the key field are: ${other}")
    }

    // TODO can we store the implicit format?
    val constructionExpressions = entityType.typeSymbol.asClass.primaryConstructor.typeSignature.paramLists.flatten.filter(_ != key._2).map { field =>
      (q"implicitly[com.datastore4s.core.FieldFormat[${field.typeSignature.typeSymbol}]].fromField(entity, ${field.asTerm.name.toString})", field)
    }

    val all = key +: constructionExpressions

    val args = all.map {
      case (expression, field) => AssignOrNamedArg(Ident(field.name), expression)
    }

    val companion = entityType.typeSymbol.companion

    val expression =
      q"""new com.datastore4s.macros.FromEntity[$entityType, $keyType] {
            override def fromEntity(entity: com.google.cloud.datastore.Entity): scala.util.Try[$entityType] = Try {
              val key = entity.getKey()
              $companion.apply(..$args)
            }
          }
        """
    println(expression)
    context.Expr[FromEntity[EntityType, KeyType]](
      expression
    )
  }
}

//trait EntityFormat[EntityType, KeyType] {
//  type KeyFactorySupplier = () => KeyFactory
//  def toEntity(record: EntityType)(implicit keyFactorySupplier: () => KeyFactory): Entity
//
//  def fromEntity(entity: Entity): Try[EntityType]
//}
