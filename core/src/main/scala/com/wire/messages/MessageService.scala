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
