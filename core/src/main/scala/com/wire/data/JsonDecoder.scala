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
  package com.wire.data

import java.net.URI
import java.text.SimpleDateFormat
import java.util.{Date, TimeZone}

import com.wire.assets.AssetToken
import com.wire.auth.{EmailAddress, Handle, PhoneNumber}
import com.wire.cryptography.{AESKey, Sha256}
import com.wire.data.Dimensions.{H, W}
import com.wire.utils.RichDate
import org.apache.commons.codec.binary.Base64
import org.json.{JSONArray, JSONObject}
import org.threeten.bp.{Duration, Instant}

import scala.collection.generic._


trait JsonDecoder[A] { self =>
  def apply(implicit js: JSONObject): A

  def map[B](f: A => B): JsonDecoder[B] = JsonDecoder.lift(a => f(self(a)))
}

object JsonDecoder {

  val utcDateFormat = new ThreadLocal[SimpleDateFormat] {
    override def initialValue(): SimpleDateFormat = {
      val format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
      format.setTimeZone(TimeZone.getTimeZone("Zulu"))
      format
    }
  }
  val shortDateFormat = new ThreadLocal[SimpleDateFormat] {
    override def initialValue(): SimpleDateFormat = {
      val format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
      format.setTimeZone(TimeZone.getTimeZone("Zulu"))
      format
    }
  }

  val DateRegex = """(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)(?:\.\d+)?Z""".r

  def decode[A](json: String)(implicit dec: JsonDecoder[A]): A = dec(new JSONObject(json))

  def parseDate(dateStr: String): Date =
    if (dateStr.length == 24) utcDateFormat.get().parse(dateStr)
    else dateStr match {
      case DateRegex(date) => shortDateFormat.get().parse(date)
      case _ => throw new IllegalArgumentException(s"Incorrect date string: $dateStr")
    }

  def apply[A](s: Symbol)(implicit js: JSONObject, dec: JsonDecoder[A]): A = dec(js.getJSONObject(s.name))

  def lift[A](f: JSONObject => A): JsonDecoder[A] = new JsonDecoder[A] {
    override def apply(implicit js: JSONObject): A = f(js)
  }

  def array[A: JsonDecoder](arr: JSONArray): Seq[A] = arrayColl[A, Vector](arr)

  def arrayColl[A, B[_]](arr: JSONArray)(implicit dec: JsonDecoder[A], cbf: CanBuild[A, B[A]]): B[A] = {
    val builder = cbf()
    builder.sizeHint(arr.length)
    (0 until arr.length) foreach (i => builder += dec(arr.getJSONObject(i)))
    builder.result
  }

  def array[T](arr: JSONArray, ex: (JSONArray, Int) => T): Vector[T] = Vector.tabulate(arr.length)(i => ex(arr, i))

  def array[A](s: Symbol)(ex: (JSONArray, Int) => A)(implicit js: JSONObject): Vector[A] =
    if (! js.has(s.name) || js.isNull(s.name)) Vector()
    else {
      val arr = js.getJSONArray(s.name)
      Vector.tabulate(arr.length)(ex(arr, _))
    }

  def intArray(arr: JSONArray): Vector[Int] = Vector.tabulate(arr.length)(i => arr.getInt(i))

  def withDefault[A](s: Symbol, default: A, dec: JSONObject => A)(implicit js: JSONObject): A =
    if (!js.has(s.name) || js.isNull(s.name)) default else dec(js)

  def opt[A](s: Symbol, dec: JSONObject => A)(implicit js: JSONObject): Option[A] =
    if (!js.has(s.name) || js.isNull(s.name)) None else Some(dec(js))

  def opt[A](s: Symbol)(implicit js: JSONObject, dec: JsonDecoder[A]): Option[A] =
    Option(js.optJSONObject(s.name)).map(dec(_))

  def decodeISOInstant(s: Symbol)(implicit js: JSONObject): Instant = withDefault(s, Instant.EPOCH, { js => parseDate(js.getString(s.name)).instant })
  def decodeOptISOInstant(s: Symbol)(implicit js: JSONObject): Option[Instant] = opt(s, decodeISOInstant(s)(_))

  implicit def decodeObject(s: Symbol)(implicit js: JSONObject): JSONObject = withDefault(s, new JSONObject(), _.getJSONObject(s.name))

  implicit def decodeString(s: Symbol)(implicit js: JSONObject): String = withDefault(s, "", _.getString(s.name))
  implicit def decodeSymbol(s: Symbol)(implicit js: JSONObject): Symbol = Symbol(js.getString(s.name))
  implicit def decodeBool(s: Symbol)(implicit js: JSONObject): Boolean = withDefault(s, false, _.getBoolean(s.name))
  implicit def decodeInt(s: Symbol)(implicit js: JSONObject): Int = withDefault(s, 0, _.getInt(s.name))
  implicit def decodeLong(s: Symbol)(implicit js: JSONObject): Long = withDefault(s, 0L, _.getLong(s.name))
  implicit def decodeFloat(s: Symbol)(implicit js: JSONObject): Float = withDefault(s, 0f, _.getDouble(s.name).toFloat)
  implicit def decodeDouble(s: Symbol)(implicit js: JSONObject): Double = withDefault(s, 0d, _.getDouble(s.name))
  implicit def decodeUtcDate(s: Symbol)(implicit js: JSONObject): Date = parseDate(js.getString(s.name))
  implicit def decodeInstant(s: Symbol)(implicit js: JSONObject): Instant = withDefault(s, Instant.EPOCH, { js => Instant.parse(js.getString(s.name)) })
  implicit def decodeDuration(s: Symbol)(implicit js: JSONObject): Duration = Duration.ofMillis(js.getLong(s.name))
  implicit def decodeBytes(s: Symbol)(implicit js: JSONObject): Array[Byte] = Base64.decodeBase64(decodeString(s))

//  implicit def decodeLocale(s: Symbol)(implicit js: JSONObject): Option[Locale] = withDefault(s, None, o => Locales.bcp47.localeFor(o.getString(s.name)))
  implicit def decodeUid(s: Symbol)(implicit js: JSONObject): UId = UId(js.getString(s.name))
  implicit def decodeOptString(s: Symbol)(implicit js: JSONObject): Option[String] = if (js.has(s.name)) { if(js.isNull(s.name)) Some("") else Some(js.getString(s.name)) } else None
  implicit def decodeOptInt(s: Symbol)(implicit js: JSONObject): Option[Int] = opt(s, _.getInt(s.name))
  implicit def decodeOptLong(s: Symbol)(implicit js: JSONObject): Option[Long] = opt(s, _.getLong(s.name))
  implicit def decodeOptBoolean(s: Symbol)(implicit js: JSONObject): Option[Boolean] = opt(s, _.getBoolean(s.name))
  implicit def decodeOptFloat(s: Symbol)(implicit js: JSONObject): Option[Float] = opt(s, _.getDouble(s.name).toFloat)
  implicit def decodeOptBytes(s: Symbol)(implicit js: JSONObject): Option[Array[Byte]] = opt(s, js => Base64.decodeBase64(s.name))

  implicit def decodeOptUid(s: Symbol)(implicit js: JSONObject): Option[UId] = opt(s, js => UId(js.getString(s.name)))
  implicit def decodeOptAssetId(s: Symbol)(implicit js: JSONObject): Option[AssetId] = opt(s, js => AssetId(js.getString(s.name)))
  implicit def decodeOptRAssetId(s: Symbol)(implicit js: JSONObject): Option[RAssetId] = opt(s, js => RAssetId(js.getString(s.name)))
  implicit def decodeOptAssetToken(s: Symbol)(implicit js: JSONObject): Option[AssetToken] = opt(s, js => AssetToken(js.getString(s.name)))
  implicit def decodeOptAESKey(s: Symbol)(implicit js: JSONObject): Option[AESKey] = opt(s, js => AESKey(js.getString(s.name)))
  implicit def decodeOptSha256(s: Symbol)(implicit js: JSONObject): Option[Sha256] = opt(s, js => Sha256(js.getString(s.name)))
  implicit def decodeOptRConvId(s: Symbol)(implicit js: JSONObject): Option[RConvId] = opt(s, js => RConvId(js.getString(s.name)))
  implicit def decodeOptMessageId(s: Symbol)(implicit js: JSONObject): Option[MessageId] = opt(s, js => MessageId(js.getString(s.name)))
//  implicit def decodeOptUtcDate(s: Symbol)(implicit js: JSONObject): Option[Date] = opt(s, decodeUtcDate(s)(_))
  implicit def decodeOptInstant(s: Symbol)(implicit js: JSONObject): Option[Instant] = opt(s, decodeInstant(s)(_))
  implicit def decodeOptDuration(s: Symbol)(implicit js: JSONObject): Option[Duration] = opt(s, decodeDuration(s)(_))
  implicit def decodeOptURI(s: Symbol)(implicit js: JSONObject): Option[URI] = opt(s, js => new URI(js.getString(s.name)))
//
  implicit def decodeSeq[A](s: Symbol)(implicit js: JSONObject, dec: JsonDecoder[A]): Vector[A] = decodeColl[A, Vector](s)
//  implicit def decodeArray[A](s: Symbol)(implicit js: JSONObject, dec: JsonDecoder[A], ct: ClassTag[A]): Array[A] = decodeColl[A, Array](s)
  def decodeColl[A, B[_]](s: Symbol)(implicit js: JSONObject, dec: JsonDecoder[A], cbf: CanBuild[A, B[A]]): B[A] = if (js.has(s.name) && !js.isNull(s.name)) arrayColl[A, B](js.getJSONArray(s.name)) else cbf.apply.result
//
  implicit def decodeEmailAddress(s: Symbol)(implicit js: JSONObject): EmailAddress = EmailAddress(js.getString(s.name))
  implicit def decodeOptEmailAddress(s: Symbol)(implicit js: JSONObject): Option[EmailAddress] = opt(s, js => EmailAddress(js.getString(s.name)))
  implicit def decodePhoneNumber(s: Symbol)(implicit js: JSONObject): PhoneNumber = PhoneNumber(js.getString(s.name))
  implicit def decodeOptHandle(s: Symbol)(implicit js: JSONObject): Option[Handle] = opt(s, js => Handle(js.getString(s.name)))
  implicit def decodeOptPhoneNumber(s: Symbol)(implicit js: JSONObject): Option[PhoneNumber] = opt(s, js => PhoneNumber(js.getString(s.name)))
//
//  implicit def decodeGcmSenderId(s: Symbol)(implicit js: JSONObject): GcmSenderId = GcmSenderId(js.getString(s.name))
//
//  implicit def decodeOptCallSequenceNumber(s: Symbol)(implicit js:JSONObject): Option[CallSequenceNumber] = decodeOptInt(s) filter (_ > 1) map CallSequenceNumber // 0 means uninitialized, and 1 can happen by resetting Redis, so it basically just means "unknown", too
//
//  implicit def decodeUidSeq(s: Symbol)(implicit js: JSONObject): Seq[Uid] = array[Uid](s)({ (arr, i) => Uid(arr.getString(i)) })
//  implicit def decodeUserIdSeq(s: Symbol)(implicit js: JSONObject): Seq[UserId] = array[UserId](s)({ (arr, i) => UserId(arr.getString(i)) })
//  implicit def decodeConvIdSeq(s: Symbol)(implicit js: JSONObject): Seq[ConvId] = array[ConvId](s)({ (arr, i) => ConvId(arr.getString(i)) })
//  implicit def decodeDoubleSeq(s: Symbol)(implicit js: JSONObject): Seq[Double] = array[Double](s)({ _.getDouble(_) })
//  implicit def decodeStringSeq(s: Symbol)(implicit js: JSONObject): Seq[String] = array[String](s)({ _.getString(_) })
//  implicit def decodeClientIdSeq(s: Symbol)(implicit js: JSONObject): Seq[ClientId] = array[ClientId](s)({ (arr, i) => ClientId(arr.getString(i)) })
//  implicit def decodeFloatSeq(s: Symbol)(implicit js: JSONObject): Seq[Float] = array[Float](s)({ _.getDouble(_).toFloat })
//
  implicit def decodeUserId(s: Symbol)(implicit js: JSONObject): UserId = UserId(js.getString(s.name))
  implicit def decodeConvId(s: Symbol)(implicit js: JSONObject): ConvId = ConvId(js.getString(s.name))
  implicit def decodeRConvId(s: Symbol)(implicit js: JSONObject): RConvId = RConvId(js.getString(s.name))
  implicit def decodeClientId(s: Symbol)(implicit js: JSONObject): ClientId = ClientId(js.getString(s.name))
  implicit def decodeAssetId(s: Symbol)(implicit js: JSONObject): AssetId = AssetId(js.getString(s.name))
  implicit def decodeRAssetId(s: Symbol)(implicit js: JSONObject): RAssetId = RAssetId(js.getString(s.name))
  implicit def decodeAESKey(s: Symbol)(implicit js: JSONObject): AESKey = AESKey(js.getString(s.name))
  implicit def decodeMessageId(s: Symbol)(implicit js: JSONObject): MessageId = MessageId(js.getString(s.name))

  implicit def decodeId[A <: Id](s: Symbol)(implicit js: JSONObject, id: IdGen[A]): A = id.decode(js.getString(s.name))

  implicit def decodeOptId[A <: Id](s: Symbol)(implicit js: JSONObject, id: IdGen[A]): Option[A] = if (js.has(s.name) && !js.isNull(s.name)) Some(id.decode(js.getString(s.name))) else None

  implicit def decodeOptWidth(s: Symbol)(implicit js: JSONObject): Option[Dimensions.W] = opt(s, dec => Dimensions.W(dec.getInt(s.name)))
  implicit def decodeWidth(s: Symbol)(implicit js: JSONObject): Dimensions.W = decodeOptWidth(s).getOrElse(W(0))
  implicit def decodeOptHeight(s: Symbol)(implicit js: JSONObject): Option[Dimensions.H] = opt(s, dec => Dimensions.H(dec.getInt(s.name)))
  implicit def decodeHeight(s: Symbol)(implicit js: JSONObject): Dimensions.H = decodeOptHeight(s).getOrElse(H(0))
}
