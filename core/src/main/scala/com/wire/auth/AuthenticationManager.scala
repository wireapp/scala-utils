package com.wire.auth

import com.wire.auth.LoginClient.LoginResult
import com.wire.data.UserId
import com.wire.network.AccessTokenProvider.Token
import com.wire.network.ErrorResponse
import com.wire.network.Response.{Cancelled, ClientClosed, HttpStatus, Status}
import com.wire.threading.{CancellableFuture, SerialDispatchQueue}
import com.wire.utils.RichInstant
import org.threeten.bp.Instant
import com.wire.logging.ZLog._
import com.wire.logging.ZLog.ImplicitTag._

import scala.concurrent.Future
import scala.concurrent.duration._

trait AuthenticationManager {

  def currentToken(): Future[Either[Status, Token]]

  def invalidateToken(): Future[Unit]

  def close(): CancellableFuture[Boolean]

}

class DefaultAuthenticationManager(client: LoginClient, user: CredentialsHandler) extends AuthenticationManager {

  import AuthenticationManager._

  implicit val dispatcher = new SerialDispatchQueue

  private var closed = false

  private val tokenPref = user.accessToken

  /**
    * Last login request result. Used to make sure we never send several concurrent login requests.
    */
  private var loginFuture: CancellableFuture[Either[Status, Token]] = CancellableFuture.lift(tokenPref() map { _.fold[Either[Status, Token]](Left(Cancelled))(Right(_)) })

  def currentToken() = {
    loginFuture = loginFuture.recover {
      case ex =>
        warn(s"login failed", ex)
        Left(Cancelled)
    } flatMap { _ =>
      CancellableFuture.lift(tokenPref()) flatMap {
        case Some(token) if !isExpired(token) =>
          info(s"Non expired token: $token")
          CancellableFuture.successful(Right(token))
        case token =>
          CancellableFuture.lift(user.cookie()) flatMap { cookie =>
            debug(s"Non existent or expired token: $token, will attempt to refresh with cookie: $cookie")
            cookie match {
              case Some(c) =>
                dispatchRequest(client.access(c, token)) {
                  case Left((requestId, resp @ ErrorResponse(Status.Forbidden | Status.Unauthorized, message, label))) =>
                    info(s"access request failed (label: $label, message: $message), will try login request. token: $token, cookie: $cookie, access resp: $resp")
                    for {
                      _ <- CancellableFuture.lift(user.cookie := None)
                      _ <- CancellableFuture.lift(user.accessToken := None)
                      res <- dispatchLoginRequest()
                    } yield res
                }
              case None =>
                dispatchLoginRequest()
            }
          }
      }
    }
    loginFuture.future
  }

  def invalidateToken() = tokenPref().map(_.foreach { token => tokenPref := Some(token.copy(expiresAt = Instant.EPOCH)) })(dispatcher)

  def isExpired(token: Token) = token.expiresAt - bgRefreshThreshold <= Instant.now

  def close() = dispatcher {
    closed = true
    loginFuture.cancel()
  }

  private def dispatchLoginRequest(): CancellableFuture[Either[Status, Token]] =
    if (user.credentials.canLogin) {
      dispatchRequest(client.login(user.userId, user.credentials)) {
        case Left((requestId, resp @ ErrorResponse(Status.Forbidden, _, _))) =>
          info(s"login request failed with: $resp")
          user.onInvalidCredentials()
          CancellableFuture.successful(Left(HttpStatus(Status.Unauthorized, s"login request failed with: $resp")))
      }
    } else { // no cookie, no password/code, therefore unable to login, don't even try
      info("Password or confirmation code missing in dispatchLoginRequest, returning Unauthorized")
      user.onInvalidCredentials()
      CancellableFuture.successful(Left(HttpStatus(Status.Unauthorized, "Password missing in dispatchLoginRequest")))
    }

  private def dispatchRequest(request: => CancellableFuture[LoginResult], retryCount: Int = 0)(handler: ResponseHandler): CancellableFuture[Either[Status, Token]] =
    request flatMap handler.orElse {
      case Right((token, cookie)) =>
        info(s"received access token: '$token' and cookie: $cookie")

        CancellableFuture.lift(for {
          _ <- tokenPref := Some(token)
          _ <- cookie match {
            case Some(c) => user.cookie := Some(c)
            case _ => Future.successful({})
          }
        } yield Right(token))

      case Left(_) if closed =>
        info("AuthManager is closed")
        CancellableFuture.successful(Left(ClientClosed))

      case Left((_, err @ ErrorResponse(Cancelled.status, msg, label))) =>
        info(s"request has been cancelled")
        CancellableFuture.successful(Left(HttpStatus(err.code, s"$msg - $label")))

      case Left(err) if retryCount < MaxRetryCount =>
        info(s"Received error from request: $err, will retry")
        dispatchRequest(request, retryCount + 1)(handler)

      case Left((rId, err)) =>
        val msg = s"Login request failed after $retryCount retries, last status: $err from request: $rId"
        error(msg)
        CancellableFuture.successful(Left(HttpStatus(err.code, msg)))
    }
}


object AuthenticationManager {

  val MaxRetryCount = 3
  val bgRefreshThreshold = 15.seconds // refresh access token on background if it is close to expire

  type ResponseHandler = PartialFunction[LoginResult, CancellableFuture[Either[Status, Token]]]

  case class Cookie(str: String) {

    private val parts = str.split('.').toSet
    val headers = Map(LoginClient.Cookie -> s"zuid=$str")
    val expiry = find("d=").map(v => Instant.ofEpochSecond(v.toLong))
    val userId = find("u=").map(UserId(_))
    def isValid = expiry.exists(_.isAfter(Instant.now))
    def find(pref: String) = parts.find(_.contains(pref)).map(_.drop(2))

    override def toString = s"${str.take(10)}, exp: $expiry, userId: $userId"
  }
}