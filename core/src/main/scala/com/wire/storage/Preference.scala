package com.wire.storage

import com.wire.events.{Signal, SourceSignal}
import com.wire.threading.{SerialDispatchQueue, Threading}
import org.threeten.bp.Instant

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

    //    implicit def idCodec[A: Id]: PrefCodec[A] = apply[A](implicitly[Id[A]].encode, implicitly[Id[A]].decode)
    implicit def optCodec[A: PrefCodec]: PrefCodec[Option[A]] = apply[Option[A]](_.fold("")(implicitly[PrefCodec[A]].encode), { str => if (str == "") None else Some(implicitly[PrefCodec[A]].decode(str)) })

    implicit val InstantCodec = apply[Instant](d => String.valueOf(d.toEpochMilli), s => Instant.ofEpochMilli(java.lang.Long.parseLong(s)))
    //    implicit val TokenCodec = apply[Option[Token]] (
    //      { t => optCodec[String].encode(t map Token.Encoder.apply map (_.toString)) },
    //      { s => optCodec[String].decode(s) map (new JSONObject(_)) map (Token.Decoder.apply(_)) })
  }

}