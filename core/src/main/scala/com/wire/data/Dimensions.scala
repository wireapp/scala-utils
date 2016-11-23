package com.wire.data

trait Dimensions {
  import Dimensions._
  val width: W
  val height: H
}

object Dimensions {

  trait Dimension {
    val s: Int //s for scalar!
    override def toString: String = s.toString
  }

  case class W(s: Int) extends Dimension
  case class H(s: Int) extends Dimension

  def unapply(dim: Dimensions): Option[(W, H)] = Some((dim.width, dim.height))
}
