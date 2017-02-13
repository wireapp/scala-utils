package com.wire.utils

import java.text.Collator
import java.util.Locale

object Locales {

  //TODO
  lazy val transliteration = new Transliteration {
    override def transliterate(s: String) = s
  }

  def currentLocale: Locale = Locale.getDefault

  // the underlying native collator shows no signs of being thread-safe & locale might change – that's why this is a def instead of a val
  def currentLocaleOrdering: Ordering[String] = new Ordering[String] {
    private[this] val collator = Collator.getInstance(currentLocale)
    final def compare(a: String, b: String): Int = collator.compare(a, b)
  }
}

trait Transliteration {
  def transliterate(s: String): String
}