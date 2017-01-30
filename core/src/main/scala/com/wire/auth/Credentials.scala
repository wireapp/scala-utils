package com.wire.auth

import com.wire.auth.AuthenticationManager.Cookie
import com.wire.data.AccountId
import com.wire.network.AccessTokenProvider.Token
import com.wire.storage.Preference
import org.json.JSONObject


trait Credentials {
  def canLogin: Boolean
  def autoLoginOnRegistration: Boolean
  def addToRegistrationJson(o: JSONObject): Unit
  def addToLoginJson(o: JSONObject): Unit

  def maybeEmail: Option[EmailAddress]
  def maybePhone: Option[PhoneNumber]
  def maybePassword: Option[String]
  def maybeUsername: Option[Handle]
}


trait CredentialsHandler {
  val userId: AccountId
  val cookie: Preference[Cookie]
  val accessToken: Preference[Option[Token]]

  def credentials: Credentials
  def onInvalidCredentials(): Unit = {}
}
