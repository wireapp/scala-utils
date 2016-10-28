package com.wire.messages

import com.wire.conversations.ConversationData
import com.wire.data.ProtoFactory.{GenericMessage, Text}
import com.wire.data.{GenericMessageEvent, UId, UserId}
import com.wire.testutils.FullFeatureSpec
import org.threeten.bp.Instant

import scala.concurrent.Await
import scala.concurrent.duration._

class MessageServiceTest extends FullFeatureSpec  {

  scenario("Process message event for given conversation") {
    val service = new DefaultMessageService

    val conv = ConversationData()
    val sender = UserId()
    val receivedTime = Instant.now
    val protoMessage = GenericMessage(UId(), Text("Test"))

    val result = Await.result(service.processEvents(conv, Seq(GenericMessageEvent(UId(), conv.remoteId, receivedTime, sender, protoMessage))), 5.seconds)

    result should have size 1
    inside(result.head) { case MessageData(_, convId, senderId, msgType, protos, localTime) =>
        convId    shouldEqual conv.id
        senderId  shouldEqual sender
        msgType   shouldEqual MessageType.Text
        protos    shouldEqual Seq(protoMessage)
        localTime shouldEqual receivedTime
    }
  }

}
