/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH

 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
 package com.wire.macros

import scala.language.experimental.macros
import scala.annotation.tailrec
import scala.reflect.macros.blackbox.Context

object logging {

  type LogTag = String

  object ImplicitTag {
    implicit def implicitLogTag: LogTag = macro LogTagMacros.enclosingLogTag
  }

  def logTagFor[A <: Singleton](a: A): LogTag = macro LogTagMacros.logTagForSingleton[A]

  def logTagFor[A]: LogTag = macro LogTagMacros.logTagFor[A]
}


object LogTagMacros {

  def enclosingLogTag(c: Context) = {
    import c.universe._

    def nameOf(s: c.Symbol): String = if (s.name.toString == "package") nameOf(s.owner) else s.name.toString

    @tailrec def owningClasses(s: c.Symbol, accu: List[c.Symbol] = Nil): List[c.Symbol] =
      if (s == NoSymbol || s.isPackage) accu
      else if (s.isClass && !nameOf(s).startsWith("$")) owningClasses(s.owner, s :: accu)
      else owningClasses(s.owner, accu)

    val parents = owningClasses(c.internal.enclosingOwner)
    val name = if (parents.isEmpty) "UNKNOWN" else parents.map(nameOf).mkString(".")

    q"$name"
  }

  def logTagForSingleton[A <: Singleton](c: Context)(a: c.Expr[A])(implicit tag: c.WeakTypeTag[A]) = logTagFor[A](c)

  def logTagFor[A](c: Context)(implicit tag: c.WeakTypeTag[A]) = {
    import c.universe._
    val name = tag.tpe.typeSymbol.fullName.split('.').lastOption.getOrElse("UNKNOWN")
    q"$name"
  }
}
