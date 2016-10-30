package com.wire.testutils

import com.wire.data.{ClientId, RConvId, UId, UserId}
import org.threeten.bp.Instant

/**
  * This object provides helper methods that will return events in the form of push notifications
  * as if they were sent from the backend. It is done like that so as to make the process of how
  * events are created very clear and so that if the backend changes part of the API, we can just
  * change the json structure accordingly, which should then break a few tests.
  */
object Events {
  def conversationOtrMessage(rConvId:         RConvId   = RConvId(),
                             time:            Instant   = Instant.EPOCH,
                             text:            String    = "",
                             sender:          ClientId  = ClientId(),
                             recipient:       ClientId  = ClientId(),
                             from:            UserId    = UserId(),
                             notificationId:  UId       = UId()
                            ) = {
    jsonFrom("/events/conversation.otr-message-add.json", Map(
      "conversation"    -> rConvId.str,
      "time"            -> time.toString,
      "text"            -> text,
      "sender"          -> sender.str,
      "recipient"       -> recipient.str,
      "from"            -> from.str,
      "notificationId"  -> notificationId.str
    ))
  }
}
