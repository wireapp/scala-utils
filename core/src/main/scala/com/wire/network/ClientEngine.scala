package com.wire.network

import com.wire.threading.CancellableFuture

import scala.concurrent.Future

trait ClientEngine {

  def fetch[A](requestName: String = "", r: Request[A]): CancellableFuture[Response]

}

object ClientEngine {
  type ErrorOrResponse[T] = CancellableFuture[Either[ErrorResponse, T]]
  type ErrorOr[A] = Future[Either[ErrorResponse, A]]
}