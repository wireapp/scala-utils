package com.wire.otr

import java.io.File

import com.wire.cryptobox.{CryptoBox, PreKey}
import com.wire.data.{AccountId, ClientId}
import com.wire.error.LoggedTry
import com.wire.logging.ZLog.ImplicitTag._
import com.wire.logging.ZLog.verbose
import com.wire.macros.returning
import com.wire.otr.Client.{OtrClientType, Verification}
import com.wire.storage.KeyValueStorage
import com.wire.threading.{SerialDispatchQueue, Threading}
import com.wire.utils.IOUtils
import org.threeten.bp.Instant

import scala.concurrent.Future

trait CryptoBoxService {

  def clientLabel: String

  def cryptoBoxDir: File

  def cryptoBox: Future[Option[CryptoBox]]

  def apply[A](f: CryptoBox => Future[A]): Future[Option[A]]

  def deleteCryptoBox(): Future[Unit]

  def close(): Future[Unit]

  def createClient(id: ClientId = ClientId()): Future[Option[(Client, PreKey, Seq[PreKey])]]

  def generatePreKeysIfNeeded(remainingKeys: Seq[Int]): Future[Seq[PreKey]]

}

//TODO fix unmanaged dependency on cryptobox jni
class DefaultCryptoBoxService(accountId: AccountId, keyValue: KeyValueStorage, targetDir: File) extends CryptoBoxService {

  import CryptoBoxService._

  override lazy val cryptoBoxDir = returning(new File(targetDir, accountId.str))(_.mkdirs())

  private implicit val dispatcher = new SerialDispatchQueue(Threading.IO)

  private val lastPreKeyId = keyValue.keyValuePref("otr_last_prekey_id", 0)

  private var _cryptoBox = Option.empty[CryptoBox]

  override lazy val clientLabel = "Scala Utils"

  override def cryptoBox = Future {
    _cryptoBox.orElse {
      returning(load) { _cryptoBox = _ }
    }
  }

  private def load = LoggedTry {
    cryptoBoxDir.mkdirs()
    CryptoBox.open(cryptoBoxDir.getAbsolutePath)
  } .toOption

  override def apply[A](f: (CryptoBox) => Future[A]) = cryptoBox flatMap {
    case None => Future successful None
    case Some(cb) => f(cb) map (Some(_))
  }

  override def deleteCryptoBox() = Future {
    _cryptoBox.foreach(_.close())
    _cryptoBox = None
    IOUtils.deleteRecursively(cryptoBoxDir)
    verbose(s"cryptobox directory deleted")
  }

  override def close() = Future {
    _cryptoBox.foreach(_.close())
    _cryptoBox = None
  }

  override def createClient(id: ClientId) = apply { cb =>
    val (lastKey, keys) = (cb.newLastPreKey(), cb.newPreKeys(0, PreKeysCount))
    (lastPreKeyId := keys.last.id) map { _ =>
      (Client(id, clientLabel, clientLabel, Some(Instant.now), signalingKey = Some(SignalingKey()), verified = Verification.Verified, devType = OtrClientType.desktop), lastKey, keys.toSeq)
    }
  }

  override def generatePreKeysIfNeeded(remainingKeys: Seq[Int]) = {
    val remaining = remainingKeys.filter(_ <= CryptoBox.MAX_PREKEY_ID)

    val maxId = if (remaining.isEmpty) None else Some(remaining.max)

    // old version was not updating lastPreKeyId properly, we need to detect that and reset it to lastId on backend
    def shouldResetLastIdPref(lastId: Int) = maxId.exists(max => max > lastId && max < LocalPreKeysLimit / 2)

    if (remaining.size > LowPreKeysThreshold) Future.successful(Nil)
    else lastPreKeyId() flatMap { lastId =>
      val startId =
        if (lastId > LocalPreKeysLimit) 0
        else if (shouldResetLastIdPref(lastId)) maxId.fold(0)(_ + 1)
        else lastId + 1

      val count = PreKeysCount - remaining.size

      apply { cb =>
        val keys = cb.newPreKeys(startId, count).toSeq
        (lastPreKeyId := keys.last.id) map (_ => keys)
      } map { _ getOrElse Nil }
    }
  }
}

object CryptoBoxService {
  val PreKeysCount = 100
  val LowPreKeysThreshold = 50
  val LocalPreKeysLimit = 16 * 1024
}
