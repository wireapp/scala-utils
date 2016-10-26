package com.wire.utils

import java.io._
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPOutputStream

import com.wire.macros.logging.LogTag
import com.wire.macros.returning
import com.wire.threading.CancellableFuture

import scala.collection.Iterator.continually
import scala.concurrent.{ExecutionContext, Promise}
import scala.util.control.NonFatal

object IOUtils {
  private val buffer = new ThreadLocal[Array[Byte]] {
    override def initialValue(): Array[Byte] = new Array[Byte](8096)
  }

  def copy(in: InputStream, out: OutputStream): Long = {
    try {
      val buff = buffer.get()
      returning(continually(in read buff).takeWhile(_ != -1).map(returning(_)(out.write(buff, 0, _)).toLong).sum) { _ =>
        out match {
          case out: FileOutputStream =>
            out.flush()
            out.getFD.sync()
          case _ => // nothing to do
        }
      }
    } finally {
      // make sure both streams are closed
      var rethrow = None: Option[Throwable]
      try {
        out.close()
      } catch {
        case NonFatal(ex) => rethrow = Some(ex)
      }
      try {
        in.close()
      } catch {
        case NonFatal(ex) => rethrow = Some(ex)
      }
      rethrow foreach (throw _)
    }
  }

  def copy(in: InputStream, out: File): Long = {
    out.getParentFile.mkdirs()
    copy(in, new FileOutputStream(out))
  }

  def copy(in: File, out: File): Long = {
    out.getParentFile.mkdirs()
    copy(new FileInputStream(in), new BufferedOutputStream(new FileOutputStream(out)))
  }

  def copyAsync(in: => InputStream, out: File)(implicit ec: ExecutionContext): CancellableFuture[Long] =
    copyAsync(in, new BufferedOutputStream(new FileOutputStream(out)))

  def copyAsync(in: => InputStream, out: => OutputStream)(implicit ec: ExecutionContext): CancellableFuture[Long] = {
    val promise = Promise[Long]
    val cancelled = new AtomicBoolean(false)

    promise.trySuccess(copy(new CancellableStream(in, cancelled), out))

    new CancellableFuture(promise) {
      override def cancel()(implicit tag: LogTag): Boolean = cancelled.compareAndSet(false, true)
    }
  }

  def toByteArray(in: InputStream) = {
    val out = new ByteArrayOutputStream()
    copy(in, out)
    out.toByteArray
  }

  def gzip(data: Array[Byte]) = {
    val bos = new ByteArrayOutputStream()

    withResource(new GZIPOutputStream(bos)) { os =>
      os.write(data)
      os.finish()
    }
    bos.toByteArray
  }

  def skip(is: InputStream, count: Long): Boolean = {
    val skipped = is.skip(count)
    if (skipped < 0) false
    else if (skipped < count) skip(is, count - skipped)
    else true
  }

  def readFully(is: InputStream, buffer: Array[Byte], offset: Int, count: Int): Boolean = {
    val read = is.read(buffer, offset, count)
    if (read < 0) false
    else if (read == count) true
    else readFully(is, buffer, offset + read, count - read)
  }

  def asString(in: InputStream) = new String(toByteArray(in), "utf8")

  def withResource[I: Resource, O](in: I)(op: I => O): O = try op(in) finally implicitly[Resource[I]].close(in)

  def withResource[I <: Closeable, O](in: I)(op: I => O): O = try op(in) finally in.close()

  def counting[A](o: => OutputStream)(op: OutputStream => A): (Long, A) = {
    var written = 0L
    val delegate = o
    val out = new OutputStream {
      override def write(buffer: Array[Byte], offset: Int, length: Int): Unit = {
        delegate.write(buffer, offset, length)
        if (length > 0L) written += math.min(buffer.length - offset, length)
      }

      override def write(oneByte: Int): Unit = {
        delegate.write(oneByte)
        written += 1L
      }

      override def flush(): Unit = delegate.flush()

      override def close(): Unit = delegate.close()
    }
    try {
      val a = op(out)
      (written, a)
    } finally out.close()
  }

  def md5(file: File): Array[Byte] = hash(file, "MD5")

  def md5(stream: => InputStream): Array[Byte] = hash(stream, "MD5")

  def sha256(file: File): Array[Byte] = hash(file, "SHA-256")

  def sha256(is: InputStream): Array[Byte] = hash(is, "SHA-256")

  def hash(file: File, hashAlgorithm: String): Array[Byte] = hash(new FileInputStream(file), hashAlgorithm)

  def hash(stream: => InputStream, hashAlgorithm: String): Array[Byte] = withResource(stream) { in =>
    val digest = MessageDigest.getInstance(hashAlgorithm)
    val buff = buffer.get()
    continually(in read buff) takeWhile (_ != -1) foreach (size => digest.update(buff, 0, size))
    digest.digest()
  }

  def deleteRecursively(file: File): Unit = {
    if (file.isDirectory)
      Option(file.listFiles()).foreach(_ foreach deleteRecursively)
    file.delete()
  }
}

class CancellableStream(stream: InputStream, cancelled: AtomicBoolean) extends FilterInputStream(stream) {
  override def read(buffer: Array[Byte], byteOffset: Int, byteCount: Int): Int = {
    if (cancelled.get) throw CancellableFuture.DefaultCancelException
    else super.read(buffer, byteOffset, byteCount)
  }

  override def read(): Int = {
    if (cancelled.get) throw CancellableFuture.DefaultCancelException
    else super.read()
  }
}

trait Resource[Res] {
  def close(r: Res): Unit
}