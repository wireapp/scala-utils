package com.wire.messages

import com.wire.conversations.ConversationData
import com.wire.data.ProtoFactory.{Asset, GenericMsg, Text}
import com.wire.data._
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
    val protoMessage = GenericMsg(UId(), Text("Test"))

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

  scenario("Ignore events before conversation cleared time") {
    val clearTime = Instant.now
    implicit val conv = ConversationData(lastCleared = clearTime)
    implicit val sender = UserId()

    def content(count: Int) = GenericMsg(UId(), Text(s"Test $count"))

    val earlierEvents = generateEvents(clearTime, before = true, content = content)
    val laterEvents = generateEvents(clearTime, before = false, content = content)

    val res = Await.result(service.processEvents(conv, earlierEvents ++ laterEvents), 5.seconds)

    res should have size laterEvents.size
    res.foreach {
      inside(_) { case MessageData(_, _, _, _, _, time) =>
        time should be > clearTime
      }
    }
  }

  scenario("Process add asset events") {
    implicit val conv = ConversationData()
    implicit val sender = UserId()

    val assetEvents = generateEvents(count = 4, content = c => GenericMsg(UId(), c match {
      case 1 => Asset(Mime.Audio.MP3, 1024 * 1024, Some("Audio file"))
      case 2 => Asset(Mime.Image.PNG, 1024 * 1024, Some("Image file"))
      case 3 => Asset(Mime.Video.MP4, 1024 * 1024, Some("Video file"))
      case 4 => Asset(Mime("something"), 1024 * 1024, Some("Some other file"))
    }))

    val res = Await.result(service.processEvents(conv, assetEvents), 5.seconds)

    res should have size 4
    res.zipWithIndex.foreach { case (m, i) =>
      inside(m) { case MessageData(_, _, _, msgType, _, _) =>
          msgType shouldEqual (i + 1 match {
            case 1 => MessageType.AudioAsset
            case 2 => MessageType.ImageAsset
            case 3 => MessageType.VideoAsset
            case 4 => MessageType.OtherAsset
          })
      }
    }
  }

  /**
    * Generates a sequence of simple GenericMessage Text Events to a given conversation from a given user.
    * Each event will be given a timestamp up to count seconds before or after the given fromInstant, so as
    * not to overlap with the fromInstant.
    */
  def generateEvents(fromTime: Instant = Instant.now, before: Boolean = false, count: Int = 3, content: Int => GenericMsg)
                    (implicit convTo: ConversationData, fromUser: UserId) : Seq[MsgEvent] = {
    def addTime(i: Int) = (if (before) -(count - (i - 1)) else i).seconds
    (1 to count).map { i =>
      GenericMsgEvent(
        UId(),
        convTo.remoteId,
        fromTime + addTime(i),
        fromUser,
        content(i))
    }
  }

}
