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

import com.wire.auth.{EmailAddress, Handle, PhoneNumber}
import com.wire.data.{Id, IdGen}
import org.threeten.bp.Instant

import scala.language.higherKinds

abstract class DbTranslator[T] {
  def loadOpt(cursor: Cursor, index: Int): Option[T]
  def load(cursor: Cursor, index: Int): T = loadOpt(cursor, index) match {
    case Some(v) => v
    case None => throw new IllegalStateException(s"Expected non-NULL value from db")
  }
  def literal(value: T): String
}

object DbTranslator {
  implicit object StringTranslator extends DbTranslator[String] {
    override def loadOpt(cursor: Cursor, index: Int) = cursor.getString(index)
    override def literal(value: String) = value
  }
//  implicit object UidTranslator extends DbTranslator[Uid] {
//    override def load(cursor: Cursor, index: Int): Uid = Uid(cursor.getString(index))
//    override def save(value: Uid, name: String, values: ContentValues): Unit = values.put(name, value.str)
//    override def bind(value: Uid, index: Int, stmt: SQLiteProgram): Unit = stmt.bindString(index, value.str)
//  }
  implicit object IntTranslator extends DbTranslator[Int] {
    override def loadOpt(cursor: Cursor, index: Int) = cursor.getInt(index)
    override def literal(value: Int) = String.valueOf(value)
  }
  implicit object LongTranslator extends DbTranslator[Long] {
    override def loadOpt(cursor: Cursor, index: Int) = cursor.getLong(index)
    override def literal(value: Long) = String.valueOf(value)
  }
//  implicit object DoubleTranslator extends DbTranslator[Double] {
//    override def load(cursor: Cursor, index: Int): Double = cursor.getDouble(index)
//    override def save(value: Double, name: String, values: ContentValues): Unit = values.put(name, java.lang.Double.valueOf(value))
//    override def bind(value: Double, index: Int, stmt: SQLiteProgram): Unit = stmt.bindDouble(index, value)
//  }
//  implicit object FloatTranslator extends DbTranslator[Float] {
//    override def load(cursor: Cursor, index: Int): Float = cursor.getFloat(index)
//    override def save(value: Float, name: String, values: ContentValues): Unit = values.put(name, java.lang.Float.valueOf(value))
//    override def bind(value: Float, index: Int, stmt: SQLiteProgram): Unit = stmt.bindDouble(index, value)
//  }
  implicit object BooleanTranslator extends DbTranslator[Boolean] {
    override def loadOpt(cursor: Cursor, index: Int) = cursor.getInt(index).map(_ == 1)
    override def literal(value: Boolean) = if (value) "1" else "0"
  }
//  implicit object DateTranslator extends DbTranslator[Date] {
//    override def load(cursor: Cursor, index: Int): Date = new Date(cursor.getLong(index))
//    override def save(value: Date, name: String, values: ContentValues): Unit = values.put(name, java.lang.Long.valueOf(value.getTime))
//    override def bind(value: Date, index: Int, stmt: SQLiteProgram): Unit = stmt.bindLong(index, value.getTime)
//    override def literal(value: Date): String = value.getTime.toString
//  }
  implicit object InstantTranslator extends DbTranslator[Instant] {
    override def loadOpt(cursor: Cursor, index: Int) = cursor.getLong(index).map(Instant.ofEpochMilli)
    override def literal(value: Instant) = value.toEpochMilli.toString
  }
//  implicit object FiniteDurationTranslator extends DbTranslator[FiniteDuration] {
//    override def load(cursor: Cursor, index: Int): FiniteDuration = FiniteDuration(cursor.getLong(index), TimeUnit.MILLISECONDS)
//    override def save(value: FiniteDuration, name: String, values: ContentValues): Unit = values.put(name, java.lang.Long.valueOf(value.toMillis))
//    override def bind(value: FiniteDuration, index: Int, stmt: SQLiteProgram): Unit = stmt.bindLong(index, value.toMillis)
//    override def literal(value: FiniteDuration): String = value.toMillis.toString
//  }
  implicit object PhoneNumberTranslator extends DbTranslator[PhoneNumber] {
    override def loadOpt(cursor: Cursor, index: Int) = cursor.getString(index).map(PhoneNumber)
    override def literal(value: PhoneNumber) = value.str
  }
  implicit object EmailAddressTranslator extends DbTranslator[EmailAddress] {
    override def loadOpt(cursor: Cursor, index: Int) = cursor.getString(index).map(EmailAddress)
    override def literal(value: EmailAddress) = value.str
  }
  implicit object HandleTranslator extends DbTranslator[Handle] {
    override def loadOpt(cursor: Cursor, index: Int) = cursor.getString(index).map(Handle)
    override def literal(value: Handle) = value.string
  }
//  implicit def optionTranslator[A](implicit trans: DbTranslator[A]): DbTranslator[Option[A]] = new DbTranslator[Option[A]] {
//    override def load(cursor: Cursor, index: Int): Option[A] = if (cursor.isNull(index)) None else Some(trans.load(cursor, index))
//    override def save(value: Option[A], name: String, values: ContentValues): Unit = value match {
//      case Some(v) => trans.save(v, name, values)
//      case None => values.putNull(name)
//    }
//    override def bind(value: Option[A], index: Int, stmt: SQLiteProgram): Unit = value match {
//      case Some(v) => trans.bind(v, index, stmt)
//      case None => stmt.bindNull(index)
//    }
//    override def literal(value: Option[A]): String = value.fold("NULL")(trans.literal)
//  }
//  implicit object ByteArrayTranslator extends DbTranslator[Array[Byte]] {
//    override def load(cursor: Cursor, index: Int): Array[Byte] = cursor.getBlob(index)
//    override def save(value: Array[Byte], name: String, values: ContentValues): Unit = values.put(name, value)
//    override def bind(value: Array[Byte], index: Int, stmt: SQLiteProgram): Unit = stmt.bindBlob(index, value)
//    override def literal(value: Array[Byte]): String = throw new UnsupportedOperationException("can't get sql literal for blob")
//  }
//  implicit object FileTranslator extends DbTranslator[File] {
//    override def load(cursor: Cursor, index: Int): File = new File(cursor.getString(index))
//    override def save(value: File, name: String, values: ContentValues): Unit = values.put(name, literal(value))
//    override def bind(value: File, index: Int, stmt: SQLiteProgram): Unit = stmt.bindString(index, literal(value))
//    override def literal(value: File): String = value.getCanonicalPath
//  }
  implicit def idTranslator[A <: Id: IdGen](): DbTranslator[A] = new DbTranslator[A] {
    override def loadOpt(cursor: Cursor, index: Int) = cursor.getString(index).map(implicitly[IdGen[A]].decode(_))
    override def literal(value: A): String = implicitly[IdGen[A]].encode(value)
  }
//  implicit def jsonTranslator[A: JsonDecoder : JsonEncoder](): DbTranslator[A] = new DbTranslator[A] {
//    override def save(value: A, name: String, values: ContentValues): Unit = values.put(name, literal(value))
//    override def bind(value: A, index: Int, stmt: SQLiteProgram): Unit = stmt.bindString(index, literal(value))
//    override def load(cursor: Cursor, index: Int): A = implicitly[JsonDecoder[A]].apply(new JSONObject(cursor.getString(index)))
//    override def literal(value: A): String = JsonEncoder.encodeString(value)
//  }
//  implicit def jsonArrTranslator[A, B[C] <: Traversable[C]]()(implicit dec: JsonDecoder[A], enc: JsonEncoder[A], cbf: CanBuild[A, B[A]]): DbTranslator[B[A]] = new DbTranslator[B[A]] {
//    override def save(value: B[A], name: String, values: ContentValues): Unit = if (value.isEmpty) values.putNull(name) else values.put(name, literal(value))
//    override def bind(value: B[A], index: Int, stmt: SQLiteProgram): Unit = if (value.isEmpty) stmt.bindNull(index) else stmt.bindString(index, literal(value))
//    override def load(cursor: Cursor, index: Int): B[A] = if (cursor.isNull(index)) cbf.apply.result else JsonDecoder.arrayColl[A, B](new JSONArray(cursor.getString(index)))
//    override def literal(value: B[A]): String = JsonEncoder.arr(value).toString
//  }
//  implicit def protoTranslator[A <: MessageNano : ProtoDecoder](): DbTranslator[A] = new DbTranslator[A] {
//    override def save(value: A, name: String, values: ContentValues): Unit = values.put(name, MessageNano.toByteArray(value))
//    override def bind(value: A, index: Int, stmt: SQLiteProgram): Unit = stmt.bindBlob(index, MessageNano.toByteArray(value))
//    override def load(cursor: Cursor, index: Int): A = implicitly[ProtoDecoder[A]].apply(cursor.getBlob(index))
//    override def literal(value: A): String = throw new UnsupportedOperationException("can't get sql literal for proto")
//  }
//  implicit def protoSeqTranslator[A <: MessageNano, B[C] <: Traversable[C]]()(implicit dec: ProtoDecoder[A], cbf: CanBuild[A, B[A]]): DbTranslator[B[A]] = new DbTranslator[B[A]] {
//    def toBytes(value: B[A]) = {
//      val size = value.foldLeft(0)(_ + _.getSerializedSize)
//      val buff = Array.ofDim[Byte](size)
//      val out = CodedOutputByteBufferNano.newInstance(buff)
//      value foreach { _.writeTo(out) }
//      buff
//    }
//
//    override def save(value: B[A], name: String, values: ContentValues): Unit = if (value.isEmpty) values.putNull(name) else values.put(name, toBytes(value))
//    override def bind(value: B[A], index: Int, stmt: SQLiteProgram): Unit = if (value.isEmpty) stmt.bindNull(index) else stmt.bindBlob(index, toBytes(value))
//    override def load(cursor: Cursor, index: Int): B[A] = if (cursor.isNull(index)) cbf.apply.result else {
//      val builder = cbf.apply()
//      val in = CodedInputByteBufferNano.newInstance(cursor.getBlob(index))
//      while (!in.isAtEnd)
//        builder += implicitly[ProtoDecoder[A]].apply(in)
//      builder.result()
//    }
//    override def literal(value: B[A]): String = throw new UnsupportedOperationException("can't get sql literal for proto seq")
//  }
}
