package com.wire.network

import java.net.{ConnectException, URI, UnknownHostException}
import java.util.concurrent.TimeoutException

import com.wire.network.ContentEncoder.{EmptyRequestContent, RequestContent}
import com.wire.network.Request.ProgressCallback
import com.wire.network.Response.ResponseBodyDecoder
import com.wire.threading.CancellableFuture
import com.wire.threading.CancellableFuture.CancelException

import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.control.NonFatal

trait AsyncClient {
  import AsyncClient._
  def apply(uri:                      URI,
            method:                   String                      = Request.GetMethod,
            body:                     RequestContent              = EmptyRequestContent,
            headers:                  Map[String, String]         = EmptyHeaders,
            followRedirect:           Boolean                     = true,
            timeout:                  FiniteDuration              = DefaultTimeout,
            decoder:                  Option[ResponseBodyDecoder] = None,
            downloadProgressCallback: Option[ProgressCallback]    = None): CancellableFuture[Response]
}

object AsyncClient {
  val MultipartPostTimeout = 15.minutes
  val DefaultTimeout = 30.seconds
  val EmptyHeaders = Map[String, String]()

  val UserAgentHeader = "User-Agent"
  val ContentTypeHeader = "Content-Type"

  def userAgent(appVersion: String = "*", zmsVersion: String = "*") = {
    s"Wire/$appVersion (zms $zmsVersion; scala-utils)"
  }

  private def exceptionStatus: PartialFunction[Throwable, Response] = {
    case e: ConnectException => Response(Response.ConnectionError(e.getMessage))
    case e: UnknownHostException => Response(Response.ConnectionError(e.getMessage))
    case e: ConnectionClosedException => Response(Response.ConnectionError(e.getMessage))
    case e: ConnectionFailedException => Response(Response.ConnectionError(e.getMessage))
    case e: RedirectLimitExceededException => Response(Response.ConnectionError(e.getMessage))
    case e: TimeoutException => Response(Response.ConnectionError(e.getMessage))
    case e: CancelException => Response(Response.Cancelled)
    case NonFatal(e) => Response(Response.InternalError(e.getMessage, Some(e)))
  }

  class ConnectionClosedException(message: String, throwable: Throwable) extends Exception(message, throwable)

  class ConnectionFailedException(message: String) extends Exception(message)

  class RedirectLimitExceededException(message: String) extends Exception(message)

}
