package com.wire.auth

import com.wire.auth.AuthenticationManager.Cookie
import com.wire.data.AccountId
import com.wire.network.ZNetClient.ErrorOrResponse
import com.wire.users.UserInfo

trait RegistrationClient {
  def register(id: AccountId, creds: Credentials, name: String, accentId: Option[Int]): ErrorOrResponse[(UserInfo, Cookie)]

  def requestVerificationEmail(email: EmailAddress): ErrorOrResponse[Unit]
}
