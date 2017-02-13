package com.wire.users

sealed trait AccentColor {
  def id: Int
}

object AccentColor {
  def apply(): AccentColor = new AccentColor {
    override def id = 1
  }
}
