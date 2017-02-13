package com.wire.accounts

import java.io.File

import com.wire.accounts.AccountData.AccountDataDao
import com.wire.auth.Credentials.EmailCredentials
import com.wire.auth.{Credentials, CredentialsHandler, DefaultLoginClient, EmailAddress}
import com.wire.config.BackendConfig
import com.wire.data.{AccountId, UserId}
import com.wire.db.{Database, SQLiteDatabase}
import com.wire.network.AccessTokenProvider.Token
import com.wire.network.Response.Status
import com.wire.network.{ApacheHTTPAsyncClient, ErrorResponse}
import com.wire.reactive.Signal
import com.wire.storage.{DefaultKVStorage, Preference}
import com.wire.storage.KeyValueData.KeyValueDataDao
import com.wire.testutils.FullFeatureSpec

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Right

class LoginSpec extends FullFeatureSpec {

  scenario("Login with a new user and create an AccountData entry") {

    import com.wire.threading.Threading.Implicits.Background
    import com.wire.reactive.EventContext.Implicits.global

    val db: Database = new SQLiteDatabase(new File("core/src/test/resources/global.db")) {
      override val daos = Seq(AccountDataDao, KeyValueDataDao)
    }
//    db.dropAllTables()
//    db.onCreate()

    val prefs = new DefaultKVStorage(db)
    val accounts = new AccountStorage(db)

    val loginClient = new DefaultLoginClient(new ApacheHTTPAsyncClient(), BackendConfig.StagingBackend)

    val currentAccountPref = prefs.keyValuePref[Option[AccountId]]("CurrentUserPref", Option.empty[AccountId])

    val currentAccountData = currentAccountPref.signal.flatMap {
      case Some(id) => accounts.optSignal(id)
      case None => Signal.const(Option.empty[AccountData])
    }

    currentAccountData {
      case Some(accountData) => Some(new SQLiteDatabase(new File(s"core/src/test/resources/${accountData.userId}.db")))
      case None => None
    }

    val id = AccountId("b7fb0eff-4035-46a6-a60d-d4c88d7d70bd")

    val credentialsHandler = new CredentialsHandler {
      override val userId: AccountId = id
      override val cookie: Preference[Option[String]] = Preference[Option[String]](None, accounts.get(id).map(_.flatMap(_.cookie)), { c => accounts.update(id, _.copy(cookie = c)) })
      override val accessToken: Preference[Option[Token]] = Preference[Option[Token]](None, accounts.get(id).map(_.flatMap(_.accessToken)), { token => accounts.update(id, _.copy(accessToken = token)) })
      override def credentials: Credentials = EmailCredentials(Some(EmailAddress("dean+2@wire.com")), Some("aqa123456"))
      override def onInvalidCredentials(): Unit = println("Should logout here!")
    }


    Await.ready(accounts.insert(AccountData(id, credentialsHandler.credentials.email, hash = credentialsHandler.credentials.password.get, password = credentialsHandler.credentials.password, activated = false)), 5.seconds)


    def switchAccount(ch: CredentialsHandler) = {
      println(s"switching to account: ${ch.credentials}")
      for {
        matching   <- accounts.find(ch.credentials)
        account    =  matching.flatMap(_.authorized(ch.credentials))
        _          <- {
          println(s"setAccount($account)")
          currentAccountPref := account.map(_.id)
        }
      } yield
        (ch, matching)
    }.map {
      case (cr, Some(acc)) =>
        println("activating")
        activate(acc, cr)
      case _ => println("no account")
    }


    def activate(account: AccountData, ch: CredentialsHandler): Future[Either[ErrorResponse, AccountData]] =
      if (account.activated) Future successful Right(account)
      else loginClient.login(account.id, ch.credentials).future flatMap {
        case Right((token, cookie)) =>
          for {
            _ <- ch.cookie := cookie
            _ <- ch.accessToken := Some(token)
            acc <- accounts.updateOrCreate(account.id, _.copy(activated = true, cookie = cookie, accessToken = Some(token)), account.copy(activated = true, cookie = cookie, accessToken = Some(token)))
          } yield Right(acc)
        case Left(ErrorResponse(Status.Forbidden, _, "pending-activation")) =>
          Future successful Right(account.copy(activated = false))
        case Left(err) =>
          println(s"activate failed: $err")
          Future successful Left(err)
      }

    switchAccount(credentialsHandler)


    Thread.sleep(5000)
  }
}
