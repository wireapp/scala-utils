package com.wire.auth

import com.wire.auth.AuthenticationManager.Token
import com.wire.testutils.FullFeatureSpec

class DefaultAuthenticationManagerTest extends FullFeatureSpec {


  scenario("Let's get started") {

    val client = new LoginClient {

      override def access(cookie: Option[String], token: Option[Token]) = ???

      override def login(accountId: Any, credentials: Credentials) = ???
    }


  }


}
