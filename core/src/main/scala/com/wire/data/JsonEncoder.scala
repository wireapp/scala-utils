package com.wire.data

import java.util.Date

import com.wire.macros.returning
import org.json.{JSONArray, JSONObject}
import org.threeten.bp.Instant

import scala.collection.GenTraversable

trait JsonEncoder[A] { self =>
  def apply(v: A): JSONObject

  def comap[B](f: B => A): JsonEncoder[B] = JsonEncoder.lift(b => self(f(b)))
}

object JsonEncoder {

  def arr[A](items: GenTraversable[A])(implicit enc: JsonEncoder[A]): JSONArray = returning(new JSONArray)(arr => items.foreach(item => arr.put(enc(item))))

  def array[A](items: GenTraversable[A])(enc: (JSONArray, A) => Unit): JSONArray = returning(new JSONArray)(arr => items.foreach(enc(arr, _)))

  def arrString(items: Seq[String]): JSONArray = returning(new JSONArray) { arr => items foreach arr.put }

  def arrNum[A: Numeric](items: Seq[A]): JSONArray = returning(new JSONArray) { arr => items foreach arr.put }

  def apply(encode: JSONObject => Unit): JSONObject = returning(new JSONObject)(encode)

  def build[A](f: A => JSONObject => Unit): JsonEncoder[A] = lift(a => returning(new JSONObject)(f(a)))

  def lift[A](f: A => JSONObject): JsonEncoder[A] = new JsonEncoder[A] {
    override def apply(a: A): JSONObject = f(a)
  }

  def encode[A: JsonEncoder](value: A): JSONObject = implicitly[JsonEncoder[A]].apply(value)

  def encodeString[A: JsonEncoder](value: A): String = encode(value).toString

  def encodeDate(date: Date): String = JsonDecoder.utcDateFormat.get().format(date)
  def encodeInstant(instant: Instant): Long = instant.toEpochMilli

  def encodeISOInstant(time: Instant): String = JsonDecoder.utcDateFormat.get().format(new Date(time.toEpochMilli))
}