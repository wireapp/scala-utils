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
import com.wire.db.Database.ContentValues
import com.wire.logging.ZLog._
import com.wire.logging.ZLog.ImplicitTag._
import com.wire.macros.returning

import scala.collection.breakOut
import scala.language.implicitConversions

trait Reader[A] {
  def apply(implicit c: Cursor): A
}

object Reader {
  def apply[A](f: Cursor => A): Reader[A] = new Reader[A] { def apply(implicit c: Cursor): A = f(c) }
}

abstract class Dao[T, A] extends DaoIdOps[T] {
  type IdCols = Column[A]
  type IdVals = A

  protected def idColumns(id: IdCols): IndexedSeq[Column[_]] = Vector(id)
  protected def idValueSplitter(v: IdVals): Array[String] = Array(idCol(v))
  def idExtractor(item: T): IdVals = idCol.extractor(item)

  override def getAll(ids: Set[A])(implicit db: Database): Vector[T] =
    if (ids.isEmpty) Vector.empty
    else list(db.query(table.name, null, s"${idCol.name} IN (${ids.map(idCol.col.sqlLiteral).mkString("'", "','", "'")})", null, null, null, null))
}

abstract class Dao2[T, A, B] extends DaoIdOps[T] {
  type IdCols = (Column[A], Column[B])
  type IdVals = (A, B)

  protected def idColumns(id: IdCols): IndexedSeq[Column[_]] = Vector(id._1, id._2)
  protected def idValueSplitter(v: IdVals): Array[String] = Array(idCol._1.col.sqlLiteral(v._1), idCol._2.col.sqlLiteral(v._2))
  def idExtractor(item: T): IdVals = (idCol._1.extractor(item), idCol._2.extractor(item))
}

abstract class Dao3[T, A, B, C] extends DaoIdOps[T] {
  type IdCols = (Column[A], Column[B], Column[C])
  type IdVals = (A, B, C)

  protected def idColumns(id: IdCols): IndexedSeq[Column[_]] = Vector(id._1, id._2, id._3)
  protected def idValueSplitter(v: IdVals): Array[String] = Array(idCol._1.col.sqlLiteral(v._1), idCol._2.col.sqlLiteral(v._2), idCol._3.col.sqlLiteral(v._3))
  def idExtractor(item: T): IdVals = (idCol._1.extractor(item), idCol._2.extractor(item), idCol._3.extractor(item))
}

abstract class DaoIdOps[T] extends BaseDao[T] {
  type IdCols
  type IdVals

  protected def idColumns(id: IdCols): IndexedSeq[Column[_]]
  protected def idValueSplitter(v: IdVals): Array[String]
  def idExtractor(item: T): IdVals

  val idCol: IdCols

  private lazy val builtIdCols = idColumnsQueryBuilder(idCol)
  protected def idColumnsQueryBuilder(id: IdCols): String = idColumns(id).map(c => s"${c.name} = ?").mkString(" AND ")

  def getAll(ids: Set[IdVals])(implicit db: Database): Vector[T] = {
    val builder = Vector.newBuilder[T]
    ids foreach { getById(_) foreach { builder += _ } }
    builder.result()
  }

  def getById(id: IdVals)(implicit db: Database) = single(findById(id))

  def getCursor(id: IdVals)(implicit db: Database) = findById(id)

  private def findById(id: IdVals)(implicit db: Database): Cursor = db.query(table.name, null, builtIdCols, idValueSplitter(id), null, null, null, "1")

  def delete(id: IdVals)(implicit db: Database): Int = db.delete(table.name, builtIdCols, idValueSplitter(id))

//  def deleteEvery(ids: GenTraversableOnce[IdVals])(implicit db: Database): Unit = inTransaction {
//    withDatabase(s"DELETE FROM ${table.name} WHERE $builtIdCols") { stmt =>
//      ids.foreach { id =>
//        idValueSplitter(id).zipWithIndex foreach { case (v, idx) => stmt.bindString(idx + 1, v) }
//        stmt.execute()
//      }
//    }
//  }

  def update(item: T)(f: T => T)(implicit db: Database): Option[T] = updateById(idExtractor(item))(f)

  def updateById(id: IdVals)(f: T => T)(implicit db: Database): Option[T] = getById(id).map(it => insertOrReplace(f(it)))

  override def Table(name: String, columns: Column[_]*) = new TableWithId(name, columns:_*)(idColumns(idCol))
}

abstract class BaseDao[T] extends Reader[T] {

  val table: Table[T]

  def onCreate(db: Database) = db.execSQL(table.createSql)

  def values(item: T): ContentValues = table.save(item)

  def listCursor(implicit db: Database): Cursor = db.query(table.name)

  def list(implicit db: Database): Vector[T] = list(listCursor)

  def iterating(c: => Cursor): Managed[Iterator[T]] = Database.iteratingWithReader(this)(c)

  def single(c: Cursor, close: Boolean = true): Option[T] = try { if (c.moveToFirst()) Option(apply(c)) else None } finally { if (close) c.close() }

  def list(c: Cursor, close: Boolean = true, filter: T => Boolean = { _ => true }): Vector[T] = try { CursorIterator(c)(this).filter(filter).toVector } finally { if (close) c.close() }

  def foreach(c: Cursor, f: T => Unit): Unit =
    try { CursorIterator(c)(this).foreach(f) } finally { c.close() }

  def foreach(f: T => Unit)(implicit db: Database): Unit = {
    val c = listCursor
    try { CursorIterator(c)(this).foreach(f) } finally { c.close() }
  }

  def find[A](col: Column[A], value: A)(implicit db: Database): Cursor = db.query(table.name, selection = s"${col.name} = ?", selectionArgs = Seq(col(value)))

  def findInSet[A](col: Column[A], values: Set[A])(implicit db: Database): Cursor = db.query(table.name, null, s"${col.name} IN (${values.iterator.map(_ => "?").mkString(", ")})", values.map(col(_))(breakOut): Array[String], null, null, null)

  def delete[A](col: Column[A], value: A)(implicit db: Database): Int = db.delete(table.name, s"${col.name} = ?", Array(col(value)))

//  def insertOrIgnore(items: GenTraversableOnce[T])(implicit db: Database): Unit = insertWith(table.insertOrIgnoreSql)(items)
//
//  def insertOrReplace(items: GenTraversableOnce[T])(implicit db: Database): Unit = insertWith(table.insertSql)(items)
//
//  private def insertWith(sql: String)(items: GenTraversableOnce[T])(implicit db: Database): Unit = inTransaction {
//    withDatabase(sql) { stmt =>
//      items foreach { item =>
//        table.bind(item, stmt)
//        stmt.execute()
//      }
//    }
//  }

  def insertOrIgnore(item: T)(implicit db: Database): T = returning(item)(i => db.insertWithOnConflict(table.name, null, values(i), Database.ConflictIgnore))

  def insertOrReplace(item: T)(implicit db: Database): T = returning(item)(i => db.insertWithOnConflict(table.name, null, values(i), Database.ConflictReplace))

  def deleteAll(implicit db: Database): Int = db.delete(table.name, null, null)

  def Table(name: String, columns: Column[_]*) = new Table(name, columns:_*)

  type Column[A] = ColBinder[A, T]

  final implicit def columnToValue[A](col: Column[A])(implicit cursor: Cursor): A = {
    val index = cursor.getColumnIndex(col.name)
    if (index < 0) {
      error(s"findColumn returned $index for column: ${col.name}")
    }
    col.load(cursor, index)
  }

  def readerFor[A](col: Column[A]): Reader[A] = new Reader[A] {
    def apply(implicit c: Cursor): A = col
  }

  final implicit class colToColumn[A](col: Col[A]) {
    def apply(extractor: T => A): Column[A] = ColBinder[A, T](col, extractor)
  }
}
