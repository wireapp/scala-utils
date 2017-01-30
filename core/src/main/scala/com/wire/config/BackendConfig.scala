package com.wire.config

import com.wire.config.BackendConfig.PushId

case class BackendConfig(baseUrl: String, pushUrl: String, gcmSenderId: PushId, environment: String) {
  import BackendConfig._
  if (gcmSenderId != stagingSenderId && gcmSenderId != prodSenderId) throw new IllegalArgumentException(s"Unknown sender id: $gcmSenderId")
}

object BackendConfig {
  case class PushId(str: String) extends AnyVal
  val Seq(stagingSenderId, prodSenderId) = Seq("723990470614", "782078216207") map PushId

  val StagingBackend = BackendConfig("https://staging-nginz-https.zinfra.io", "https://staging-nginz-ssl.zinfra.io/await", stagingSenderId, "staging")
  val ProdBackend = BackendConfig("https://prod-nginz-https.wire.com", "https://prod-nginz-ssl.wire.com/await", prodSenderId, "prod")

  lazy val byName = Seq(StagingBackend, ProdBackend).map(b => b.environment -> b).toMap

  def apply(baseUrl: String): BackendConfig = BackendConfig(baseUrl, "", stagingSenderId, "") // XXX only use for testing!
}



