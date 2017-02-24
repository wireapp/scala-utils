package com.wire.otr

import com.wire.cryptobox.{CryptoBox, CryptoSession, PreKey}
import com.wire.error.LoggedTry
import com.wire.logging.ZLog.ImplicitTag._
import com.wire.logging.ZLog.verbose
import com.wire.logging.ZLog.error
import com.wire.macros.returning
import com.wire.reactive.{AggregatingSignal, EventStream}
import com.wire.threading.SerialDispatchQueue
import org.apache.commons.codec.binary.Base64

import scala.concurrent.Future

class CryptoSessionService(cryptoBox: CryptoBoxService) {
  private val dispatchers = Array.fill(17)(new SerialDispatchQueue())

  val onCreate = EventStream[String]()
  val onCreateFromMessage = EventStream[String]()

  private def dispatcher(id: String) = dispatchers(math.abs(id.hashCode) % dispatchers.length)

  private def dispatch[A](id: String)(f: Option[CryptoBox] => A) = cryptoBox.cryptoBox.map(f) (dispatcher(id))

  def getOrCreateSession(id: String, key: PreKey) = dispatch(id) {
    case None => None
    case Some(cb) =>
      verbose(s"getOrCreateSession($id)")
      def createSession() = returning(cb.initSessionFromPreKey(id, key))(_ => onCreate ! id)

      loadSession(cb, id).getOrElse(createSession())
  }

  private def loadSession(cb: CryptoBox, id: String): Option[CryptoSession] =
    LoggedTry(Option(cb.tryGetSession(id))).getOrElse {
      error("session loading failed unexpectedly, will delete session file")
      cb.deleteSession(id)
      None
    }

  def deleteSession(id: String) = dispatch(id) { cb =>
    verbose(s"deleteSession($id)")
    cb foreach (_.deleteSession(id))
  }

  def getSession(id: String) = dispatch(id) { cb =>
    verbose(s"getSession($id)")
    cb.flatMap(loadSession(_, id))
  }

  def withSession[A](id: String)(f: CryptoSession => A): Future[Option[A]] = dispatch(id) { cb =>
    cb.flatMap(loadSession(_, id)) map { session =>
      returning(f(session)) { _ => session.save() }
    }
  }

  def decryptMessage(sessionId: String, msg: Array[Byte]): Future[Array[Byte]] = dispatch(sessionId) {
    case None => throw new Exception("CryptoBox missing")
    case Some(cb) =>
      verbose(s"decryptMessage($sessionId for message: ${msg.length} = ${Base64.encodeBase64String(msg)})")

      val (session, plain) =
        loadSession(cb, sessionId).fold {
          val sm = cb.initSessionFromMessage(sessionId, msg)
          onCreate ! sessionId
          onCreateFromMessage ! sessionId
          (sm.getSession, sm.getMessage)
        } { s =>
          (s, s.decrypt(msg))
        }
      session.save()
      plain
  }

  def remoteFingerprint(sid: String) = {
    def fingerprint = withSession(sid)(_.getRemoteFingerprint)
    val stream = onCreate.filter(_ == sid).mapAsync(_ => fingerprint)

    new AggregatingSignal[Option[Array[Byte]], Option[Array[Byte]]](stream, fingerprint, (prev, next) => next)
  }


}
