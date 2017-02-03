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
package com.wire.events

import com.wire.data.UId
import com.wire.logging.ZLog.ImplicitTag._
import com.wire.logging.ZLog._
import com.wire.storage.KeyValueStorage
import com.wire.threading.Threading
import com.wire.utils.RichFuture

import scala.concurrent.Future

class PushService {

}

class LastNotificationIdService(kVStorage: KeyValueStorage) {

  import LastNotificationIdService._

  implicit val dispatcher = Threading.Background

  private val lastIdPref = kVStorage.keyValuePref[Option[UId]](LastNotficationId, None)

  def lastNotificationId() = Future(lastIdPref()).flatten

  def updateLastIdOnNotification(id: UId, processing: Future[Any]): Unit = {
    processing.onSuccess {
      case _ =>
        lastIdPref := Some(id)
        verbose(s"updated last id: $id")
    }
  }

}

object LastNotificationIdService {
  val LastNotficationId = "last_notification_id"
}
