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
import com.wire.data.ProtoFactory.Asset.Original
import com.wire.data.ProtoFactory.{Asset, GenericMsg, Text}
import com.wire.data._
import com.wire.logging.Logging._

import scala.concurrent.Future

trait MessageService {
  private[messages] def processEvents(conv: ConversationData, events: Seq[MsgEvent]): Future[Set[MessageData]]
  def addTextMessage(convId: ConvId, text: String): Future[MessageData]
  def addAssetMessage(convId: ConvId, assetId: AssetId, mime: Mime): Future[MessageData]
}

class DefaultMessageService(content: MessageContentUpdater) extends MessageService {

  override private[messages] def processEvents(conv: ConversationData, events: Seq[MsgEvent]): Future[Set[MessageData]] = {

    val afterCleared = events.filter(e => conv.lastCleared.isBefore(e.time))

    content.addMessages(conv.id, afterCleared.collect { case ev =>
      verbose(s"processing event: $ev")
      ev match {
        case GenericMsgEvent(id, convId, time, from, protos @ GenericMsg(_, content)) =>
          val msgType = content match {
            case Text(_)                                           => MessageType.Text
            case Asset(Some(Original(Mime.Audio(), _, _)), _, _)   => MessageType.AudioAsset
            case Asset(Some(Original(Mime.Image(), _, _)), _, _)   => MessageType.ImageAsset
            case Asset(Some(Original(Mime.Video(), _, _)), _, _)   => MessageType.VideoAsset
            case Asset(_)                                          => MessageType.OtherAsset
            case _                                                 => MessageType.Unknown
          }
          MessageData(
            convId    = conv.id,
            senderId  = from,
            msgType   = msgType,
            protos    = Seq(protos),
            localTime = time
          )
      }
    })
  }

  override def addTextMessage(convId: ConvId, text: String): Future[MessageData] = ???

  override def addAssetMessage(convId: ConvId, assetId: AssetId, mime: Mime): Future[MessageData] = ???
}
