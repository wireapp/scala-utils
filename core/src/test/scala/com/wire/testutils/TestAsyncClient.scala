package com.wire.testutils

import java.net.URI

import com.wire.network.ContentEncoder.{EmptyRequestContent, RequestContent}
import com.wire.network.Request.ProgressCallback
import com.wire.network.Response.ResponseBodyDecoder
import com.wire.network.{AsyncClient, Request, Response}
import com.wire.threading.{CancellableFuture, SerialDispatchQueue}

import scala.concurrent.duration.FiniteDuration

class TestAsyncClient extends AsyncClient {

  protected implicit val dispatcher = new SerialDispatchQueue(name = "TestAsyncClient")

  import AsyncClient._
  override def apply(uri:                      URI,
                     method:                   String                      = Request.GetMethod,
                     body:                     RequestContent              = EmptyRequestContent,
                     headers:                  Map[String, String]         = EmptyHeaders,
                     followRedirect:           Boolean                     = true,
                     timeout:                  FiniteDuration              = DefaultTimeout,
                     decoder:                  Option[ResponseBodyDecoder] = None,
                     downloadProgressCallback: Option[ProgressCallback]    = None): CancellableFuture[Response] = {

    CancellableFuture.successful {
      Thread.sleep(500)
      Response.apply(Response.HttpStatus(200, "success"))
    }
  }
}
