package com.wire.testutils

import com.wire.data._
import org.json.{JSONArray, JSONObject}
import org.threeten.bp.Instant

import scala.collection.JavaConverters._

object BackendResponses {

  def notificationsPageResponse(hasMore: Boolean, nots: Seq[JSONObject]) =
    JsonEncoder { o =>
      o.put("has_more", hasMore)
      o.put("notifications", nots.asJava)
    }

  def conversationOtrMessageAdd(rConvId:         RConvId   = RConvId(),
                                time:            Instant   = Instant.EPOCH,
                                text:            String    = "",
                                sender:          ClientId  = ClientId(),
                                recipient:       ClientId  = ClientId(),
                                from:            UserId    = UserId(),
                                notificationId:  UId       = UId()
                                ) =
    JsonEncoder { o =>
      val arr = new JSONArray()
      arr.put(JsonEncoder { o =>
        o.put("conversation", rConvId.str)
        o.put("time", time.toString)
        o.put("data", JsonEncoder { o =>
          o.put("text", text)
          o.put("sender", sender.str)
          o.put("recipient", recipient.str)
        })
        o.put("from", from.str)
        o.put("type", "conversation.otr-message-add")
      })

      o.put("payload", arr)
      o.put("id", notificationId.str)
    }
}
