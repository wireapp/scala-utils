package com.wire.otr

import com.wire.data.{ClientId, UserId}
import com.wire.db.Database
import com.wire.otr.Client.Verification
import com.wire.otr.UserClients.UserClientsDao
import com.wire.reactive.Signal
import com.wire.storage.{CachedStorage, LRUCacheStorage}
import com.wire.logging.ZLog.ImplicitTag._

trait OtrClientStorage extends CachedStorage[UserId, UserClients] {

  def incomingClientsSignal(userId: UserId, clientId: ClientId): Signal[Seq[Client]] =
    signal(userId) map { ucs =>
      ucs.clients.get(clientId).flatMap(_.regTime).fold(Seq.empty[Client]) { current =>
        ucs.clients.values.filter(c => c.verified == Verification.Unknown && c.regTime.exists(_.isAfter(current))).toVector
      }
    }

  def getClients(user: UserId) = get(user).map(_.fold(Seq.empty[Client])(_.clients.values.toVector))

  def updateVerified(userId: UserId, clientId: ClientId, verified: Boolean) = update(userId, { uc =>
    uc.clients.get(clientId) .fold (uc) { client =>
      uc.copy(clients = uc.clients + (client.id -> client.copy(verified = if (verified) Verification.Verified else Verification.Unverified)))
    }
  })
}

class DefaultOtrClientStorage(db: Database) extends LRUCacheStorage[UserId, UserClients](2000, UserClientsDao, db) with OtrClientStorage
