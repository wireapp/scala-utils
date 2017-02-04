package com.wire.db

import java.io.File
import java.sql.{DriverManager, ResultSet}

import com.wire.assets.AssetData
import com.wire.data.{AssetId, JsonDecoder, JsonEncoder, Managed}
import com.wire.macros.returning
import com.wire.testutils.FullFeatureSpec
import com.wire.threading.SerialDispatchQueue

class SqliteTest extends FullFeatureSpec {

  scenario("Figure out this JDBC stuff") {


    val dbFile = new File("core/src/test/resources/database.db")
    new File(dbFile.getParent).mkdir()
    dbFile.createNewFile()


    val connection = Managed(DriverManager.getConnection(s"jdbc:sqlite:${dbFile.getAbsolutePath}"))

    connection.foreach { c =>
      val rs = returning(c.createStatement()) { st =>
        st.setQueryTimeout(30)
        st.executeUpdate("drop table if exists person")
        st.executeUpdate("create table person (id integer, name string)")
        st.executeUpdate("insert into person values(1, 'leo')")
        st.executeUpdate("insert into person values(2, 'yui')")
        st.executeUpdate("insert into person values(3, 'woop')")
      }.executeQuery("select * from person")

      while (rs.next()) {
        println(s"name: ${rs.getString("name")}")
        println(s"id: ${rs.getInt("id")}")
      }
    }
  }

  scenario("Be one with the Dao") {

    implicit object AssetDataDao extends Dao[AssetData, AssetId] {
      import Col._
      val Id    = id[AssetId]('_id, "PRIMARY KEY").apply(_.id)
      val Data = text('data)(JsonEncoder.encodeString(_))

      override val idCol = Id
      override val table = Table("Assets", Id, Data)

      override def apply(implicit cursor: ResultSet): AssetData = JsonDecoder.decode(Data)(AssetData.AssetDataDecoder)
    }

    implicit val db = new Database {
      override implicit val dispatcher = new SerialDispatchQueue(name = "Test Database")
      override def setTransactionSuccessful() = ???
      override def endTransaction() = ???
      override def beginTransactionNonExclusive() = ???
      override def isInTransaction = ???
      override def close() = ???
      override def execSQL(createSql: String) = ???
      override def query(tableName: String, columns: Set[String], selection: String, selectionArgs: Seq[String], groupBy: String, having: String, orderBy: String, limit: String) = ???
      override def delete(tableName: String, whereClaus: String, whereArgs: Seq[String]) = ???
    }

    AssetDataDao.list

  }

}
