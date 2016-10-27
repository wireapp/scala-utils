package com.wire.data

import java.math.BigInteger
import java.util.UUID

import scala.util.Random

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
  def apply(int: Int): Uid = Uid(int.toString)
  def apply(mostSigBits: Long, leastSigBits: Long): Uid = Uid(new UUID(mostSigBits, leastSigBits).toString)

  implicit object UidId extends IdGen[Uid] {
    override def random(): Uid = Uid()
    override def decode(str: String): Uid = Uid(str)
  }
}

case class ClientId(str: String) extends Id {
  def longId = new BigInteger(str, 16).longValue()
}

object ClientId {

  implicit object ClientIdGen extends IdGen[ClientId] {
    override def random(): ClientId = ClientId(Random.nextLong().toHexString)
    override def decode(str: String): ClientId = ClientId(str)
    override def encode(id: ClientId): String = id.str
  }

  def apply() = ClientIdGen.random()

  def opt(id: String) = Option(id).filter(_.nonEmpty).map(ClientId(_))
}

