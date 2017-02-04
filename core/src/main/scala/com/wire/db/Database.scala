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

import java.sql.ResultSet

import com.wire.data.Managed
import com.wire.macros.logging.LogTag
import com.wire.macros.returning
import com.wire.threading._

import scala.concurrent.Future

trait Database {

  import Database._

  implicit val dispatcher: SerialDispatchQueue

  lazy val readExecutionContext: DispatchQueue = new UnlimitedDispatchQueue(Threading.IO, name = "Database_readQueue_" + hashCode().toHexString)

  def apply[A](f: => A)(implicit logTag: LogTag = ""): CancellableFuture[A] = dispatcher {
    inTransaction(f)
  } ("Database_" + logTag)

  def withTransaction[A](f: => A)(implicit logTag: LogTag = ""): CancellableFuture[A] = apply(f)

  def read[A](f: => A): Future[A] = Future {
    inReadTransaction(f)
  } (readExecutionContext)

  def setTransactionSuccessful(): Unit

  def endTransaction(): Unit

  def beginTransactionNonExclusive(): Unit

  def isInTransaction: Boolean

  def close(): Unit

  def execSQL(createSql: String): Unit

  def query(tableName:     String,
            columns:       Set[String] = Set.empty,
            selection:     String      = "",
            selectionArgs: Seq[String] = Seq.empty,
            groupBy:       String      = "",
            having:        String      = "",
            orderBy:       String      = "",
            limit:         String      = ""
           ): ResultSet

  def delete(tableName: String, whereClaus: String, whereArgs: Seq[String]): Int

  def insertWithOnConflict(tableName: String, nullColumnHack: String, initialValues: ContentValues, conflictAlgorithm: Int) = Long

  def inTransaction[A](body: => A): A = inTransaction(_ => body)

  def inTransaction[A](body: Transaction => A): A = {
    val tr = new Transaction(this)
    if (isInTransaction) body(tr)
    else {
      beginTransactionNonExclusive()
      try returning(body(tr)) { _ => setTransactionSuccessful() }
      finally endTransaction()
    }
  }

  def inReadTransaction[A](body: => A): A =
    if (isInTransaction) body
    else {
      beginTransactionNonExclusive()
      try returning(body) { _ => setTransactionSuccessful() }
      finally endTransaction()
    }
}

object Database {

  def iteratingWithReader[A](reader: Reader[A])(c: => ResultSet): Managed[Iterator[A]] = Managed(c).map(new ResultSetIterator[A](_)(reader))

  val ConflictRollback = 1
  val ConflictAbort = 2
  val ConflictFail = 3
  val ConflictIgnore = 4
  val ConflictReplace = 5

  type ContentValues = Map[String, String]

  class Transaction(db: Database) {
    def flush() = {
      db.setTransactionSuccessful()
      db.endTransaction()
      db.beginTransactionNonExclusive()
    }
  }
}
