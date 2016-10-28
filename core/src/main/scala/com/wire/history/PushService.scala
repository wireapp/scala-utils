package com.wire.history

import com.wire.data.UId
import com.wire.logging.Logging.verbose
import com.wire.macros.logging.ImplicitTag._
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
