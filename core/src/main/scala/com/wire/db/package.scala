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
