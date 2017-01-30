/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH

 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
  package com.wire.messages

import com.wire.conversations.ConversationData
import com.wire.data.ProtoFactory.{Asset, GenericMsg, Text}
import com.wire.data._
import com.wire.testutils.FullFeatureSpec
import com.wire.utils.RichInstant
import org.threeten.bp.Instant

import scala.concurrent.duration._

class MessageServiceSpec extends FullFeatureSpec {

  val mockContentUpdater      = mock[MessageContentUpdater]
  val service: MessageService = new DefaultMessageService(mockContentUpdater)

  scenario("Process message event for given conversation") {
    val conv = ConversationData()
    val sender = UserId()
    val receivedTime = Instant.now
    val protoMessage = GenericMsg(UId(), Text("Test"))

    (mockContentUpdater.addMessages _)
      .expects(where { (cId, ms) =>
        cId shouldEqual conv.id
        ms should have size 1
        inside(ms.head) {
          case MessageData(_, convId, senderId, msgType, protos, localTime) =>
            convId    shouldEqual conv.id
            senderId  shouldEqual sender
            msgType   shouldEqual MessageType.Text
            protos    shouldEqual Seq(protoMessage)
            localTime shouldEqual receivedTime
        }
        true
      })

    service.processEvents(conv, Seq(GenericMsgEvent(UId(), conv.remoteId, receivedTime, sender, protoMessage)))
  }

  scenario("Ignore events before conversation cleared time") {
    val clearTime = Instant.now
    implicit val conv = ConversationData(lastCleared = clearTime)
    implicit val sender = UserId()

    def content(count: Int) = GenericMsg(UId(), Text(s"Test $count"))

    val earlierEvents = generateEvents(clearTime, before = true, content = content)
    val laterEvents = generateEvents(clearTime, before = false, content = content)

    (mockContentUpdater.addMessages _)
      .expects(where { (_, ms) =>
        ms should have size laterEvents.size
        ms.foreach {
          inside(_) { case MessageData(_, _, _, _, _, time) =>
            time should be > clearTime
          }
        }
        true
      })

    service.processEvents(conv, earlierEvents ++ laterEvents)

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

    (mockContentUpdater.addMessages _)
      .expects(where { (_, ms) =>
        ms should have size 4
        ms.zipWithIndex.foreach { case (m, i) =>
          inside(m) { case MessageData(_, _, _, msgType, _, _) =>
            msgType shouldEqual (i + 1 match {
              case 1 => MessageType.AudioAsset
              case 2 => MessageType.ImageAsset
              case 3 => MessageType.VideoAsset
              case 4 => MessageType.OtherAsset
            })
          }
        }
        true
      })

    service.processEvents(conv, assetEvents)
  }

  /**
    * Generates a sequence of GenericMessageEvents to a given conversation from a given user, each event being
    * defined by the content function that takes the index of the event currently being generated.
    * Each event will be given a timestamp up to count seconds before or after the given fromInstant, so as
    * not to overlap with the fromInstant.
    */
  def generateEvents(fromTime: Instant = Instant.now, before: Boolean = false, count: Int = 3, content: Int => GenericMsg)
                    (implicit convTo: ConversationData, fromUser: UserId): Seq[MsgEvent] = {
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
