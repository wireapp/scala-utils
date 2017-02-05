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
package com.wire.storage

import com.wire.data.{Id, IdGen}
import com.wire.reactive.{Signal, SourceSignal}
import com.wire.threading.{SerialDispatchQueue, Threading}
import org.threeten.bp.Instant
import com.wire.logging.ZLog.ImplicitTag._

import scala.concurrent.Future

trait Preference[A] {
  def default: A

  def apply(): Future[A]

  def :=(value: A): Future[Unit]

  lazy val signal: SourceSignal[A] = {
    val s = Signal[A]()
    apply().onSuccess { case v => s.publish(v, Threading.Background) }(Threading.Background)
    s
  }
}

object Preference {
  def empty[A] = new Preference[Option[A]] {
    def default = None

    def apply() = Future.successful(None)

    def :=(value: Option[A]) = Future.successful(())
  }

  def inMemory[A](defaultValue: A): Preference[A] = new Preference[A] {
    private implicit val dispatcher = new SerialDispatchQueue()
    private var value = defaultValue

    override def default = defaultValue

    override def :=(v: A) = Future {
      value = v; signal ! v
    }

    override def apply() = Future {
      value
    }
  }

  def apply[A](defaultValue: A, load: => Future[A], save: A => Future[Any]): Preference[A] = new Preference[A] {

    import Threading.Implicits.Background

    override def default = defaultValue

    override def :=(v: A) = save(v) map { _ => signal ! v }

    override def apply() = load
  }

  trait PrefCodec[A] {
    def encode(v: A): String

    def decode(str: String): A
  }

  object PrefCodec {
    def apply[A](enc: A => String, dec: String => A): PrefCodec[A] = new PrefCodec[A] {
      override def encode(v: A): String = enc(v)

      override def decode(str: String): A = dec(str)
    }

    implicit val StrCodec = apply[String](identity, identity)
    implicit val IntCodec = apply[Int](String.valueOf, java.lang.Integer.parseInt)
    implicit val LongCodec = apply[Long](String.valueOf, java.lang.Long.parseLong)
    implicit val BooleanCodec = apply[Boolean](String.valueOf, java.lang.Boolean.parseBoolean)

    implicit def idCodec[A <: Id: IdGen]: PrefCodec[A] = apply[A](implicitly[IdGen[A]].encode, implicitly[IdGen[A]].decode)
    implicit def optCodec[A: PrefCodec]: PrefCodec[Option[A]] = apply[Option[A]](_.fold("")(implicitly[PrefCodec[A]].encode), { str => if (str == "") None else Some(implicitly[PrefCodec[A]].decode(str)) })

    implicit val InstantCodec = apply[Instant](d => String.valueOf(d.toEpochMilli), s => Instant.ofEpochMilli(java.lang.Long.parseLong(s)))
    //    implicit val TokenCodec = apply[Option[Token]] (
    //      { t => optCodec[String].encode(t map Token.Encoder.apply map (_.toString)) },
    //      { s => optCodec[String].decode(s) map (new JSONObject(_)) map (Token.Decoder.apply(_)) })
  }

}
