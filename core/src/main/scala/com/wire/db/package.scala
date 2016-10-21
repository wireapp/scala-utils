package com.wire

import com.wire.macros.returning

package object db {
  def inTransaction[A](body: => A)(implicit db: DatabaseEngine): A =
    inTransaction(_ => body)

  def inTransaction[A](body: Transaction => A)(implicit db: DatabaseEngine): A = {
    val tr = new Transaction(db)
    if (db.inTransaction()) body(tr)
    else {
      db.beginTransactionNonExclusive()
      try returning(body(tr)) { _ => db.setTransactionSuccessful() }
      finally db.endTransaction()
    }
  }

  class Transaction(db: DatabaseEngine) {
    def flush() = {
      db.setTransactionSuccessful()
      db.endTransaction()
      db.beginTransactionNonExclusive()
    }
  }

  def inReadTransaction[A](body: => A)(implicit db: DatabaseEngine): A =
    if (db.inTransaction()) body
    else {
      db.beginTransactionNonExclusive()
      try returning(body) { _ => db.setTransactionSuccessful() }
      finally db.endTransaction()
    }
}
