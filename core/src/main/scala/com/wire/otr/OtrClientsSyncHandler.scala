package com.wire.otr

import com.wire.accounts.AccountData.ClientRegistrationState
import com.wire.data.{AccountId, ClientId, UserId}
import com.wire.network.ErrorResponse
import com.wire.reactive.Signal
import com.wire.storage.KeyValueStorage
import com.wire.sync.SyncResult
import sun.security.krb5.internal.NetClient

import scala.concurrent.Future

trait OtrClientsSyncHandler {
  def syncSelfClients(): Future[SyncResult]
  def registerClient(password: Option[String]): Future[Either[ErrorResponse, (ClientRegistrationState, Option[Client])]]
}

class DefaultOtrClientsSyncHandler(accountId:  AccountId,
                                   userId:     UserId,
                                   clientId:   Signal[Option[ClientId]],
                                   netClient:  OtrClient,
                                   otrClients: OtrClientsService,
                                   storage:    OtrClientStorage,
                                   cryptoBox:  CryptoBoxService,
                                   kvStorage:  KeyValueStorage) {


}
