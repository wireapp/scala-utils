package com.wire.conversations

import com.wire.data.{ConvId, RConvId}

case class ConversationData(id:         ConvId          = ConvId(),
                            remoteId:   RConvId         = RConvId(),
                            name:       Option[String]  = None
                           ) {
  override def toString: String =
    s"""
       |ConversationData:
       | id:          $id
       | remoteId:    $remoteId
       | name:        $name
    """.stripMargin
}
