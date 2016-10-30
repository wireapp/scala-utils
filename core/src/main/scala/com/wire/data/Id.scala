package com.wire.data

import java.util.UUID

import scala.util.Random

sealed trait Id {
  val str: String
  override def toString: String = str
}
trait IdGen[A <: Id] extends Ordering[A] {
  def random(): A = decode(UId().str)
  def decode(str: String): A
  def encode(id: A): String = id.str

  override def compare(x: A, y: A): Int = Ordering.String.compare(encode(x), encode(y))
}

case class UId(str: String) extends Id
object UId {
  def apply(): UId = UId(UUID.randomUUID().toString)
  def apply(int: Int): UId = UId(int.toString)
  def apply(mostSigBits: Long, leastSigBits: Long): UId = UId(new UUID(mostSigBits, leastSigBits).toString)

  implicit object UIdGen extends IdGen[UId] {
    override def decode(str: String): UId = UId(str)
  }
}

case class ClientId(str: String) extends Id
object ClientId {
  implicit object ClientIdGen extends IdGen[ClientId] {
    override def random(): ClientId = ClientId(Random.nextLong().toHexString)
    override def decode(str: String): ClientId = ClientId(str)
  }

  def apply() = ClientIdGen.random()

  def opt(id: String) = Option(id).filter(_.nonEmpty).map(ClientId(_))
}

case class UserId(str: String) extends Id
object UserId {
  def apply(): UserId = UserIdGen.random()

  implicit object UserIdGen extends IdGen[UserId] {
    override def decode(str: String) = UserId(str)
  }
}

case class ConvId(str: String) extends Id
object ConvId {
  def apply(): ConvId = ConvIdGen.random()

  implicit object ConvIdGen extends IdGen[ConvId] {
    override def decode(str: String) = ConvId(str)
  }
}

case class RConvId(str: String) extends Id
object RConvId {
  def apply(): RConvId = RConvIdGen.random()

  implicit object RConvIdGen extends IdGen[RConvId] {
    override def decode(str: String) = RConvId(str)
  }
}

case class MessageId(str: String) extends Id
object MessageId {
  def apply(): MessageId = MessageIdGen.random()

  implicit object MessageIdGen extends IdGen[MessageId] {
    override def decode(str: String) = MessageId(str)
  }
}

case class AssetId(str: String) extends Id
object AssetId {
  def apply(): AssetId = AssetIdGen.random()

  implicit object AssetIdGen extends IdGen[AssetId] {
    override def decode(str: String) = AssetId(str)
  }
}

case class RAssetDataId(str: String) extends Id

object RAssetDataId {
  val Empty = RAssetDataId("empty")
  def apply(): RAssetDataId = Id.random()

  implicit object Id extends IdGen[RAssetDataId] {
    override def decode(str: String) = RAssetDataId(str)
  }
}
