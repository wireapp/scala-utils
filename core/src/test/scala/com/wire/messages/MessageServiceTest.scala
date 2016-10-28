package com.wire.messages

import com.wire.conversations.ConversationData
import com.wire.data.ProtoBuffer.{GenericMessage, Text}
import com.wire.data.{GenericMessageEvent, RConvId, UId, UserId}
import com.wire.testutils.FullFeatureSpec
import org.threeten.bp.Instant
import scala.concurrent.duration._

import scala.concurrent.Await

class MessageServiceTest extends FullFeatureSpec {


  scenario("Process message event for given conversation") {
    val service = new DefaultMessageService

    val conversation = ConversationData() //generate random conversation
    val sender = UserId()

    val processing = service.processEvents(
      conversation,
      Seq(GenericMessageEvent(UId(), RConvId(), Instant.now, UserId(), GenericMessage(UId(), Text("Test")))))

    Await.result(processing, 5.seconds) shouldEqual Seq(MessageData())

  }
}
