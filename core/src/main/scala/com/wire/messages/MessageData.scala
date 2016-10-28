package com.wire.messages


import com.wire.data.ProtoBuffer.GenericMessage
import com.wire.data.{ConvId, MessageId, UserId}
import org.threeten.bp.Instant

case class MessageData(id:        MessageId           = MessageId(),
                       convId:    ConvId              = ConvId(),
                       userId:    UserId              = UserId(),
                       msgType:   MessageType         = MessageType.Text,
                       protos:    Seq[GenericMessage] = Seq.empty,
                       localTime: Instant             = Instant.now
                      ) {
  override def toString: String =
    s"""
      |MessageData:
      | id:         $id
      | convId:     $convId
      | userId:     $userId
      | msgType:    $msgType
      | localTime:  $localTime
    """.stripMargin
}

sealed trait MessageType

object MessageType {
  case object Text extends MessageType
}

