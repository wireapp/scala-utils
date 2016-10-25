package com.wire.core

import java.util.UUID

trait Id {
  val str: String
  override def toString: String = str
}

trait IdGen[A <: Id] extends Ordering[A] {
  def random(): A
  def decode(str: String): A
  def encode(id: A): String = id.toString

  override def compare(x: A, y: A): Int = Ordering.String.compare(encode(x), encode(y))
}

case class Uid(str: String) extends Id

object Uid {
  def apply(): Uid = Uid(UUID.randomUUID().toString)
  def apply(mostSigBits: Long, leastSigBits: Long): Uid = Uid(new UUID(mostSigBits, leastSigBits).toString)

  implicit object UidId extends IdGen[Uid] {
    override def random(): Uid = Uid()
    override def decode(str: String): Uid = Uid(str)
  }
}
