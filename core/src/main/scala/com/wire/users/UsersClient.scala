package com.wire.users

import com.wire.network.ZNetClient.ErrorOrResponse

trait UsersClient {
  def loadSelf(): ErrorOrResponse[UserInfo]
}
