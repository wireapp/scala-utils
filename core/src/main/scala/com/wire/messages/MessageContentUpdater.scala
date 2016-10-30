package com.wire.messages

import com.wire.data.ConvId

import scala.concurrent.Future

trait MessageContentUpdater {
  def addMessages(convId: ConvId, msgs: Seq[MessageData]): Future[Set[MessageData]]
}
