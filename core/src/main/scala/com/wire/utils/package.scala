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

import java.net.URI
import java.security.MessageDigest
import java.util.Date
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference

import com.wire.error.LoggedTry
import com.wire.macros.logging.LogTag
import com.wire.threading.{CancellableFuture, Threading}
import org.apache.commons.codec.binary.Base64
import org.threeten.bp
import org.threeten.bp.Instant
import org.threeten.bp.Instant.now

import scala.annotation.tailrec
import scala.collection.Searching.{Found, InsertionPoint, SearchResult}
import scala.collection.generic.CanBuild
import scala.collection.{GenIterable, SeqView}
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.language.{higherKinds, implicitConversions}
import scala.math.{Ordering, abs}
import scala.util.{Failure, Success, Try}
import scala.{PartialFunction => =/>}

package object utils {

  //collection string representation formatted one item per line
  //TODO make into a macro or compiler plugin etc.
  //TODO find the general collection name (currently prints specific implementation name, e.g. HashTrieMap instead of Map)
  def fCol[A](c: GenIterable[A], max: Int = 6) = {
    val name = c.getClass.getSimpleName
    val margin = String.format("%1$" + (name.length + 1) + "s", " ")
    val n = s"\n$margin"
    val startN = s"${if (c.size > 1) "\n" else ""}"
    if (c.size <= max + 1) {
      s"$startN$name(${c.mkString(n)})"
    } else {
      s"$startN$name(${c.take(3).mkString(n)}$n...hiding ${c.size - max} elements...$n${c.toVector.takeRight(3).mkString(n)})"
    }
  }

  def sha2(s: String): String = Base64.encodeBase64String(MessageDigest.getInstance("SHA-256").digest(s.getBytes("utf8")))

  def sha2(bytes: Array[Byte]): String = Base64.encodeBase64String(MessageDigest.getInstance("SHA-256").digest(bytes))

  trait SHA2Digest {
    def apply(s: String): String
  }

  def withCleanupOnFailure[A](f: => A)(pf: Throwable => Unit): A = try f catch { case cause: Throwable => pf(cause); throw cause }

  def force[A](s: Seq[A]) = s match {
    case _: Stream[_] => s.toVector
    case _: SeqView[_, _] => s.toVector
    case _ => s
  }

  def assertEager[A](res: A) = {
    def fail = throw new UnsupportedOperationException(s"assertEager($res) found lazy collection!")
    res match {
      case _: Stream[_] => fail
      case _: Iterator[_] => fail
      case _: SeqView[_, _] => fail
      case _ => res
    }
  }
  def max(d1: Date, d2: Date): Date = if (d2.after(d1)) d2 else d1
  def min(d1: Date, d2: Date): Date = if (d2.after(d1)) d1 else d2
  def nanoNow = System.nanoTime.nanos

  @tailrec
  def compareAndSet[A](ref: AtomicReference[A])(updater: A => A): A = {
    val current = ref.get
    val updated = updater(current)
    if (ref.compareAndSet(current, updated)) updated
    else compareAndSet(ref)(updater)
  }

  implicit class RichTraversableOnce[A](val b: TraversableOnce[A]) extends AnyVal {
    def by[B, C[_, _]](f: A => B)(implicit cbf: CanBuild[(B, A), C[B, A]]): C[B, A] = byMap[B, A, C](f, identity)

    def byMap[B, C, D[_, _]](f: A => B, g: A => C)(implicit cbf: CanBuild[(B, C), D[B, C]]): D[B, C] = {
      val builder = cbf()
      builder ++= b.map(a => (f(a), g(a)))
      builder.result()
    }

    def flatIterator[C, D](implicit ev: A <:< (C, TraversableOnce[D])): Iterator[(C, D)] = b.toIterator.flatMap { a =>
      val (c, ds) = ev(a)
      ds.map(d => (c, d))
    }
  }

  implicit class RichUri(val uri: URI) extends AnyVal {
    def appendQuery(query: String) = new URI(uri.getScheme, uri.getAuthority, uri.getPath, if (uri.getQuery == null) query else s"${uri.getQuery}&$query", uri.getFragment)
    def appendPath(path: String) = new URI(uri.getScheme, uri.getAuthority, s"${uri.getPath}${if (path.startsWith("/")) "" else "/"}$path", uri.getQuery, uri.getFragment)
  }

  implicit class RichIndexedSeq[A](val items: IndexedSeq[A]) extends AnyVal {
    // adapted from scala.collection.Searching
    @tailrec final def binarySearch[B](elem: B, f: A => B, from: Int = 0, to: Int = items.size)(implicit ord: Ordering[B]): SearchResult = {
      if (to == from) InsertionPoint(from) else {
        val idx = from + (to - from - 1) / 2
        math.signum(ord.compare(elem, f(items(idx)))) match {
          case -1 => binarySearch(elem, f, from, idx)(ord)
          case  1 => binarySearch(elem, f, idx + 1, to)(ord)
          case  _ => Found(idx)
        }
      }
    }
  }

  implicit class RichOption[A](val opt: Option[A]) extends AnyVal {
    @inline final def fold2[B](ifEmpty: => B, f: A => B): B = if (opt.isEmpty) ifEmpty else f(opt.get) // option's catamorphism with better type inference properties than the one provided by the std lib
    def mapFuture[B](f: A => Future[B])(implicit ec: ExecutionContext): Future[Option[B]] = flatMapFuture(f(_).map(Some(_)))
    def flatMapFuture[B](f: A => Future[Option[B]]): Future[Option[B]] = fold2(Future.successful(None), f(_))
  }

  implicit class RichEither[L, R](val sum: Either[L, R]) extends AnyVal {
    def map[S](f: R => S): Either[L, S] = sum.right.map(f)
    def flatMap[M >: L, S](f: R => Either[M, S]): Either[M, S] = sum.right.flatMap(f)
    def toOption(effect: L => Unit = _ => ()) = sum.fold(l => { effect(l); None }, Some(_))
    def mapFuture[S](f: R => Future[S])(implicit ec: ExecutionContext): Future[Either[L, S]] = flatMapFuture(f(_).map(Right(_)))
    def flatMapFuture[M >: L, S](f: R => Future[Either[M, S]])(implicit ec: ExecutionContext): Future[Either[M, S]] = sum.fold(l => Future.successful(Left(l)), r => f(r))
  }

  implicit class RichTry[A](val t: Try[A]) extends AnyVal {
    def toRight[L](f: Throwable => L): Either[L, A] = t match {
      case Success(s) => Right(s)
      case Failure(t) => Left(f(t))
    }
  }

  implicit def finiteDurationIsThreetenBPDuration(a: FiniteDuration): bp.Duration = a.asJava

  implicit class RichFiniteDuration(val a: FiniteDuration) extends AnyVal {
    def fromEpoch = Instant.ofEpochMilli(a.toMillis)
    def asJava = bp.Duration.ofNanos(a.toNanos)
    def elapsedSince(b: bp.Instant): Boolean = b plus a isBefore now
    def untilNow = {
      val n = (nanoNow - a).toNanos.toDouble
      if (abs(n) > 1e9d) s"${n.toDouble / 1e9d} s"
      else if (abs(n) > 1e6d) s"${n.toDouble / 1e6d} ms"
      else if (abs(n) > 1e3d) s"${n.toDouble / 1e3d} µs"
      else s"$n ns"
    }
  }

  private val units = List((1000L, "ns"), (1000L, "µs"), (1000L, "ms"), (60L, "s"), (60L, "m"), (60L, "h"), (24L, "d"))

  implicit class RichThreetenBPDuration(val a: bp.Duration) extends AnyVal {
    def asScala: FiniteDuration = a.toNanos.nanos
  }

  implicit class RichDate(val a: Date) extends AnyVal {
    def instant: bp.Instant = bp.Instant.ofEpochMilli(a.getTime)
    def isAfter(i: Instant): Boolean = a.getTime > i.toEpochMilli
    def isBefore(i: Instant): Boolean = a.getTime < i.toEpochMilli
  }

  implicit class RichInstant(val a: bp.Instant) extends AnyVal {
    def javaDate: Date = new Date(a.toEpochMilli)
    def until(b: bp.Instant): FiniteDuration = (b.toEpochMilli - a.toEpochMilli).millis
    def -(d: FiniteDuration) = a.minusNanos(d.toNanos)
    def +(d: FiniteDuration) = a.plusNanos(d.toNanos)
    def isAfter(d: Date): Boolean = a.toEpochMilli > d.getTime
    def isBefore(d: Date): Boolean = a.toEpochMilli < d.getTime
    def max(b: bp.Instant) = if (a isBefore b) b else a
    def max(b: Date) = if (a.toEpochMilli < b.getTime) b else a
    def min(b: bp.Instant) = if (a isBefore b) a else b
    def min(b: Date) = if (a.toEpochMilli < b.getTime) a else b
    def >=(b: bp.Instant) = !a.isBefore(b)
    def <=(b: bp.Instant) = !a.isAfter(b)
  }

  implicit lazy val InstantIsOrdered: Ordering[Instant] = Ordering.ordered[Instant]
  implicit lazy val DurationIsOrdered: Ordering[bp.Duration] = Ordering.ordered[bp.Duration]

  implicit class RichFuture[A](val a: Future[A]) extends AnyVal {
    def flatten[B](implicit executor: ExecutionContext, ev: A <:< Future[B]): Future[B] = a.flatMap(ev)
    def zip[B](f: Future[B])(implicit executor: ExecutionContext) = RichFuture.zip(a, f)
    def recoverWithLog(reportHockey: Boolean = false)(implicit tag: LogTag): Future[Unit] = RichFuture.recoverWithLog(a, reportHockey)
    def lift: CancellableFuture[A] = CancellableFuture.lift(a)
    def andThenFuture[B](pf: Try[A] =/> Future[B])(implicit ec: ExecutionContext): Future[A] = {
      val p = Promise[A]
      a.onComplete(t => if (pf isDefinedAt t) try pf(t).andThen { case _ => p.complete(t) } catch { case _: Throwable => p.complete(t) } else p.complete(t))
      p.future
    }

    def logFailure(reportHockey: Boolean = false)(implicit tag: LogTag): Future[A] =
      a.andThen { case Failure(cause) => LoggedTry.errorHandler(reportHockey)(implicitly)(cause) }(Threading.Background)

    def withTimeout(t: FiniteDuration): Future[A] =
      Future.firstCompletedOf(
        Seq(a, CancellableFuture.delay(t).future.flatMap(_ => Future.failed(new TimeoutException(s"operation timed out after $t")))(Threading.Background))
      )(Threading.Background)
  }

  implicit class RichFutureOpt[A](val a: Future[Option[A]]) extends AnyVal {
    def mapSome[B](f: A => B)(implicit ec: ExecutionContext): Future[Option[B]] = a.map(_.map(f))
    def flatMapSome[B](f: A => Future[B])(implicit ec: ExecutionContext): Future[Option[B]] = a.flatMap(_.mapFuture(f))
    def mapOpt[B](f: A => Option[B])(implicit ec: ExecutionContext): Future[Option[B]] = a.map(_.flatMap(f))
    def flatMapOpt[B](f: A => Future[Option[B]])(implicit ec: ExecutionContext): Future[Option[B]] = a.flatMap(_.flatMapFuture(f))
    def foldOpt[B](e: => Future[Option[B]], f: A => Future[Option[B]])(implicit ec: ExecutionContext): Future[Option[B]] = a.flatMap(_.fold(e)(f))
    def or[L](l: => L): Future[Either[L, A]] = a.map(_.toRight(l))(Threading.Background)
  }

  implicit class RichFutureEither[L, R](val a: Future[Either[L, R]]) extends AnyVal {
    def mapLeft[M](f: L => M)(implicit ec: ExecutionContext): Future[Either[M, R]] = a.map(_.left map f)
    def mapRight[S](f: R => S)(implicit ec: ExecutionContext): Future[Either[L, S]] = a.map(_ map f)
    def mapEither[S](f: R => Either[L, S])(implicit ec: ExecutionContext): Future[Either[L, S]] = a.map(_ flatMap f)
    def flatMapEither[M >: L, S](f: R => Future[Either[M, S]])(implicit ec: ExecutionContext): Future[Either[M, S]] = a.flatMap(_.fold(l => Future.successful(Left(l)), f))
    def flatMapRight[S](f: R => Future[S])(implicit ec: ExecutionContext): Future[Either[L, S]] = a.flatMap(_.fold(l => Future.successful(Left(l)), r => f(r).map(Right(_))))
    def flatMapLeft[M](f: L => Future[M])(implicit ec: ExecutionContext): Future[Either[M, R]] = a.flatMap(_.fold(l => f(l).map(Left(_)), r => Future.successful(Right(r))))
    def foldEither[M, S](m: L => Future[M], s: R => Future[S])(implicit ec: ExecutionContext): Future[Either[M, S]] = a.flatMap(_.fold(l => m(l).map(Left(_)), r => s(r).map(Right(_))))
    def foldEitherMerge[M, S](m: L => Future[Either[M, S]], s: R => Future[Either[M, S]])(implicit ec: ExecutionContext): Future[Either[M, S]] = a.flatMap(_.fold(l => m(l), r => s(r)))
  }

  object RichFuture {

    def zip[A, B](fa: Future[A], fb: Future[B])(implicit executor: ExecutionContext): Future[(A, B)] =
      for (a <- fa; b <- fb) yield (a, b)

    def traverseSequential[A, B](as: Seq[A])(f: A => Future[B])(implicit executor: ExecutionContext): Future[Seq[B]] = {
      def processNext(remaining: Seq[A], acc: List[B] = Nil): Future[Seq[B]] =
        if (remaining.isEmpty) Future.successful(acc.reverse)
        else f(remaining.head) flatMap { res => processNext(remaining.tail, res :: acc) }

      processNext(as)
    }

    def recoverWithLog[A](f: Future[A], reportHockey: Boolean = false)(implicit tag: LogTag): Future[Unit] = {
      val p = Promise[Unit]()
      f.onComplete {
        case Success(_) =>
          p.success(())
        case Failure(t) =>
          p.success(())
          LoggedTry.errorHandler(reportHockey)(implicitly)(t)
      }(Threading.Background)
      p.future
    }

    /**
     * Process sequentially and ignore results.
     * Similar to traverseSequential, but doesn't care about the result, and continues on failure.
     */
    def processSequential[A, B](as: Seq[A])(f: A => Future[B])(implicit executor: ExecutionContext, tag: LogTag): Future[Unit] = {
      def processNext(remaining: Seq[A]): Future[Unit] =
        if (remaining.isEmpty) Future.successful(())
        else LoggedTry(recoverWithLog(f(remaining.head), reportHockey = true)).getOrElse(Future.successful(())) flatMap { _ => processNext(remaining.tail) }

      processNext(as)
    }
  }
}
