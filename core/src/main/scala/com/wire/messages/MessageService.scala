package com.wire.messages

import com.wire.conversations.ConversationData
import com.wire.data.ProtoFactory.{Asset, GenericMsg, Text}
import com.wire.data._
import com.wire.logging.Logging._

import scala.concurrent.Future

trait MessageService {
  private[messages] def processEvents(conv: ConversationData, events: Seq[MsgEvent]): Future[Seq[MessageData]]
  def addTextMessage(convId: ConvId, text: String): Future[MessageData]
  def addAssetMessage(convId: ConvId, assetId: AssetId, mime: Mime): Future[MessageData]
}

class DefaultMessageService extends MessageService {

  import com.wire.threading.Threading.Implicits.Background

  override private[messages] def processEvents(conv: ConversationData, events: Seq[MsgEvent]): Future[Seq[MessageData]] = Future {

    val afterCleared = events.filter(e => conv.lastCleared.isBefore(e.time))

    afterCleared.collect { case ev =>
      verbose(s"processing event: $ev")
      println(s"processing event: $ev")
      ev match {
        case GenericMsgEvent(id, convId, time, from, protos) =>
          val msgType = protos match {
            case GenericMsg(_, Text(_))   => MessageType.Text
            case GenericMsg(_, Asset(_))  => MessageType.AudioAsset
            case _                        => MessageType.Unknown
          }
          MessageData(
            convId    = conv.id,
            senderId  = from,
            msgType   = msgType,
            protos    = Seq(protos),
            localTime = time
          )
      }
    }
  }

  override def addTextMessage(convId: ConvId, text: String): Future[MessageData] = ???

  override def addAssetMessage(convId: ConvId, assetId: AssetId, mime: Mime): Future[MessageData] = ???
}
