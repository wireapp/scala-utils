package com.wire.accounts

import java.io.File

import com.wire.accounts.AccountData.AccountDataDao
import com.wire.data.AccountId
import com.wire.db.{Database, SQLiteDatabase}
import com.wire.testutils.FullFeatureSpec

class AssetStorageSpec extends FullFeatureSpec {


  scenario("Add some account") {

    val db: Database = new SQLiteDatabase(new File("core/src/test/resources/database.db"))

    AccountDataDao.onCreate(db)

    val storage = new AccountStorage(db)

    storage.insert(new AccountData(AccountId()))


    Thread.sleep(2000)
  }
}
