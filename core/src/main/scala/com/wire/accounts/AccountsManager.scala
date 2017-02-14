package com.wire.accounts


import com.wire.accounts.AccountsManager.AccountsFactory
import com.wire.auth._
import com.wire.data.AccountId
import com.wire.logging.ZLog.ImplicitTag._
import com.wire.logging.ZLog.{debug, info, verbose}
import com.wire.network.{ErrorResponse, ZNetClient}
import com.wire.network.Response.Status
import com.wire.otr.{CryptoBoxService, OtrClientsSyncHandler}
import com.wire.reactive.{EventContext, Signal}
import com.wire.storage.KeyValueStorage
import com.wire.sync.SyncServiceHandle
import com.wire.threading.SerialDispatchQueue
import com.wire.users.{AccentColor, UsersClient}

import scala.collection.mutable
import scala.concurrent.Future

class AccountsManager(prefs:             KeyValueStorage,
                      val accStorage:    AccountStorage,
                      val accFactory: AccountsFactory,
                      regClient:         RegistrationClient,
                      val loginClient:   LoginClient) {

  import AccountsManager._
  implicit val dispatcher = new SerialDispatchQueue()

  private[accounts] implicit val ec = EventContext.Global

  private[accounts] val accountMap = new mutable.HashMap[AccountId, AccountService]()

  val currentAccountPref = prefs.keyValuePref(CurrentAccountPref, Option.empty[AccountId])

  lazy val currentAccountData = currentAccountPref.signal.flatMap[Option[AccountData]] {
    case None => Signal.const(Option.empty[AccountData])
    case Some(idStr) => accStorage.optSignal(idStr)
  }

  lazy val current = currentAccountData.flatMap[Option[AccountService]] {
    case None      => Signal const None
    case Some(acc) => Signal.future(getInstance(acc) map (Some(_)))
  }

  private def getInstance(account: AccountData) = Future {
    accountMap.getOrElseUpdate(account.id, new AccountService(account, this))
  }

  def logout() = current.head flatMap {
    case Some(account) => account.logout()
    case None => Future.successful(())
  }

  def logout(account: AccountId) = currentAccountPref() flatMap {
    case Some(id) if id == account => setAccount(None) map (_ => ())
    case id =>
      verbose(s"logout($account) ignored, current id: $id")
      Future.successful(())
  }

  private def setAccount(acc: Option[AccountId]) = {
    verbose(s"setAccount($acc)")
    currentAccountPref := acc
  }

  private def switchAccount(credentials: Credentials) = {
    verbose(s"switchAccount($credentials)")
    for {
      _          <- logout()
      normalized <- normalizeCredentials(credentials)
      matching   <- accStorage.find(normalized)
      account    =  matching.flatMap(_.authorized(normalized))
      _          <- setAccount(account.map(_.id))
      service    <- account.fold(Future successful Option.empty[AccountService]) { a => getInstance(a).map(Some(_)) }
    } yield
      (normalized, matching, service)
  }


  def login(credentials: Credentials): Future[Either[ErrorResponse, AccountData]] =
    switchAccount(credentials) flatMap {
      case (normalized, _, Some(service)) => service.login(normalized)
      case (normalized, Some(account), None) => // found matching account, but is not authorized (wrong password)
        verbose(s"found matching account: $account, trying to authorize with backend")
        login(account, normalized)
      case (normalized, None, None) =>
        verbose(s"matching account not found, creating new account")
        login(new AccountData(AccountId(), None, "", None, handle = None), normalized)
    }

  private def login(account: AccountData, normalized: Credentials) = {
    def loginOnBackend() =
      loginClient.login(account.id, normalized).future map {
        case Right((token, c)) =>
          Right(account.updated(normalized).copy(cookie = c, activated = true, accessToken = Some(token)))
        case Left(error @ ErrorResponse(Status.Forbidden, _, "pending-activation")) =>
          verbose(s"account pending activation: $normalized, $error")
          Right(account.updated(normalized).copy(activated = false, cookie = None, accessToken = None))
        case Left(error) =>
          verbose(s"login failed: $error")
          Left(error)
      }

    loginOnBackend() flatMap {
      case Right(a) =>
        for {
          acc     <- accStorage.updateOrCreate(a.id, _.updated(normalized).copy(cookie = a.cookie, activated = true, accessToken = a.accessToken), a)
          service <- getInstance(acc)
          _       <- setAccount(Some(acc.id))
          res     <- service.login(normalized)
        } yield res
      case Left(err) =>
        Future successful Left(err)
    }
  }

  def requestVerificationEmail(email: EmailAddress): Unit = regClient.requestVerificationEmail(email)

//  def requestPhoneConfirmationCode(phone: PhoneNumber, kindOfAccess: KindOfAccess): CancellableFuture[ActivateResult] =
//    CancellableFuture.lift(phoneNumbers.normalize(phone)) flatMap { normalizedPhone =>
//      regClient.requestPhoneConfirmationCode(normalizedPhone.getOrElse(phone), kindOfAccess)
//    }

//  def requestPhoneConfirmationCall(phone: PhoneNumber, kindOfAccess: KindOfAccess): CancellableFuture[ActivateResult] =
//    CancellableFuture.lift(phoneNumbers.normalize(phone)) flatMap { normalizedPhone =>
//      regClient.requestPhoneConfirmationCall(normalizedPhone.getOrElse(phone), kindOfAccess)
//    }

//  def verifyPhoneNumber(phone: PhoneCredentials, kindOfVerification: KindOfVerification): ErrorOrResponse[Unit] =
//    CancellableFuture.lift(phoneNumbers.normalize(phone.phone)) flatMap { normalizedPhone =>
//      regClient.verifyPhoneNumber(PhoneCredentials(normalizedPhone.getOrElse(phone.phone), phone.code), kindOfVerification)
//    }

  private def normalizeCredentials(credentials: Credentials): Future[Credentials] = credentials match {
//    case cs @ PhoneCredentials(p, _, _) =>
//      phoneNumbers.normalize(p) map { normalized => cs.copy(phone = normalized.getOrElse(p)) }
    case other =>
      Future successful other
  }

  def register(credentials: Credentials, name: String, accent: AccentColor): Future[Either[ErrorResponse, AccountData]] = {
    debug(s"register($credentials, $name, $accent")

    def register(accountId: AccountId, normalized: Credentials) =
      regClient.register(accountId, normalized, name, Some(accent.id)).future flatMap {
        case Right((userInfo, cookie)) =>
          verbose(s"register($credentials) done, id: $accountId, user: $userInfo, cookie: $cookie")
          for {
            acc     <- accStorage.insert(AccountData(accountId, normalized).copy(cookie = cookie, userId = Some(userInfo.id), activated = normalized.autoLoginOnRegistration))
            _       = verbose(s"created account: $acc")
            service <- getInstance(acc)
            _       <- setAccount(Some(accountId))
            res     <- service.login(normalized)
          } yield res
        case Left(error) =>
          info(s"register($credentials, $name) failed: $error")
          Future successful Left(error)
      }

    switchAccount(credentials) flatMap {
      case (normalized, _, Some(service)) =>
        verbose(s"register($credentials), found matching account: $service, will just sign in")
        service.login(normalized)
      case (normalized, Some(account), None) =>
        verbose(s"register($credentials), found matching account: $account, will try signing in")
        login(account, normalized) flatMap {
          case Right(acc) => Future successful Right(acc)
          case Left(_) =>
            // login failed, maybe this account has been removed on backend, let's try registering
            register(account.id, normalized)
        }
      case (normalized, None, None) =>
        register(AccountId(), normalized)
    }
  }
}

object AccountsManager {
  val CurrentAccountPref = "CurrentUserPref"

  trait AccountsFactory {
    def userStorage(accountId: AccountId): StorageModule

    def cryptoBox(accountId: AccountId, storageModule: StorageModule): CryptoBoxService

    def usersClient(zNetClient: ZNetClient): UsersClient

    def znetClient(credentialsHandler: CredentialsHandler): ZNetClient

    def syncServiceHandle: SyncServiceHandle

    def clientsSync: OtrClientsSyncHandler
  }
}
