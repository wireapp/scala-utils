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

import java.sql.PreparedStatement

import com.wire.auth.{EmailAddress, Handle, PhoneNumber}
import com.wire.data.{Id, IdGen}
import org.threeten.bp.Instant

import scala.collection.mutable
import scala.language.higherKinds

case class Col[A](name: String, sqlType: String, modifiers: String = "")(implicit translator: DbTranslator[A]) {
  def load(cursor: Cursor, index: Int): A = translator.load(cursor, index)
  def loadOpt(cursor: Cursor, index: Int): Option[A] = translator.loadOpt(cursor, index)
  def save(value: A, values: mutable.Map[String, String]): Unit = values.put(name, sqlLiteral(value))
  def bind(value: A, index: Int, stmt: PreparedStatement): Unit = stmt.setString(index, sqlLiteral(value))
  def sqlLiteral(value: A): String = translator.literal(value)
}

object Col {
  def opt[A](c: Col[A]): Col[Option[A]] = Col[Option[A]](c.name, c.sqlType, c.modifiers)(new DbTranslator[Option[A]] {
    override def load(cursor: Cursor, index: Int) = c.loadOpt(cursor, index)
    override def loadOpt(cursor: Cursor, index: Int) = Option(c.loadOpt(cursor, index))
    override def literal(value: Option[A]) = value.fold("NULL")(c.sqlLiteral)
  })
//  def seq[A](name: Symbol, enc: Seq[A] => String, dec: String => Seq[A]): Col[Seq[A]] = Col[Seq[A]](name.name, "TEXT")(new DbTranslator[Seq[A]] {
//    override def load(cursor: Cursor, index: Int): Seq[A] = if (cursor.isNull(index)) Nil else dec(cursor.getString(index))
//    override def save(value: Seq[A], name: String, values: ContentValues): Unit = value match {
//      case Nil => values.putNull(name)
//      case _ => values.put(name, enc(value))
//    }
//    override def bind(value: Seq[A], index: Int, stmt: SQLiteProgram): Unit = value match {
//      case Nil => stmt.bindNull(index)
//      case _ => stmt.bindString(index, enc(value))
//    }
//    override def literal(value: Seq[A]): String = enc(value)
//  })
//  def set[A](name: Symbol, enc: Set[A] => String, dec: String => Set[A]): Col[Set[A]] = Col[Set[A]](name.name, "TEXT")(new DbTranslator[Set[A]] {
//    override def load(cursor: Cursor, index: Int): Set[A] = if (cursor.isNull(index)) Set.empty[A] else dec(cursor.getString(index))
//    override def save(value: Set[A], name: String, values: ContentValues): Unit =
//      if (value.isEmpty) values.putNull(name) else values.put(name, enc(value))
//    override def bind(value: Set[A], index: Int, stmt: SQLiteProgram): Unit =
//      if (value.isEmpty) stmt.bindNull(index) else stmt.bindString(index, enc(value))
//    override def literal(value: Set[A]): String = enc(value)
//  })
//  def json[A: JsonDecoder : JsonEncoder](name: Symbol) = Col[A](name.name, "TEXT")(DbTranslator.jsonTranslator[A]())
//  def jsonArr[A, B[C] <: Traversable[C]](name: Symbol)(implicit enc: JsonEncoder[A], dec: JsonDecoder[A], cbf: CanBuild[A, B[A]]) = Col[B[A]](name.name, "TEXT")(DbTranslator.jsonArrTranslator[A, B]())
//  def jsonArray[A, B[C] <: Traversable[C], D[E] <: B[E]](name: Symbol)(implicit enc: JsonEncoder[A], dec: JsonDecoder[A], cbf: CanBuild[A, D[A]]): Col[B[A]] = jsonArr[A, B](name)(enc, dec, cbf)
//
//  def proto[A <: MessageNano](name: Symbol)(implicit dec: ProtoDecoder[A]) = Col[A](name.name, "BLOB")(DbTranslator.protoTranslator[A]())
//  def protoSeq[A <: MessageNano, B[C] <: Traversable[C], D[E] <: B[E]](name: Symbol)(implicit dec: ProtoDecoder[A], cbf: CanBuild[A, D[A]]): Col[B[A]] = Col[B[A]](name.name, "BLOB")(DbTranslator.protoSeqTranslator[A, B]())
//
  def text(name: Symbol) = Col[String](name.name, "TEXT")
  def text(name: Symbol, modifiers: String) = Col[String](name.name, "TEXT", modifiers)
  def text[A](name: Symbol, enc: A => String, dec: String => A, modifiers: String = "") = Col[A](name.name, "TEXT")(new DbTranslator[A] {
    override def loadOpt(cursor: Cursor, index: Int) = cursor.getString(index).map(dec)
    override def literal(value: A) = enc(value)
  })
  def id[A <: Id: IdGen](name: Symbol, modifiers: String = "") = Col[A](name.name, "TEXT", modifiers)(DbTranslator.idTranslator())
//  def uid(name: Symbol, modifiers: String = "") = Col[Uid](name.name, "TEXT", modifiers)
  def int(name: Symbol, modifiers: String = "") = Col[Int](name.name, "INTEGER", modifiers)
//  def int[A](name: Symbol, enc: A => Int, dec: Int => A) = Col[A](name.name, "INTEGER")(new DbTranslator[A] {
//    override def save(value: A, name: String, values: ContentValues): Unit = values.put(name, Integer.valueOf(enc(value)))
//    override def bind(value: A, index: Int, stmt: SQLiteProgram): Unit = stmt.bindLong(index, enc(value))
//    override def load(cursor: Cursor, index: Int): A = dec(cursor.getInt(index))
//    override def literal(value: A): String = enc(value).toString
//  })
//  def on[A, B](c: Col[B], enc: A => B, dec: B => A) = Col[A](c.name, c.sqlType, c.modifiers)(new DbTranslator[A] {
//    override def save(value: A, name: String, values: ContentValues): Unit = c.save(enc(value), values)
//    override def bind(value: A, index: Int, stmt: SQLiteProgram): Unit = c.bind(enc(value), index, stmt)
//    override def load(cursor: Cursor, index: Int): A = dec(c.load(cursor, index))
//    override def literal(value: A): String = c.sqlLiteral(enc(value))
//  })
  def phoneNumber(name: Symbol, modifiers: String = "") = Col[PhoneNumber](name.name, "TEXT", modifiers)
  def emailAddress(name: Symbol, modifiers: String = "") = Col[EmailAddress](name.name, "TEXT", modifiers)
  def handle(name: Symbol, modifiers: String = "") = Col[Handle](name.name, "TEXT", modifiers)
//  def finiteDuration(name: Symbol, modifiers: String = "") = Col[FiniteDuration](name.name, "INTEGER", modifiers)
  def timestamp(name: Symbol, modifiers: String = "") = Col[Instant](name.name, "INTEGER", modifiers)
  def long(name: Symbol, modifiers: String = "") = Col[Long](name.name, "INTEGER", modifiers)
//  def long[A](name: Symbol, enc: A => Long, dec: Long => A) = Col[A](name.name, "INTEGER")(new DbTranslator[A] {
//    override def save(value: A, name: String, values: ContentValues): Unit = values.put(name, java.lang.Long.valueOf(enc(value)))
//    override def bind(value: A, index: Int, stmt: SQLiteProgram): Unit = stmt.bindLong(index, enc(value))
//    override def load(cursor: Cursor, index: Int): A = dec(cursor.getLong(index))
//    override def literal(value: A): String = enc(value).toString
//  })
//  def double(name: Symbol, modifiers: String = "") = Col[Double](name.name, "REAL", modifiers)
//  def float(name: Symbol, modifiers: String = "") = Col[Float](name.name, "REAL", modifiers)
//
  def bool(name: Symbol, modifiers: String = "") = Col[Boolean](name.name, "INTEGER", modifiers)
//
//  def file(name: Symbol, modifiers: String = "") = Col[File](name.name, "TEXT", modifiers)
//  def blob(name: Symbol, modifiers: String = "") = Col[Array[Byte]](name.name, "BLOB", modifiers)
}

case class ColBinder[A, B](col: Col[A], extractor: B => A, var index: Int = 0) {
  def apply(value: A): String = col.sqlLiteral(value)
  def load(cursor: Cursor, index: Int): A = col.load(cursor, index)
  def bind(value: B, stmt: PreparedStatement): Unit = col.bind(extractor(value), index + 1, stmt)
  def save(value: B, values: mutable.Map[String, String]): Unit = col.save(extractor(value), values)
  def name: String = col.name
}

