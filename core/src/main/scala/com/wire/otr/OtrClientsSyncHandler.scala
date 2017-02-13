package com.wire.otr

import com.wire.accounts.AccountData.ClientRegistrationState
import com.wire.network.ErrorResponse
import com.wire.sync.SyncResult

import scala.concurrent.Future

trait OtrClientsSyncHandler {
  def syncSelfClients(): Future[SyncResult]
  def registerClient(password: Option[String]): Future[Either[ErrorResponse, (ClientRegistrationState, Option[Client])]]
}
