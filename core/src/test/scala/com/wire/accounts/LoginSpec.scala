package com.wire.accounts

import java.io.File

import com.wire.accounts.AccountData.{AccountDataDao, ClientRegistrationState}
import com.wire.accounts.AccountsManager.AccountsFactory
import com.wire.assets.AssetData.AssetDataDao
import com.wire.assets.DefaultAssetStorage
import com.wire.auth.Credentials.EmailCredentials
import com.wire.auth._
import com.wire.config.BackendConfig
import com.wire.data.{AccountId, ClientId, SyncId}
import com.wire.db.SQLiteDatabase
import com.wire.events.EventsClient
import com.wire.macros.returning
import com.wire.network.Response.{DefaultHeaders, HttpStatus}
import com.wire.network._
import com.wire.otr.UserClients.UserClientsDao
import com.wire.otr._
import com.wire.storage.DefaultKVStorage
import com.wire.storage.KeyValueData.KeyValueDataDao
import com.wire.sync.{SyncResult, SyncServiceHandle}
import com.wire.testutils.FullFeatureSpec
import com.wire.threading.{CancellableFuture, SerialDispatchQueue, Threading}
import com.wire.users.UserData.UserDataDao
import com.wire.users.{DefaultUserStorage, DefaultUsersClient, UsersClient}
import org.json.JSONObject

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class LoginSpec extends FullFeatureSpec {

  scenario("Login with a new user and create an AccountData entry") {

    implicit val ex = Threading.Background

    val dbFile = returning(new File("core/src/test/resources/databases/global.db"))(_.delete)
    val db = new SQLiteDatabase(dbFile, Seq(AccountDataDao, KeyValueDataDao))
    val prefs = new DefaultKVStorage(db)
    val accStorage = new AccountStorage(db)

    val newCookie = "newCookie"
    val newToken = "newToken"

    var callCount = 0

    val async = if (true) {

      val newCookieHeaders = DefaultHeaders(Map(
        "set-cookie" -> s"zuid=$newCookie"
      ))

      val newTokenBody = JsonObjectResponse(new JSONObject(
        s"""
           |{
           |  "expires_in": 86400,
           |  "access_token": "$newToken",
           |  "token_type": "Bearer"
           |}
      """.stripMargin
      ))

      val userResponseBody = JsonObjectResponse(new JSONObject(
        s"""
           |{
           |  "assets": [
           |    {
           |      "size": "preview",
           |      "type": "image",
           |      "key": "3-2-c75218cc-4d2b-40b1-840b-75197abbee7c"
           |    },
           |    {
           |      "size": "complete",
           |      "type": "image",
           |      "key": "3-2-9af37c16-9ffa-45ad-ae97-8f50581990f5"
           |    }
           |  ],
           |  "name": "Dean 2",
           |  "accent_id": 1,
           |  "handle": "dean23898",
           |  "id": "fffeb02e-38ec-4110-9048-96d5404ddbd1",
           |  "email": "dean+2@wire.com",
           |}
         """.stripMargin
      ))

      returning(mock[AsyncClient]) { async =>
        (async.apply _)
          .expects(*, *, *, *, *, *, *, *)
          .anyNumberOfTimes()
          .onCall { (uri, method, body, headers, followRedirect, timeout, decoder, downloadProgressCallback) =>
            println(s"${callCount + 1}th call made to ${uri.getPath}")

            val response = uri.getPath match {
              case LoginClient.LoginPath => Response(HttpStatus(Response.Status.Success), newCookieHeaders, newTokenBody)
              case UsersClient.SelfPath => Response(HttpStatus(Response.Status.Success), Response.EmptyHeaders, userResponseBody)
              case _ => fail(s"Unexpected endpoint called: ${uri.getPath}")
            }

            callCount += 1
            CancellableFuture {
              Thread.sleep(500)
              response
            }(new SerialDispatchQueue()("TestAsyncClient"))
          }
      }
    } else new ApacheHTTPAsyncClient()

    val loginClient = new DefaultLoginClient(async, BackendConfig.StagingBackend)
    val accountsManager = new AccountsManager(prefs, accStorage,
      new AccountsFactory {
      override def zNetClient(credentialsHandler: CredentialsHandler) = new DefaultZNetClient(credentialsHandler, async, BackendConfig.StagingBackend, loginClient)

      override def userStorage(accountId: AccountId) = new StorageModule {

        override lazy val db = new SQLiteDatabase(new File(s"core/src/test/resources/databases/${accountId.str}.db"), Seq(UserDataDao, AssetDataDao, KeyValueDataDao, UserClientsDao))

        override lazy val users = new DefaultUserStorage(db)
        override lazy val assets = new DefaultAssetStorage(db)
        override lazy val keyValues = new DefaultKVStorage(db)
        override lazy val otrClients = new DefaultOtrClientStorage(db)
      }

      override def clientsSync = new OtrClientsSyncHandler {

        override def registerClient(password: Option[String]) = {
          println(s"registerClient called with password: $password")
          Future(Right(ClientRegistrationState.Registered, Some(Client(ClientId(), "a device!"))))
        }

        override def syncSelfClients() = {
          println("syncSelfClients called")
          Future(SyncResult.Success)
        }
      }

      override def usersClient(zNetClient: ZNetClient) = new DefaultUsersClient(zNetClient)

      override def syncServiceHandle = new SyncServiceHandle {
        override def syncSelfClients() = Future(SyncId())
      }

      override def cryptoBox(accountId: AccountId, storageModule: StorageModule) =
        new DefaultCryptoBoxService(accountId, storageModule.keyValues, new File("core/src/test/resources/otr"))
    },
      new RegistrationClient {
      override def register(id: AccountId, creds: Credentials, name: String, accentId: Option[Int]) = ???

      override def requestVerificationEmail(email: EmailAddress) = ???
    }, loginClient)

    println(Await.result(accountsManager.login(EmailCredentials(Some(EmailAddress("dean+2@wire.com")), Some("aqa123456"))), 5.seconds))

    Thread.sleep(3000)

    println(Await.result(accountsManager.getCurrent.flatMap {
      case Some(service) => service.znetClient.apply(Request.Get(EventsClient.NotificationsPath, EventsClient.notificationsQuery(None, None, 100))).future
      case _ => fail("No service found...")
    }, 10.seconds))

//    Await.result(accountsManager.logout(res.right.get.id), 5.seconds)
//
//    val res2 = Await.result(accountsManager.login(EmailCredentials(Some(EmailAddress("dean+3@wire.com")), Some("aqa123456"))), 5.seconds)
  }
}
