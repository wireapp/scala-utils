package com.wire.messages

import com.wire.conversations.ConversationData
import com.wire.data.{AssetId, ConvId, MessageEvent, Mime}

import scala.concurrent.Future

trait MessageService {
  private[messages] def processEvents(conv: ConversationData, events: Seq[MessageEvent]): Future[Seq[MessageData]]
  def addTextMessage(convId: ConvId, text: String): Future[MessageData]
  def addAssetMessage(convId: ConvId, assetId: AssetId, mime: Mime): Future[MessageData]
}

class DefaultMessageService extends MessageService {

  import com.wire.threading.Threading.Implicits.Background

  override private[messages] def processEvents(conv: ConversationData, events: Seq[MessageEvent]): Future[Seq[MessageData]] = {
    Future(Seq(MessageData()))
  }

  override def addTextMessage(convId: ConvId, text: String): Future[MessageData] = ???

  override def addAssetMessage(convId: ConvId, assetId: AssetId, mime: Mime): Future[MessageData] = ???
}
