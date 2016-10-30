package com.wire.conversations

import com.wire.data.{ConvId, RConvId}
import org.threeten.bp.Instant

case class ConversationData(id:           ConvId          = ConvId(),
                            remoteId:     RConvId         = RConvId(),
                            name:         Option[String]  = None,
                            lastCleared:  Instant         = Instant.EPOCH
                           ) {
  override def toString: String =
    s"""
       |ConversationData:
       | id:          $id
       | remoteId:    $remoteId
       | name:        $name
    """.stripMargin
}
