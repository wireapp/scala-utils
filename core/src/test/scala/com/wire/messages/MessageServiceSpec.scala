package com.wire.messages

import com.wire.conversations.ConversationData
import com.wire.data.ProtoFactory.{GenericMessage, Text}
import com.wire.data.{GenericMsgEvent, MsgEvent, UId, UserId}
import com.wire.testutils.FullFeatureSpec
import com.wire.utils.RichInstant
import org.threeten.bp.Instant

import scala.concurrent.Await
import scala.concurrent.duration._

class MessageServiceSpec extends FullFeatureSpec {

  var service: MessageService = _

  before {
    service = new DefaultMessageService
  }

  scenario("Process message event for given conversation") {
    val conv = ConversationData()
    val sender = UserId()
    val receivedTime = Instant.now
    val protoMessage = GenericMessage(UId(), Text("Test"))

    val result = Await.result(service.processEvents(conv, Seq(GenericMsgEvent(UId(), conv.remoteId, receivedTime, sender, protoMessage))), 5.seconds)

    result should have size 1
    inside(result.head) { case MessageData(_, convId, senderId, msgType, protos, localTime) =>
      convId shouldEqual conv.id
      senderId shouldEqual sender
      msgType shouldEqual MessageType.Text
      protos shouldEqual Seq(protoMessage)
      localTime shouldEqual receivedTime
    }
  }

  scenario("Ignore com.wire.history.events before conversation cleared time") {
    val clearTime = Instant.now
    val conv = ConversationData(lastCleared = clearTime)
    val sender = UserId()

    val earlierEvents = generateEvents(conv, sender, clearTime, before = true)
    val laterEvents = generateEvents(conv, sender, clearTime, before = false)

    val res = Await.result(service.processEvents(conv, earlierEvents ++ laterEvents), 5.seconds)

    res should have size laterEvents.size
    res.foreach {
      inside(_) { case MessageData(_, _, _, _, _, time) =>
        time should be > clearTime
      }
    }
  }

  scenario("Process add asset event") {
    val conv = ConversationData()
    val sender = UserId()

//    val assetEvent = GenericMessageEvent()
  }

  /**
    * Generates a sequence of simple GenericMessage Text Events to a given conversation from a given user.
    * Each event will be given a timestamp up to count seconds before or after the given fromInstant, so as
    * not to overlap with the fromInstant.
    */
  def generateEvents(conv: ConversationData, sender: UserId, fromTime: Instant, before: Boolean, count: Int = 3): Seq[MsgEvent] = {
    def addTime(i: Int) = (if (before) -(count - (i - 1)) else i).seconds
    (1 to count).map { i =>
      GenericMsgEvent(
        UId(),
        conv.remoteId,
        fromTime + addTime(i),
        sender,
        GenericMessage(UId(), Text(s"Test $count")))
    }
  }

}
