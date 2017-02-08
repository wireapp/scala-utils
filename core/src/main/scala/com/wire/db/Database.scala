/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH

 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.wire.db

import java.io.File
import java.sql.{DriverManager, PreparedStatement}

import com.wire.accounts.AccountData.AccountDataDao
import com.wire.data.Managed
import com.wire.db.Database.ContentValues
import com.wire.logging.ZLog.ImplicitTag._
import com.wire.logging.ZLog.verbose
import com.wire.threading.{SerialDispatchQueue, Threading}

import scala.concurrent.Future

trait Database {

  import Database._

  def execSQL(createSql: String): Unit

  def query(tableName:     String,
            columns:       Set[String] = Set.empty,
            selection:     String      = "",
            selectionArgs: Seq[String] = Seq.empty,
            groupBy:       String      = "",
            having:        String      = "",
            orderBy:       String      = "",
            limit:         String      = ""
           ): Cursor

  def delete(tableName: String, whereClaus: String, whereArgs: Seq[String]): Int

  def insertWithOnConflict(tableName: String, nullColumnHack: String, initialValues: ContentValues, conflictAlgorithm: Int): Long

  def apply[A](f: SQLiteDatabase => A): Future[A]

  def read[A](f: SQLiteDatabase => A): Future[A]

  def withStatement[A](sql: String)(body: PreparedStatement => A): A

  def dropAllTables(): Unit
}

class SQLiteDatabase(dbFile: File) extends Database {

  private lazy val daos = Seq(
    AccountDataDao
  )

  //TODO handle multiple threads/connections at some point
  lazy val dispatcher = new SerialDispatchQueue(Threading.IO)

  new File(dbFile.getParent).mkdirs()
  dbFile.createNewFile()

  override def execSQL(sql: String) = Managed(connection).acquire(_.createStatement().executeUpdate(sql))

  override def query(tableName: String, columns: Set[String], selection: String, selectionArgs: Seq[String],
                     groupBy: String, having: String, orderBy: String, limit: String) = {

    val cols = if (columns.isEmpty) "*" else columns.mkString(", ")
    val sel = if (selection.isEmpty) "" else s" WHERE $selection"
    val lim = if(limit.isEmpty) "" else s" LIMIT $limit"
    val query = s"SELECT $cols FROM $tableName$sel$lim"

    verbose(s"Querying database: $query")

    val stmt = connection.prepareStatement(query)
    selectionArgs.zipWithIndex.foreach { case (arg, i) =>
      stmt.setString(i + 1, arg)
    }

    Cursor(stmt, stmt.executeQuery())
  }

  override def withStatement[A](sql: String)(body: PreparedStatement => A): A = {
    Managed(connection).acquire { c =>
      Managed(c.prepareStatement(sql)).acquire(body)
    }
  }

  override def delete(tableName: String, whereClaus: String, whereArgs: Seq[String]) = ???

  //TODO handle transactions
  override def read[A](f: (SQLiteDatabase) => A): Future[A] = apply(f)

  private def connection = {
    verbose(s"Creating db connection to ${dbFile.getAbsolutePath}")
    DriverManager.getConnection(s"jdbc:sqlite:${dbFile.getAbsolutePath}")
  }

  override def insertWithOnConflict(tableName: String, nullColumnHack: String, initialValues: ContentValues, conflictAlgorithm: Int) = -1L

  override def apply[A](f: (SQLiteDatabase) => A) = dispatcher(f(this)).future

  override def dropAllTables() = {

  }
}

object Database {

  def iteratingWithReader[A](reader: Reader[A])(c: => Cursor): Managed[Iterator[A]] = Managed(c).map(new CursorIterator[A](_)(reader))

  val ConflictRollback = 1
  val ConflictAbort = 2
  val ConflictFail = 3
  val ConflictIgnore = 4
  val ConflictReplace = 5

  type ContentValues = Map[String, String]

}
