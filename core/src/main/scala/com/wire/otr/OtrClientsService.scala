package com.wire.otr

import com.wire.data.{ClientId, UserId}
import com.wire.logging.ZLog.ImplicitTag._
import com.wire.logging.ZLog.verbose
import com.wire.network.ZNetClient.ErrorOr
import com.wire.otr.Client.Verification
import com.wire.reactive.Signal
import com.wire.sync.SyncServiceHandle
import com.wire.threading.SerialDispatchQueue

import scala.collection.breakOut
import scala.collection.immutable.Map
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

trait OtrClientsService {
//  def requestSyncIfNeeded(retryInterval: FiniteDuration = 7.days): Future[Unit]

//  def deleteClient(id: ClientId, password: String): ErrorOr[Unit]

//  def getClient(id: UserId, client: ClientId): Future[Option[Client]]

//  def getOrCreateClient(id: UserId, client: ClientId): Future[Client]

  def updateClients(ucs: Map[UserId, Seq[Client]], replace: Boolean = false): Future[Set[UserClients]]

  def updateUserClients(user: UserId, clients: Seq[Client], replace: Boolean = false): Future[UserClients]

//  def onCurrentClientRemoved(): Future[Option[(UserClients, UserClients)]]

//  def removeClients(user: UserId, clients: Seq[ClientId]): Future[Option[(UserClients, UserClients)]]

  def updateSelfClients(clients: Seq[Client], replace: Boolean = true): Future[UserClients]

//  def updateClientLabel(id: ClientId, label: String): Future[Any]

//  def selfClient: Signal[Client]

//  def getSelfClient: Future[Option[Client]]
}

class DefaultOtrClientsService(userId: UserId, clientId: Signal[Option[ClientId]], storage: OtrClientStorage, sync: SyncServiceHandle) extends OtrClientsService {

  private implicit val dispatcher = new SerialDispatchQueue()

  override def updateUserClients(user: UserId, clients: Seq[Client], replace: Boolean) = {
    verbose(s"updateUserClients($user, $clients, $replace)")
    updateClients(Map(user -> clients), replace).map(_.head)
  }

  override def updateSelfClients(clients: Seq[Client], replace: Boolean) = clientId.head flatMap { current =>
    updateUserClients(userId, clients.map(c => if (current.contains(c.id)) c.copy(verified = Verification.Verified) else c), replace)
  }

  override def updateClients(ucs: Map[UserId, Seq[Client]], replace: Boolean) = {

    def updateOrCreate(user: UserId, clients: Seq[Client]): (Option[UserClients] => UserClients) = {
      case Some(cs) =>
        val prev = cs.clients
        val updated: Map[ClientId, Client] = clients.map { c => c.id -> prev.get(c.id).fold(c)(_.updated(c)) }(breakOut)
        cs.copy(clients = if (replace) updated else prev ++ updated)
      case None =>
        UserClients(user, clients.map(c => c.id -> c)(breakOut))
    }

    // request clients location sync if some location has no name
    // location will be present only for self clients, but let's check that just to be explicit
//    def requestLocationSyncIfNeeded(uss: Traversable[UserClients]) = {
//      val needsSync = uss.filter(_.clients.values.exists(_.regLocation.exists(!_.hasName)))
//      if (needsSync.nonEmpty)
//        if (needsSync.exists(_.user == userId)) sync.syncClientsLocation() else Future.successful(())
//    }

    storage.updateOrCreateAll(ucs.map { case (u, cs) => u -> updateOrCreate(u, cs) } (breakOut)) //map { res =>
//      requestLocationSyncIfNeeded(res)
//      res
//    }
  }
}