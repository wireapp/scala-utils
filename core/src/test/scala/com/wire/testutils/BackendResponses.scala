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
