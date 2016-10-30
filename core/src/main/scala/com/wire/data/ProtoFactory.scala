package com.wire.data

import com.waz.model.nano.Messages
import com.wire.macros.returning

/**
  * @tparam A the type of the proto message's content (eg, Text, Asset etc.)
  */
trait ProtoFactory[-A] {

  import ProtoFactory._

  def set(msg: GenericMsg): A => GenericMsg
}

object ProtoFactory {

  type GenericMsg = Messages.GenericMessage

  object GenericMsg {
    def apply[F: ProtoFactory](id: UId, content: F): GenericMsg =
      returning(new Messages.GenericMessage()) { msg =>
        msg.messageId = id.str
        implicitly[ProtoFactory[F]].set(msg)(content)
      }

    def unapply(proto: GenericMsg): Option[(UId, Any)] =
      Some(UId(proto.messageId), content(proto))

    def content(msg: GenericMsg) = {
      import Messages.{GenericMessage => GM}
      msg.getContentCase match {
        case GM.TEXT_FIELD_NUMBER   => msg.getText
        case _                      => Unknown
      }
    }
  }

  type Text = Messages.Text

  implicit object Text extends ProtoFactory[Text] {
    override def set(msg: GenericMsg) = msg.setText

    def apply(content: String): Text =
      returning(new Messages.Text()) { msg =>
        msg.content = content
      }

    def unapply(proto: Text): Option[String] =
      Some(proto.content)
  }

  type Asset = Messages.Asset

  case object Unknown

  implicit object UnkownContent extends ProtoFactory[Unknown.type] {
    override def set(msg: GenericMsg) = { _ => msg }
  }

}
