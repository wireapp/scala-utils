package com.wire.messages

import com.wire.conversations.ConversationData
import com.wire.data.ProtoFactory.{GenericMsg, Text}
import com.wire.data._

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


    afterCleared.collect {
      case GenericMsgEvent(id, convId, time, from, protos@GenericMsg(_, Text(content))) =>
        MessageData(
          convId = conv.id,
          senderId = from,
          msgType = MessageType.Text,
          protos = Seq(protos),
          localTime = time
        )
    }
  }

  override def addTextMessage(convId: ConvId, text: String): Future[MessageData] = ???

  override def addAssetMessage(convId: ConvId, assetId: AssetId, mime: Mime): Future[MessageData] = ???
}
