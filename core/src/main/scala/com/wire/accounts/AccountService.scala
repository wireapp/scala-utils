package com.wire.accounts

import com.wire.accounts.AccountData.ClientRegistrationState
import com.wire.auth.{Credentials, CredentialsHandler}
import com.wire.data.AccountId
import com.wire.logging.ZLog.ImplicitTag._
import com.wire.logging.ZLog.{info, verbose}
import com.wire.macros.returning
import com.wire.network.AccessTokenProvider.Token
import com.wire.network.ErrorResponse
import com.wire.network.Response.Status
import com.wire.network.ZNetClient.ErrorOrResponse
import com.wire.otr.Client
import com.wire.reactive.Signal
import com.wire.storage.Preference
import com.wire.threading.{CancellableFuture, Serialized}
import com.wire.users.UserData

import scala.concurrent.Future
import scala.util.Right

class AccountService(account: AccountData, manager: AccountsManager) {

  import manager._

  private val id = account.id

  private val accountData = manager.accStorage.signal(id)

  lazy val storage: StorageModule = returning(accFactory.userStorage(id)) { storage =>

    account.userId foreach { userId =>
      // ensure that self user is present on start
      storage.users.updateOrCreate(userId, identity, UserData(userId, "", account.email, account.phone, connection = UserData.ConnectionStatus.self, handle = account.handle))
    }

    val selfUserData = accountData.map(_.userId).flatMap {
      case Some(userId) => storage.users.optSignal(userId)
      case None => Signal const Option.empty[UserData]
    }

    // listen to user data changes, update account email/phone if self user data is changed
    selfUserData.collect {
      case Some(user) => (user.email, user.phone)
    } { case (email, phone) =>
      verbose(s"self user data changed, email: $email, phone: $phone")
      accStorage.update(id, _.copy(email = email, phone = phone))
    }

    selfUserData.map(_.exists(_.deleted)) { deleted =>
      if (deleted) {
        info(s"self user was deleted, logging out")
        for {
          _ <- logoutAndResetClient()
          _ = accountMap.remove(id)
          _ <- accStorage.remove(id)
        // TODO: delete database, account was deleted
        } yield ()
      }
    }

    // listen to client changes, logout and delete cryptobox if current client is removed
    val otrClient = accountData.map(a => (a.userId, a.clientId)).flatMap {
      case (Some(userId), Some(cId)) => storage.otrClients.optSignal(userId).map(_.flatMap(_.clients.get(cId)))
      case _ => Signal const Option.empty[Client]
    }
    var hasClient = false
    otrClient.map(_.isDefined) { exists =>
      if (hasClient && !exists) {
        info(s"client has been removed on backend, logging out")
        logoutAndResetClient()
      }
      hasClient = exists
    }
  }

  lazy val credentialsHandler = new CredentialsHandler {
    override val userId: AccountId = id
    override val cookie: Preference[Option[String]] = Preference[Option[String]](None, accStorage.get(id).map(_.flatMap(_.cookie)), { c => accStorage.update(id, _.copy(cookie = c)) })
    override val accessToken: Preference[Option[Token]] = Preference[Option[Token]](None, accStorage.get(id).map(_.flatMap(_.accessToken)), { token => accStorage.update(id, _.copy(accessToken = token)) })

    override def credentials: Credentials = account.credentials

    override def onInvalidCredentials(): Unit = logout()
  }

  lazy val userStorage = storage.users
  lazy val assetsStorage = storage.assets

  lazy val cryptoBox = accFactory.cryptoBox(id, storage)
  lazy val znetClient = accFactory.znetClient(credentialsHandler)
  lazy val usersClient = accFactory.usersClient(znetClient)
  lazy val clientsSync = accFactory.clientsSync
  lazy val sync = accFactory.syncServiceHandle


  def login(credentials: Credentials): Future[Either[ErrorResponse, AccountData]] =
    Serialized.future(this) {
      verbose(s"login($credentials)")
      accStorage.updateOrCreate(id, _.updated(credentials), account.updated(credentials)) flatMap { _ => ensureFullyRegistered() }
    }

  def logout(): Future[Unit] = {
    verbose(s"logout($id)")
    manager.logout(id)
  }

  private def ensureFullyRegistered(): Future[Either[ErrorResponse, AccountData]] = {
    verbose(s"ensureFullyRegistered()")

    def loadSelfUser(account: AccountData): Future[Either[ErrorResponse, AccountData]] =
      if (account.userId.isDefined) Future successful Right(account)
      else {
        usersClient.loadSelf().future flatMap {
          case Right(userInfo) =>
            verbose(s"got self user info: $userInfo")
            for {
              _ <- assetsStorage.mergeOrCreateAsset(userInfo.mediumPicture)
              _ <- userStorage.updateOrCreate(userInfo.id, _.updated(userInfo).copy(syncTimestamp = System.currentTimeMillis()), UserData(userInfo).copy(connection = UserData.ConnectionStatus.self, syncTimestamp = System.currentTimeMillis()))
              res <- accStorage.updateOrCreate(id, _.updated(userInfo), account.updated(userInfo))
            } yield Right(res)
          case Left(err) =>
            verbose(s"loadSelfUser failed: $err")
            Future successful Left(err)
        }
      }

    def checkCryptoBox =
      cryptoBox.cryptoBox flatMap {
        case Some(cb) => Future successful Some(cb)
        case None =>
          for {
            _ <- accStorage.update(id, _.copy(clientId = None, clientRegState = ClientRegistrationState.Unknown))
            _ <- cryptoBox.deleteCryptoBox()
            res <- cryptoBox.cryptoBox
          } yield res
      }

    checkCryptoBox flatMap {
      case None => Future successful Left(ErrorResponse.internalError("CryptoBox loading failed"))
      case Some(_) =>
        accStorage.get(id) flatMap {
          case None => Future successful Left(ErrorResponse.internalError(s"Missing AccountData for id: $id"))
          case Some(acc) =>
            activate(acc).future flatMap {
              case Right(acc1) if acc1.activated =>
                loadSelfUser(acc1) flatMap {
                  case Right(acc2) =>
                    // TODO: check if there is some other AccountData with the same userId already present
                    // it may happen that the same account was previously used with different credentials
                    // we should merge those accounts, delete current one, and switch to the previous one
                    ensureClientRegistered(acc2) flatMap {
                      case Right(acc3) =>
                        accStorage.updateOrCreate(id, _.updated(acc3.userId, acc3.activated, acc3.clientId, acc3.clientRegState), acc3) map {
                          Right(_)
                        }
                      case Left(err) => Future successful Left(err)
                    }
                  case Left(err) => Future successful Left(err)
                }
              case Right(acc1) => Future successful Right(acc1)
              case Left(err) => Future successful Left(err)
            }
        }
    }
  }

  private def ensureClientRegistered(account: AccountData): Future[Either[ErrorResponse, AccountData]] = {
    import ClientRegistrationState._
    if (account.clientId.isDefined) Future successful Right(account)
    else {
      clientsSync.registerClient(account.password) map {
        case Right((Registered, Some(cl))) =>
          Right(account.copy(clientId = Some(cl.id), clientRegState = Registered, activated = true))
        case Right((state, _)) =>
          sync.syncSelfClients() // request clients com.wire.sync, UI will need that
          Right(account.copy(clientRegState = state))
        case Left(err) =>
          error(s"client registration failed: $err")
          Left(err)
      }
    }
  }


  private def activate(account: AccountData): ErrorOrResponse[AccountData] =
    if (account.activated) CancellableFuture successful Right(account)
    else loginClient.login(id, account.credentials) flatMap {
      case Right((token, cookie)) =>
        CancellableFuture lift {
          for {
            _ <- credentialsHandler.cookie := cookie
            _ <- credentialsHandler.accessToken := Some(token)
            acc <- accStorage.updateOrCreate(id, _.copy(activated = true, cookie = cookie, accessToken = Some(token)), account.copy(activated = true, cookie = cookie, accessToken = Some(token)))
          } yield Right(acc)
        }
      case Left(ErrorResponse(Status.Forbidden, _, "pending-activation")) =>
        CancellableFuture successful Right(account.copy(activated = false))
      case Left(err) =>
        verbose(s"activate failed: $err")
        CancellableFuture successful Left(err)
    }

  private def logoutAndResetClient() =
    for {
      _ <- logout()
      _ <- cryptoBox.deleteCryptoBox()
      _ <- accStorage.update(id, _.copy(clientId = None, clientRegState = ClientRegistrationState.Unknown))
    } yield ()

}
