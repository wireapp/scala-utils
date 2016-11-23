package com.wire.otr

trait OtrSyncHandler {
  def postOtrImageData: Nothing
}

class OtrSyncHandlerImpl extends OtrSyncHandler {
  override def postOtrImageData: Nothing = ???
}
