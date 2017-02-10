package com.wire.accounts

import java.io.File

import com.wire.accounts.AccountData.AccountDataDao
import com.wire.data.AccountId
import com.wire.db.{Database, SQLiteDatabase}
import com.wire.testutils.FullFeatureSpec

class AccountStorageSpec extends FullFeatureSpec {


  scenario("Add some account") {


    val db: Database = new SQLiteDatabase(new File("core/src/test/resources/database.db")) {
      override val daos = Seq(AccountDataDao)
    }

    db.dropAllTables()

    AccountDataDao.onCreate(db)

    val storage = new AccountStorage(db)

    storage.insert(new AccountData(AccountId()))

    Thread.sleep(2000)
  }
}
