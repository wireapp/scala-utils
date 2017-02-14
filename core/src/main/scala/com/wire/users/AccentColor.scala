package com.wire.users

import AccentColor._

case class AccentColor(id: Int, r: Int, g: Int, b: Int, a: Int)  {
  def this(id: Int, r: Double, g: Double, b: Double, a: Double) = this(id, int(r), int(g), int(b), int(a))

  def getColor =  (a << 24) | (r << 16) | (g << 8) | b
}

object AccentColor {

  def apply(id: Int): AccentColor = AccentColors.colorsMap.getOrElse(id, AccentColors.defaultColor)

  def apply() = AccentColors.defaultColor

  private def int(c: Double) = (c * 255).toInt

  def apply(r: Double, g: Double, b: Double, a: Double): AccentColor = apply(int(r), int(g), int(b), int(a))

  /**
    * Finds closest matching accent color.
    */
  def apply(r: Int, g: Int, b: Int, a: Int): AccentColor = {
    def sq(x: Int) = x * x
    AccentColors.colors.minBy(c => sq(c.r - r) + sq(c.g - g) + sq(c.b - b) + sq(c.a - a))
  }
}

object AccentColors {
  private val Default = new AccentColor(1, 0.141, 0.552, 0.827, 1)

  var colors = Array(
    new AccentColor(1, 0.141, 0.552, 0.827, 1),
    new AccentColor(2, 0, 0.784, 0, 1),
    new AccentColor(3, 1, 0.823, 0, 1),
    new AccentColor(4, 1, 0.152, 0, 1),
    new AccentColor(5, 1, 0.588, 0, 1),
    new AccentColor(6, 0.996, 0.368, 0.741, 1),
    new AccentColor(7, 0.615, 0, 1, 1)
  )
  var colorsMap = Map(0 -> Default) ++ colors.map(c => c.id -> c).toMap

  def defaultColor = colorsMap.getOrElse(0, Default)

  def setColors(arr: Array[AccentColor]): Unit = {
    colors = arr
    colorsMap = Map(0 -> colors.headOption.getOrElse(Default)) ++ colors.map(c => c.id -> c).toMap
  }

  def getColors: Array[AccentColor] = colors
}
