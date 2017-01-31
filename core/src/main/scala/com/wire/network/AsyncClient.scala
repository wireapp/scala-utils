package com.wire.network

import java.net.{ConnectException, URI, UnknownHostException}
import java.util.concurrent.TimeoutException

import com.wire.logging.Logging.{info, warn}
import com.wire.macros.logging.ImplicitTag._
import com.wire.macros.returning
import com.wire.network.ContentEncoder.{EmptyRequestContent, GzippedRequestContent, MultipartRequestContent, RequestContent}
import com.wire.network.Request.ProgressCallback
import com.wire.network.Response.{DefaultHeaders, DefaultResponseBodyDecoder, ResponseBodyDecoder}
import com.wire.threading.CancellableFuture.CancelException
import com.wire.threading.{CancellableFuture, SerialDispatchQueue}
import org.apache.http.client.{HttpResponseException, fluent}
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.json.JSONObject

import scala.concurrent.duration.{FiniteDuration, _}
import scala.util.Try
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

class ApacheHTTPAsyncClient(bodyDecoder: ResponseBodyDecoder = DefaultResponseBodyDecoder, val userAgent: String = AsyncClient.userAgent()) extends AsyncClient {

  protected implicit val dispatcher = new SerialDispatchQueue(name = "AsyncClient")

  private val client = HttpClients.createDefault()

  import AsyncClient._
  override def apply(uri:                      URI,
                     method:                   String                      = Request.GetMethod,
                     body:                     RequestContent              = EmptyRequestContent,
                     headers:                  Map[String, String]         = EmptyHeaders,
                     followRedirect:           Boolean                     = true,
                     timeout:                  FiniteDuration              = DefaultTimeout,
                     decoder:                  Option[ResponseBodyDecoder] = None,
                     downloadProgressCallback: Option[ProgressCallback]    = None): CancellableFuture[Response] = {
    info(s"Starting request[$method]($uri) with headers: '$headers' and body: $body")
    val requestTimeout = if (method != Request.PostMethod) timeout else body match {
      case _: MultipartRequestContent => MultipartPostTimeout
      case _ => timeout
    }

    CancellableFuture {

      try {
        val res = buildHttpRequest(uri, method, body, headers, followRedirect, requestTimeout).execute().returnResponse()

        Response(
          Response.HttpStatus(res.getStatusLine.getStatusCode, res.getStatusLine.getReasonPhrase),
          DefaultHeaders(res.getAllHeaders.map(h => h.getName -> h.getValue).toMap),
          Try(JsonObjectResponse(new JSONObject(EntityUtils.toString(res.getEntity)))).toOption.getOrElse(EmptyResponse)
        )
      } catch {
        case e: HttpResponseException => Response(Response.HttpStatus(e.getStatusCode, e.getMessage))
      }
    }
  }


  private def buildHttpRequest(uri: URI, method: String, body: RequestContent, headers: Map[String, String], followRedirect: Boolean, timeout: FiniteDuration): fluent.Request = {
    if (method != Request.GetMethod && method != Request.PostMethod) warn(s"Not handling $method methods yet")

    val t = timeout.toMillis.toInt

    returning(
      (method match {
        case Request.PostMethod => fluent.Request.Post(uri)
        case _ => fluent.Request.Get(uri)
      }).connectTimeout(t)
        .socketTimeout(t)
    ) { r =>
      headers.foreach { case (k, v) => r.setHeader(k, v.trim) }
      r.setHeader(UserAgentHeader, userAgent)
      body match {
        case GzippedRequestContent(bytes, contentType) if contentType == "application/json" => r.bodyByteArray(bytes, ContentType.APPLICATION_JSON)
        case _ =>
      }
    }
  }

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
