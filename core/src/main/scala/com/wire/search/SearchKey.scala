package com.wire.search

import java.util.regex.Pattern.{compile, quote}

import com.wire.utils.Locales

final class SearchKey private (val asciiRepresentation: String) extends Serializable {
  private[this] lazy val pattern = compile(s"(.+ )?${quote(asciiRepresentation)}.*")
  def isAtTheStartOfAnyWordIn(other: SearchKey) = pattern.matcher(other.asciiRepresentation).matches
  def isEmpty = asciiRepresentation.isEmpty

  override def equals(any: Any): Boolean = any match {
    case other: SearchKey => other.asciiRepresentation == asciiRepresentation
    case _ => false
  }
  override def hashCode: Int = asciiRepresentation.##
  override def toString: String = s"${classOf[SearchKey].getSimpleName}($asciiRepresentation)"
}

object SearchKey extends (String => SearchKey) {
  val empty = new SearchKey("")
  def apply(name: String): SearchKey = new SearchKey(transliterated(name))
  def unsafeRestore(asciiRepresentation: String) = new SearchKey(asciiRepresentation)
  def unapply(k: SearchKey): Option[String] = Some(k.asciiRepresentation)

  def transliterated(s: String): String = Locales.transliteration.transliterate(s).trim
}