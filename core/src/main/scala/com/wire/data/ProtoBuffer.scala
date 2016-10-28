package com.wire.data

import com.waz.model.nano.Messages
import com.wire.macros.returning

trait ProtoBuffer[-A] {

  import ProtoBuffer._

  def set(msg: GenericMessage): A => GenericMessage
}

object ProtoBuffer {

  type GenericMessage = Messages.GenericMessage

  object GenericMessage {
    def apply[A: ProtoBuffer](id: UId, content: A): GenericMessage =
      returning(new Messages.GenericMessage()) { msg =>
        msg.messageId = id.str
        implicitly[ProtoBuffer[A]].set(msg)(content)
      }
  }

  type Text = Messages.Text

  implicit object Text extends ProtoBuffer[Text] {
    override def set(msg: GenericMessage) = msg.setText

    def apply(content: String): Text =
      returning(new Messages.Text()) { msg =>
        msg.content = content
      }
  }

  type Asset = Messages.Asset

}
