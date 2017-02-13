package com.wire.otr

import scala.concurrent.Future

trait CryptoBoxService {

  def cryptoBox: Future[Option[CryptoBox]]

  def deleteCryptoBox(): Future[Unit]

}


//TODO include JVM cryptobox and remove this class
trait CryptoBox {

}