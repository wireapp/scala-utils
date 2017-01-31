package com.wire.auth

import com.wire.auth.AuthenticationManager.Cookie
import com.wire.data.AccountId
import com.wire.network.AccessTokenProvider.Token
import com.wire.storage.Preference
import org.json.JSONObject


trait Credentials {
  def canLogin: Boolean = true
  def autoLoginOnRegistration: Boolean = true
  def addToRegistrationJson(o: JSONObject): Unit = ()

  val email = Option.empty[EmailAddress]
  val phone = Option.empty[PhoneNumber]
  val password = Option.empty[String]
  val username = Option.empty[Handle]

  override def toString = s"Credentials: email: $email, password: $password"
}


trait CredentialsHandler {
  val userId: AccountId
  val cookie: Preference[Cookie]
  val accessToken: Preference[Option[Token]]

  def credentials: Credentials
  def onInvalidCredentials(): Unit = {}
}
