package com.wire.messages


import com.wire.data.ProtoFactory.GenericMessage
import com.wire.data.{ConvId, MessageId, UserId}
import org.threeten.bp.Instant

case class MessageData(id:        MessageId           = MessageId(),
                       convId:    ConvId              = ConvId(),
                       senderId:    UserId              = UserId(),
                       msgType:   MessageType         = MessageType.Text,
                       protos:    Seq[GenericMessage] = Seq.empty,
                       localTime: Instant             = Instant.now
                      ) {
  override def toString: String =
    s"""
      |MessageData:
      | id:         $id
      | convId:     $convId
      | senderId:   $senderId
      | msgType:    $msgType
      | protos:     ${protos.toString().replace("\n", "")}
      | localTime:  $localTime
    """.stripMargin
}

sealed trait MessageType

object MessageType {
  case object Text extends MessageType
}

