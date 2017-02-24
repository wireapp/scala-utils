package com.wire.otr

import java.io.File

import com.wire.assets.AssetData.AssetDataDao
import com.wire.data.AccountId
import com.wire.db.SQLiteDatabase
import com.wire.macros.returning
import com.wire.otr.UserClients.UserClientsDao
import com.wire.storage.DefaultKVStorage
import com.wire.storage.KeyValueData.KeyValueDataDao
import com.wire.testutils.FullFeatureSpec
import com.wire.threading.Threading
import com.wire.users.UserData.UserDataDao

import scala.concurrent.Await
import scala.concurrent.duration._

class CryptoBoxServiceSpec extends FullFeatureSpec {

  implicit val dispatcher = Threading.Background

  scenario("Setup cryptobox test") {

    val dbFile = returning(new File("core/src/test/resources/databases/global.db"))(_.delete)
    val accountId = AccountId()
    val db = new SQLiteDatabase(new File(s"core/src/test/resources/databases/${accountId.str}.db"), Seq(UserDataDao, AssetDataDao, KeyValueDataDao, UserClientsDao))
    val prefs = new DefaultKVStorage(db)

    val crypto = new DefaultCryptoBoxService(AccountId(), prefs, new File(s"core/src/test/resources/otr"))

    println(Await.result(crypto.cryptoBox.map(_.get.getLocalFingerprint).map(new String(_)), 5.seconds))


  }

}
