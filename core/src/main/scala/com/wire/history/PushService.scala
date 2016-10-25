package com.wire.history

import com.wire.core.Uid
import com.wire.logging.Logging
import com.wire.logging.Logging.verbose
import com.wire.storage.KeyValueStorage

import scala.concurrent.Future
import com.wire.utils.RichFuture

class PushService {

}

class LastNotificationIdService(kVStorage: KeyValueStorage) {
  import LastNotificationIdService._
  private val lastIdPref = kVStorage.keyValuePref[Option[Uid]](LastNotficationId, None)

  def lastNotificationId() = Future(lastIdPref()).flatten

  def updateLastIdOnNotification(id: Uid, processing: Future[Any]): Unit = {
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
