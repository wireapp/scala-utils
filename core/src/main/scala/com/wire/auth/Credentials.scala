package com.wire.auth

import com.wire.auth.AuthenticationManager.Cookie
import com.wire.data.AccountId
import com.wire.network.AccessTokenProvider.Token
import com.wire.storage.Preference
import sun.security.util.Password


trait CredentialsHandler {
  val userId: AccountId
  val cookie: Preference[Cookie]
  val accessToken: Preference[Option[Token]]

  def credentials: Credentials
  def onInvalidCredentials(): Unit = {}
}

sealed trait Credentials {
  def canLogin: Boolean = true
  def autoLoginOnRegistration: Boolean = true

  //TODO add invitation code
  val email    = Option.empty[EmailAddress]
  val phone    = Option.empty[PhoneNumber]
  val password = Option.empty[String]
  val handle   = Option.empty[Handle]

  override def toString = s"Credentials: email: $email, password: $password"
}


object Credentials {
  val Empty = new Credentials {
    override def canLogin: Boolean = false
    override def autoLoginOnRegistration: Boolean = false
  }

  case class EmailCredentials(override val email: Option[EmailAddress], override val password: Option[String] = None) extends Credentials {
    override def canLogin: Boolean = email.isDefined && password.isDefined
    override def autoLoginOnRegistration = false
  }

  case class PhoneCredentials(override val phone: Option[PhoneNumber], code: Option[ConfirmationCode]) extends Credentials {
    override def canLogin: Boolean = false
    override def autoLoginOnRegistration: Boolean = true
  }

  case class UsernameCredentials(override val handle: Option[Handle], override val password: Option[String]) extends Credentials {
    override def canLogin: Boolean = password.isDefined
    override def autoLoginOnRegistration = false
  }

}
