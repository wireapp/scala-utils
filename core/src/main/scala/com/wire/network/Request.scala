package com.wire.network

import java.net.{URI, URLEncoder}

import com.wire.download.ProgressIndicator
import com.wire.network.ContentEncoder.{EmptyContentEncoder, EmptyRequestContent}
import com.wire.network.Request.ProgressCallback
import com.wire.network.Response.ResponseBodyDecoder
import com.wire.utils.Timeouts

import scala.concurrent.duration.FiniteDuration

case class Request[A: ContentEncoder](httpMethod: String = Request.GetMethod,
                                      resourcePath: Option[String] = None,
                                      absoluteUri: Option[URI] = None,
                                      data: Option[A] = None,
                                      decoder: Option[ResponseBodyDecoder] = None,
                                      uploadCallback: Option[ProgressCallback] = None,
                                      downloadCallback: Option[ProgressCallback] = None,
                                      requiresAuthentication: Boolean = true,
                                      headers: Map[String, String] = Request.EmptyHeaders,
                                      retryPolicy: RetryPolicy = RetryPolicy.NeverRetry,
                                      followRedirect: Boolean = true,
                                      timeout: FiniteDuration = Timeouts.DefaultNetworkTimeout
                                     ) {

  assert(uploadCallback.isEmpty, "uploadCallback is not supported yet") //TODO

  require(resourcePath.isDefined || absoluteUri.isDefined, "Either resourcePath or absoluteUri has to be specified")

  def getBody = data.map(implicitly[ContentEncoder[A]].apply).getOrElse(EmptyRequestContent)
}

object Request {
  type ProgressCallback = ProgressIndicator.ProgressData => Unit

  val PostMethod = "POST"
  val PutMethod = "PUT"
  val GetMethod = "GET"
  val DeleteMethod = "DELETE"
  val HeadMethod = "HEAD"

  val EmptyHeaders = Map[String, String]()

  def Post[A: ContentEncoder](path: String, data: A, uploadCallback: Option[ProgressCallback] = None, requiresAuthentication: Boolean = true, headers: Map[String, String] = EmptyHeaders, timeout: FiniteDuration = Timeouts.DefaultNetworkTimeout) =
    Request[A](PostMethod, Some(path), data = Some(data), uploadCallback = uploadCallback, requiresAuthentication = requiresAuthentication, headers = headers, timeout = timeout)

  def Put[A: ContentEncoder](path: String, data: A, uploadCallback: Option[ProgressCallback] = None, requiresAuthentication: Boolean = true, headers: Map[String, String] = EmptyHeaders) =
    Request[A](PutMethod, Some(path), data = Some(data), uploadCallback = uploadCallback, requiresAuthentication = requiresAuthentication, headers = headers)

  def Delete[A: ContentEncoder](path: String, data: Option[A] = None, requiresAuthentication: Boolean = true, headers: Map[String, String] = EmptyHeaders) =
    Request[A](DeleteMethod, Some(path), data = data, requiresAuthentication = requiresAuthentication, headers = headers)

  def Get(path: String, downloadCallback: Option[ProgressCallback] = None, requiresAuthentication: Boolean = true, headers: Map[String, String] = EmptyHeaders) =
    Request[Unit](GetMethod, Some(path), downloadCallback = downloadCallback, requiresAuthentication = requiresAuthentication, headers = headers)(EmptyContentEncoder)

  def query(path: String, args: (String, Any)*): String = {
    args map {
      case (key, value) =>
        URLEncoder.encode(key, "utf8") + "=" + URLEncoder.encode(value.toString, "utf8")
    } mkString(path + (if (path.contains('?')) "&" else "?"), "&", "")
  }
}
