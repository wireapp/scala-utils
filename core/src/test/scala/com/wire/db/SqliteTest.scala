package com.wire.db

import java.io.File
import java.sql.DriverManager

import com.wire.assets.{AssetData, DefaultAssetStorage}
import com.wire.data.{AssetId, JsonDecoder, JsonEncoder}
import com.wire.testutils.FullFeatureSpec

import scala.concurrent.Await

class SqliteTest extends FullFeatureSpec {

  lazy val db: Database = new SQLiteDatabase(new File("core/src/test/resources/database.db"))

  private def resetTable() = {
    db.execSQL(
      """
        |drop table if exists person;
        |create table person (id integer, name string);
        |insert into person values(1, 'leo');
        |insert into person values(2, 'yui');
      """.stripMargin
    )
  }

  scenario("query all") {
    resetTable()
    val cur = db.query("person")

    Seq((1, "leo"), (2, "yui")).foreach { case (id, name) =>
      cur.moveToNext()
      cur.getString("name") shouldEqual name
      cur.getInt("id") shouldEqual id
    }
    cur.moveToNext() shouldEqual false
    cur.close()
  }

  scenario("query selection") {
    resetTable()
    val cur = db.query("person", Set("name"), "name = ?", Seq("leo"))

    cur.moveToNext()
    cur.getString("name") shouldEqual "leo"
    cur.moveToNext() shouldEqual false
    cur.close()
  }

  scenario("query all columns with selection") {
    val cur = db.query("person", selection = "name = ? OR name = ?", selectionArgs = Seq("leo"))

    cur.moveToNext()
    cur.getString("name") shouldEqual "leo"
    cur.getInt("id") shouldEqual 1

    cur.moveToNext() shouldEqual false
    cur.close()
  }

  scenario("Multiple queries") {
    val cur = db.query("person")
    cur.close()

    val cur2 = db.query("person")
    cur2.close()
  }

  scenario("What happens with two connections?") {

    val c1 = DriverManager.getConnection(s"jdbc:sqlite:core/src/test/resources/database.db")
    val c2 = DriverManager.getConnection(s"jdbc:sqlite:core/src/test/resources/database.db")

  }


  scenario("Add some stuff to cached storage") {


    val storage = new DefaultAssetStorage(db)

    storage.insert(AssetData())
    storage.insert(AssetData())

    Thread.sleep(2000)
  }



}
