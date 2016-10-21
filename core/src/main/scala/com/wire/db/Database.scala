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
