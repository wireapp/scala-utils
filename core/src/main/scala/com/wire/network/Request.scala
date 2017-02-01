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
  package com.wire.network

import java.net.URI

import com.wire.download.ProgressIndicator
import com.wire.network.ContentEncoder.{EmptyContentEncoder, EmptyRequestContent}
import com.wire.network.Request.ProgressCallback
import com.wire.network.Response.ResponseBodyDecoder
import com.wire.utils.Timeouts

import scala.concurrent.duration.FiniteDuration

case class Request[A: ContentEncoder](httpMethod: String = Request.GetMethod,
                                      resourcePath: Option[String] = None,
                                      query: Map[String, String] = Map.empty,
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

  def Post[A: ContentEncoder](path: String, query: Map[String, String] = Map.empty, data: A, uploadCallback: Option[ProgressCallback] = None, requiresAuthentication: Boolean = true, headers: Map[String, String] = EmptyHeaders, timeout: FiniteDuration = Timeouts.DefaultNetworkTimeout) =
    Request[A](PostMethod, Some(path), query, data = Some(data), uploadCallback = uploadCallback, requiresAuthentication = requiresAuthentication, headers = headers, timeout = timeout)

  def Put[A: ContentEncoder](path: String, query: Map[String, String] = Map.empty, data: A, uploadCallback: Option[ProgressCallback] = None, requiresAuthentication: Boolean = true, headers: Map[String, String] = EmptyHeaders) =
    Request[A](PutMethod, Some(path), query, data = Some(data), uploadCallback = uploadCallback, requiresAuthentication = requiresAuthentication, headers = headers)

  def Delete[A: ContentEncoder](path: String, query: Map[String, String] = Map.empty, data: Option[A] = None, requiresAuthentication: Boolean = true, headers: Map[String, String] = EmptyHeaders) =
    Request[A](DeleteMethod, Some(path), query, data = data, requiresAuthentication = requiresAuthentication, headers = headers)

  def Get(path: String, query: Map[String, String] = Map.empty, downloadCallback: Option[ProgressCallback] = None, requiresAuthentication: Boolean = true, headers: Map[String, String] = EmptyHeaders) =
    Request[Unit](GetMethod, Some(path), query, downloadCallback = downloadCallback, requiresAuthentication = requiresAuthentication, headers = headers)(EmptyContentEncoder)
}
