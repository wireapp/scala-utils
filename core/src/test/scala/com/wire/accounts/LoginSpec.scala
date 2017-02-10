package com.wire.accounts

import java.io.File

import com.wire.accounts.AccountData.AccountDataDao
import com.wire.db.{Database, SQLiteDatabase}
import com.wire.testutils.FullFeatureSpec

class LoginSpec extends FullFeatureSpec {

  scenario("Login with a new user and create an AccountData entry") {

    val db: Database = new SQLiteDatabase(new File("core/src/test/resources/database.db")) {
      override val daos = Seq(AccountDataDao)
    }






  }

}
