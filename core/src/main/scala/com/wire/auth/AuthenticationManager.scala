package com.wire.auth

import com.wire.auth.LoginClient.LoginResult
import com.wire.logging.Logging._
import com.wire.network.AccessTokenProvider.Token
import com.wire.network.ErrorResponse
import com.wire.network.Response.{Cancelled, ClientClosed, HttpStatus, Status}
import com.wire.threading.{CancellableFuture, SerialDispatchQueue}
import com.wire.utils.RichInstant
import org.threeten.bp.Instant

import scala.concurrent.Future
import scala.concurrent.duration._

trait AuthenticationManager {

  def currentToken(): Future[Either[Status, Token]]

  def invalidateToken(): Future[Unit]

  def close(): CancellableFuture[Boolean]

}

class DefaultAuthenticationManager(client: LoginClient, user: CredentialsHandler) extends AuthenticationManager {

  import AuthenticationManager._

  implicit val dispatcher = new SerialDispatchQueue(name = "AuthenticationManager")

  private var closed = false

  private val tokenPref = user.accessToken

  /**
    * Last login request result. Used to make sure we never send several concurrent login requests.
    */
  private var loginFuture: CancellableFuture[Either[Status, Token]] = CancellableFuture.lift(tokenPref() map { _.fold[Either[Status, Token]](Left(Cancelled))(Right(_)) })

  def currentToken() = tokenPref() flatMap {
    case Some(token) if !isExpired(token) =>
      if (shouldRefresh(token)) performLogin() // schedule login on background and don't care about the result, it's supposed to refresh the access token
      Future.successful(Right(token))
    case _ => performLogin()
  }

  def invalidateToken() = tokenPref() .map (_.foreach { token => tokenPref := Some(token.copy(expiresAt = Instant.EPOCH)) })(dispatcher)

  def isExpired(token: Token) = token.expiresAt <= Instant.now

  def close() = dispatcher {
    closed = true
    loginFuture.cancel()
  }

  private def shouldRefresh(token: Token) = token.expiresAt - bgRefreshThreshold <= Instant.now

  /**
    * Performs login request once the last request is finished, but only if we still need it (ie. we don't have access token already)
    */
  private def performLogin(): Future[Either[Status, Token]] = {
    info(s"performLogin, credentials: ${user.credentials}")

    loginFuture = loginFuture.recover {
      case ex =>
        warn(s"login failed", ex)
        Left(Cancelled)
    } flatMap { _ =>
      CancellableFuture.lift(tokenPref()) flatMap {
        case Some(token: Token) if !isExpired(token) =>
          if (shouldRefresh(token)) dispatchAccessRequest()
          CancellableFuture.successful(Right(token))
        case _ =>
          info(s"No access token, or expired, cookie: ${user.cookie}")
          CancellableFuture.lift(user.cookie()) flatMap {
            case Some(_) => dispatchAccessRequest()
            case None => dispatchLoginRequest()
          }
      }
                  }
    loginFuture.future
  }

  private def dispatchAccessRequest(): CancellableFuture[Either[Status, Token]] =
    for {
      token <- CancellableFuture lift user.accessToken()
      cookie <- CancellableFuture lift user.cookie()
      res <-
      dispatchRequest(client.access(cookie, token)) {
        case Left(resp @ ErrorResponse(Status.Forbidden | Status.Unauthorized, message, label)) =>
          info(s"access request failed (label: $label, message: $message), will try login request. token: $token, cookie: $cookie, access resp: $resp")
          user.cookie := None
          user.accessToken := None
          dispatchLoginRequest()
      }
    } yield res

  private def dispatchLoginRequest(): CancellableFuture[Either[Status, Token]] =
    if (user.credentials.canLogin) {
      dispatchRequest(client.login(user.userId, user.credentials)) {
        case Left(resp @ ErrorResponse(Status.Forbidden, _, _)) =>
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
        info(s"receivedAccessToken: '$token'")
        tokenPref := Some(token)
        cookie.foreach(c => user.cookie := Some(c))
        CancellableFuture.successful(Right(token))

      case Left(_) if closed => CancellableFuture.successful(Left(ClientClosed))

      case Left(err @ ErrorResponse(Cancelled.status, msg, label)) =>
        info(s"request has been cancelled")
        CancellableFuture.successful(Left(HttpStatus(err.code, s"$msg - $label")))

      case Left(err) if retryCount < MaxRetryCount =>
        info(s"Received error from request: $err, will retry")
        dispatchRequest(request, retryCount + 1)(handler)

      case Left(err) =>
        val msg = s"Login request failed after $retryCount retries, last status: $err"
        error(msg)
        CancellableFuture.successful(Left(HttpStatus(err.code, msg)))
    }
}


object AuthenticationManager {

  val MaxRetryCount = 3
  val bgRefreshThreshold = 15.seconds // refresh access token on background if it is close to expire

  type ResponseHandler = PartialFunction[LoginResult, CancellableFuture[Either[Status, Token]]]

  type Cookie = Option[String]
}