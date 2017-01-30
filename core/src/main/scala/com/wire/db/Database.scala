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

import com.wire.macros.logging.LogTag
import com.wire.threading._

import scala.concurrent.Future

trait Database {

  implicit val dispatcher: SerialDispatchQueue

  implicit val dbHelper: DatabaseOpenHelper

  lazy val readExecutionContext: DispatchQueue = new UnlimitedDispatchQueue(Threading.IO, name = "Database_readQueue_" + hashCode().toHexString)

  def apply[A](f: DatabaseEngine => A)(implicit logTag: LogTag = ""): CancellableFuture[A] = dispatcher {
    implicit val db = dbHelper.getWritableDatabase
    inTransaction(f(db))
  } ("Database_" + logTag)

  def withTransaction[A](f: DatabaseEngine => A)(implicit logTag: LogTag = ""): CancellableFuture[A] = apply(f)

  def read[A](f: DatabaseEngine => A): Future[A] = Future {
    implicit val db = dbHelper.getWritableDatabase
    inReadTransaction(f(db))
  } (readExecutionContext)

  def close() = dispatcher {
    dbHelper.close()
  }
}

//TODO think of a more general way of handling this
trait DatabaseEngine {
  def setTransactionSuccessful(): Unit
  def endTransaction(): Unit
  def beginTransactionNonExclusive(): Unit
  def inTransaction(): Boolean
}

trait DatabaseOpenHelper {
  def getWritableDatabase: DatabaseEngine
  def close(): Unit
}
