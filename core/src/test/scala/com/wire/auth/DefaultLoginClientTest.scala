package com.wire.auth

import com.wire.config.BackendConfig
import com.wire.network.AccessTokenProvider.Token
import com.wire.network.Response.{DefaultHeaders, HttpStatus}
import com.wire.network._
import com.wire.testutils.FullFeatureSpec
import com.wire.threading.{CancellableFuture, SerialDispatchQueue}
import com.wire.utils.RichInstant
import org.json.JSONObject
import org.threeten.bp.Instant

import scala.concurrent.Await
import scala.concurrent.duration._

class DefaultLoginClientTest extends FullFeatureSpec {

  scenario("Test reading access token end point") {

    val mockAync = mock[AsyncClient]
    val returnHeaders = DefaultHeaders(Map(
      "content-encoding"                 -> "gzip",
      "request-id"                       -> "7DXR2gKdyyHPcadmGsbJnx",
      "access-control-allow-credentials" -> "true",
      "server"                           -> "nginx",
      "access-control-expose-headers"    -> "Request-Id, Location",
      "date"                             -> "Sat, 28 Jan 2017 14:15:20 GMT",
      "content-length"                   -> "229",
      "connection"                       -> "keep-alive",
      "set-cookie"                       -> "zuid=FrXL_1Up0Zz3WiPO9zGztAZDfJYIgKiVb7Bt0I2CPL0-QsModAL8pAruxewgHb7VA_LQtDZH-u4308ce-H9hAA==.v=1.k=1.d=1491660920.t=u.l=.u=5ab6152b-83eb-45eb-b7ad-83c012efb991.r=942b973e; Path=/access; Expires=Sat, 08-Apr-2017 14:15:20 GMT; Domain=wire.com; HttpOnly; Secure",
      "content-type"                     -> "application/json",
      "strict-transport-security"        -> "max-age=31536000; preload"
    ))

    val returnBody = JsonObjectResponse(new JSONObject(
      """
        |{
        |  "expires_in": 0,
        |  "access_token": "token2394",
        |  "token_type": "coolToken"
        |}
      """.stripMargin
    ))

    (mockAync.apply _)
      .expects(*, *, *, *, *, *, *, *)
      .once()
      .returning(CancellableFuture {
        Response(HttpStatus(Response.Status.Success), returnHeaders, returnBody)
      }(new SerialDispatchQueue(name = "TestAsyncClient")))

    val client = new DefaultLoginClient(mockAync, BackendConfig("https://www.someurl.com"))

    Await.ready(client.access(
      Some("zuid=FrXL_1Up0Zz3WiPO9zGztAZDfJYIgKiVb7Bt0I2CPL0-QsModAL8pAruxewgHb7VA_LQtDZH-u4308ce-H9hAA==.v=1.k=1.d=1491660920.t=u.l=.u=5ab6152b-83eb-45eb-b7ad-83c012efb991.r=942b973e"),
      Some(Token("some token", "my type", Instant.now + 1.second))), 5.seconds)

  }

}
