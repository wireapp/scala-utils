package com.wire.accounts

import java.io.File

import com.wire.accounts.AccountData.{AccountDataDao, ClientRegistrationState}
import com.wire.accounts.AccountsManager.AccountsFactory
import com.wire.assets.DefaultAssetStorage
import com.wire.auth.Credentials.EmailCredentials
import com.wire.auth._
import com.wire.config.BackendConfig
import com.wire.data.{AccountId, ClientId, SyncId, UserId}
import com.wire.db.{Database, SQLiteDatabase}
import com.wire.network.{ApacheHTTPAsyncClient, DefaultZNetClient, ZNetClient}
import com.wire.otr._
import com.wire.storage.DefaultKVStorage
import com.wire.storage.KeyValueData.KeyValueDataDao
import com.wire.sync.{SyncResult, SyncServiceHandle}
import com.wire.testutils.FullFeatureSpec
import com.wire.threading.{CancellableFuture, Threading}
import com.wire.users.{DefaultUserStorage, UserInfo, UsersClient}

import scala.concurrent.Future

class LoginSpec extends FullFeatureSpec {


  scenario("Login with a new user and create an AccountData entry") {

    implicit val ex = Threading.Background

    val db: Database = new SQLiteDatabase(new File("core/src/test/resources/global.db")) {
      override val daos = Seq(AccountDataDao, KeyValueDataDao)
    }

//    db.dropAllTables()
//    db.onCreate()

    val prefs = new DefaultKVStorage(db)
    val accStorage = new AccountStorage(db)

    val asyncClient = new ApacheHTTPAsyncClient()
    val loginClient = new DefaultLoginClient(asyncClient, BackendConfig.StagingBackend)


    val accountsManager = new AccountsManager(prefs, accStorage,
      new AccountsFactory {
      override def znetClient(credentialsHandler: CredentialsHandler) = new DefaultZNetClient(credentialsHandler, asyncClient, BackendConfig.StagingBackend, loginClient)

      override def userStorage(accountId: AccountId) = new StorageModule {

        override lazy val db = new SQLiteDatabase(new File(s"core/src/test/resources/${accountId.str}.db"))

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

      override def usersClient(zNetClient: ZNetClient) = new UsersClient {
        override def loadSelf() = {
          println("loadSelf called")
          CancellableFuture(Right(UserInfo(UserId())))
        }
      }

      override def syncServiceHandle = new SyncServiceHandle {
        override def syncSelfClients() = Future(SyncId())
      }

      override def cryptoBox(accountId: AccountId, storageModule: StorageModule) = new CryptoBoxService {
        override def deleteCryptoBox() = {
          println("deleting crypto box")
          Future.successful({})
        }
        override def cryptoBox = {
          println("accessing cryptobox")
          Future(Some(new CryptoBox {}))
        }
      }
    },

      new RegistrationClient {
      override def register(id: AccountId, creds: Credentials, name: String, accentId: Option[Int]) = ???

      override def requestVerificationEmail(email: EmailAddress) = ???
    }, loginClient)

    accountsManager.login(EmailCredentials(Some(EmailAddress("dean+2@wire.com")), Some("aqa123456")))


    Thread.sleep(5000)
  }
}
