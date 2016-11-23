package com.wire.network

import com.wire.cache.CacheService
import com.wire.network.Response.Status
import com.wire.network.ResponseConsumer.{ByteArrayConsumer, FileConsumer, JsonConsumer, StringConsumer}


case class Response(status: Response.Status,
                    headers: Response.Headers = Response.EmptyHeaders,
                    body: ResponseContent = EmptyResponse)

object Response {

  sealed trait Status {
    def isSuccess: Boolean

    def msg: String

    val status: Int

    def isFatal = Status.isFatal(status)
  }

  object Status {
    val Success = 200
    val Created = 201
    val NoResponse = 204

    val MovedPermanently = 301
    val MovedTemporarily = 302
    val SeeOther = 303

    val BadRequest = 400
    val Unauthorized = 401
    val Forbidden = 403
    val NotFound = 404
    val RateLimiting = 420
    val LoginRateLimiting = 429
    val Conflict = 409
    val PreconditionFailed = 412

    val InternalErrorCode = 499
    val CancelledCode = 498
    val UnverifiedCode = 497
    val TimeoutCode = 599
    val ConnectionErrorCode = 598

    def isFatal(status: Int) = status != Unauthorized && status != RateLimiting && status / 100 == 4
  }

  object ErrorStatus {
    def unapply(status: Status) = !status.isSuccess
  }

  object ServerErrorStatus {
    def unapply(status: Status) = status match {
      case HttpStatus(s, _) => s / 100 == 5
      case _ => false
    }
  }

  object ClientErrorStatus {
    def unapply(status: Status) = status match {
      case HttpStatus(s, _) => s / 100 == 4
      case _ => false
    }
  }

  object SuccessStatus {
    def unapply(status: Status) = status.isSuccess
  }

  object SuccessHttpStatus {
    def unapply(status: Status) = status match {
      case s: HttpStatus => s.isSuccess
      case _ => false
    }
  }

  case class HttpStatus(status: Int, msg: String = "") extends Status {
    override def isSuccess: Boolean = status / 100 == 2
  }

  /**
    * Response status indicating some internal exception happening during request or response processing.
    * This should generally indicate a bug in our code and we don't know if the request was processed by server.
    */
  case class InternalError(msg: String, cause: Option[Throwable] = None, httpStatus: Option[HttpStatus] = None) extends Status {
    override val isSuccess: Boolean = false
    override val status: Int = Status.InternalErrorCode
  }

  /**
    * Response status indicating internet connection problem.
    */
  case class ConnectionError(msg: String) extends Status {
    override val isSuccess: Boolean = false
    override val status: Int = Status.ConnectionErrorCode
  }

  case object Cancelled extends Status {
    override val msg = "Cancelled by user"
    override val isSuccess = false
    override val status: Int = Status.CancelledCode
  }

  case object ClientClosed extends Status {
    override val msg = "ZNetClient has been closed"
    override val isSuccess = false
    override val status: Int = 603
  }


  trait ResponseBodyDecoder {
    def apply(contentType: String, contentLength: Long): ResponseConsumer[_ <: ResponseContent]
  }

  object DefaultResponseBodyDecoder extends ResponseBodyDecoder {
    val TextContent = "text/.*".r
    val JsonContent = "application/json.*".r
    val ImageContent = "image/.*".r

    override def apply(contentType: String, contentLength: Long): ResponseConsumer[_ <: ResponseContent] =
      contentType match {
        case JsonContent() => new JsonConsumer(contentLength)
        case TextContent() => new StringConsumer(contentLength)
        case _ => new ByteArrayConsumer(contentLength, contentType)
      }
  }

  def CacheResponseBodyDecoder(cache: CacheService) = new ResponseBodyDecoder {

    import DefaultResponseBodyDecoder._

    val InMemoryThreshold = 24 * 1024

    override def apply(contentType: String, contentLength: Long): ResponseConsumer[_ <: ResponseContent] =
      contentType match {
        case JsonContent() => new JsonConsumer(contentLength)
        case TextContent() => new StringConsumer(contentLength)
        case _ if contentLength > InMemoryThreshold => new FileConsumer(contentType)(cache)
        case _ => new ByteArrayConsumer(contentLength, contentType)
      }
  }

  trait Headers {
    def apply(key: String): Option[String]

    def foreach(key: String)(f: String => Unit): Unit
  }

  case class DefaultHeaders(headers: Map[String, String]) extends Headers {

    override def apply(key: String): Option[String] = headers.get(key)

    override def foreach(key: String)(f: (String) => Unit): Unit = headers.foreach { case (_, s) => f(s) }

  }

  //TODO default implementation of Headers, replacing KoushHeaders with something JVM friendly

  case object EmptyHeaders extends Headers {
    override def apply(key: String): Option[String] = None

    override def foreach(key: String)(f: (String) => Unit): Unit = ()
  }

}
