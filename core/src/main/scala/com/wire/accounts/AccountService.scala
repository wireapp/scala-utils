package com.wire.accounts

import com.wire.Lifecycle
import com.wire.accounts.AccountData.ClientRegistrationState
import com.wire.auth.AuthenticationManager.Cookie
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
import com.wire.reactive.{EventContext, Signal}
import com.wire.storage.Preference
import com.wire.threading.{CancellableFuture, Serialized, Threading}
import com.wire.users.UserData
import com.wire.utils.ExponentialBackoff

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Right

class AccountService(@volatile var account: AccountData, manager: AccountsManager) { self =>
  import AccountService._

  import manager._

  private[accounts] val id = account.id

  private val accountData = manager.accStorage.signal(id)

  val lifecycle = new Lifecycle()

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

  @volatile
  private[accounts] var credentials = Credentials.Empty

  lazy val credentialsHandler = new CredentialsHandler {
    override val userId: AccountId = id
    override val cookie: Preference[Option[Cookie]] = Preference[Option[Cookie]](None, accStorage.get(id).map(_.flatMap(_.cookie)), { c => accStorage.update(id, _.copy(cookie = c)) })
    override val accessToken: Preference[Option[Token]] = Preference[Option[Token]](None, accStorage.get(id).map(_.flatMap(_.accessToken)), { token => accStorage.update(id, _.copy(accessToken = token)) })

    override def credentials: Credentials = self.credentials

    override def onInvalidCredentials(): Unit = logout()
  }

  lazy val userStorage = storage.users
  lazy val assetsStorage = storage.assets

  lazy val cryptoBox = accFactory.cryptoBox(id, storage)
  lazy val znetClient = accFactory.zNetClient(credentialsHandler)
  lazy val usersClient = accFactory.usersClient(znetClient)
  lazy val clientsSync = accFactory.clientsSync
  lazy val sync = accFactory.syncServiceHandle

  val isLoggedIn = currentAccountPref.signal.map(_.contains(id))

  isLoggedIn.on(Threading.Ui) { lifecycle.setLoggedIn }

  accountData { acc =>
    verbose(s"acc: $acc")
    account = acc
    if (acc.cookie.isDefined) {
      if (credentials == Credentials.Empty) credentials = acc.credentials
    }
  } (EventContext.Global)

  accountData.zip(isLoggedIn) {
    case (acc, loggedIn) =>
      verbose(s"accountData: $acc, loggedIn: $loggedIn")
      if (loggedIn && acc.activated && (acc.userId.isEmpty || acc.clientId.isEmpty)) {
        verbose(s"account data needs registration: $acc")
        Serialized.future(self) { ensureFullyRegistered() }
      }
  }

  private var awaitActivationFuture = CancellableFuture successful Option.empty[AccountData]

  private val shouldAwaitActivation = lifecycle.uiActive.zip(accStorage.optSignal(id)) map {
    case (true, Some(acc)) => !acc.activated && acc.password.isDefined
    case _ => false
  }

  shouldAwaitActivation.on(dispatcher) {
    case true   => awaitActivationFuture = awaitActivationFuture.recover { case _: Throwable => () } flatMap { _ => awaitActivation(0) }
    case false  => awaitActivationFuture.cancel()("stop_await_activate")
  }

  lifecycle.lifecycleState { state => verbose(s"lifecycle state: $state") }

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

    def ensureClientRegistered(account: AccountData): Future[Either[ErrorResponse, AccountData]] = {
      import ClientRegistrationState._
      if (account.clientId.isDefined) {
        verbose(s"Client: ${account.clientId} already registered")
        Future successful Right(account)
      }
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
      case Left((_, ErrorResponse(Status.Forbidden, _, "pending-activation"))) =>
        CancellableFuture successful Right(account.copy(activated = false))
      case Left((_, err)) =>
        verbose(s"activate failed: $err")
        CancellableFuture successful Left(err)
    }

  private def logoutAndResetClient() =
    for {
      _ <- logout()
      _ <- cryptoBox.deleteCryptoBox()
      _ <- accStorage.update(id, _.copy(clientId = None, clientRegState = ClientRegistrationState.Unknown))
    } yield ()

  private def awaitActivation(retry: Int = 0): CancellableFuture[Option[AccountData]] =
    CancellableFuture lift accStorage.get(id) flatMap {
      case None => CancellableFuture successful None
      case Some(data) if data.activated => CancellableFuture successful Some(data)
      case Some(_) if !lifecycle.isUiActive => CancellableFuture successful None
      case Some(data) =>
        activate(data) flatMap {
          case Right(acc) if acc.activated => CancellableFuture successful Some(acc)
          case _ =>
            CancellableFuture.delay(ActivationThrottling.delay(retry)) flatMap { _ => awaitActivation(retry + 1) }
        }
    }

}

object AccountService {
  val ActivationThrottling = new ExponentialBackoff(2.seconds, 15.seconds)
}
