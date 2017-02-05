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
import java.sql.{DriverManager, ResultSet, Statement}

import com.wire.data.Managed
import com.wire.logging.ZLog.ImplicitTag._
import com.wire.logging.ZLog.verbose

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

  def insertWithOnConflict(tableName: String, nullColumnHack: String, initialValues: ContentValues, conflictAlgorithm: Int) = Long
}

/**
  * Allows keeping connection open while ResultSet is being used outside of Database class, and also
  * provides a nice way to wrap the android methods, making copying easier
  */
case class Cursor(st: Statement, resultSet: ResultSet) extends AutoCloseable {

  def close() = {
    st.close()
    st.getConnection.close()
  }

  def moveToFirst() = resultSet.first()

  def moveToNext() = resultSet.next()

  def isClosed = resultSet.isClosed

  def isAfterLast = resultSet.isAfterLast

  def getColumnIndex(columnName: String) = resultSet.findColumn(columnName)

  def count: Int = -1

  def getString(colIndex: Int): String = resultSet.getString(colIndex)
  def getString(colLabel: String): String = resultSet.getString(colLabel)

  def getInt(colIndex: Int): Int = resultSet.getInt(colIndex)
  def getInt(colLabel: String): Int = resultSet.getInt(colLabel)
}

class SQLiteDatabase(dbFile: File) extends Database {

  new File(dbFile.getParent).mkdirs()
  dbFile.createNewFile()

  override def execSQL(sql: String) = Managed(connection).acquire(_.createStatement().executeUpdate(sql))

  override def query(tableName: String, columns: Set[String], selection: String, selectionArgs: Seq[String],
                     groupBy: String, having: String, orderBy: String, limit: String) = {

    val cols = if (columns.isEmpty) "*" else columns.mkString(", ")
    val sel = if (selection.isEmpty) "" else s" WHERE $selection"
    val query = s"SELECT $cols FROM $tableName$sel"

    verbose(s"Querying database: $query")

    val st = connection.prepareStatement(query)
    selectionArgs.zipWithIndex.foreach { case (arg, i) =>
      st.setString(i + 1, arg)
    }

    Cursor(st, st.executeQuery())
  }

  override def delete(tableName: String, whereClaus: String, whereArgs: Seq[String]) = ???

  private def connection = {
    verbose(s"Creating db connection to ${dbFile.getAbsolutePath}")
    DriverManager.getConnection(s"jdbc:sqlite:${dbFile.getAbsolutePath}")
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
