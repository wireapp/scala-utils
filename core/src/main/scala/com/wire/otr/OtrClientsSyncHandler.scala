package com.wire.otr

import com.wire.accounts.AccountData.ClientRegistrationState
import com.wire.data.{AccountId, ClientId, UserId}
import com.wire.error.LoggedTry
import com.wire.logging.ZLog.ImplicitTag._
import com.wire.logging.ZLog.{verbose, warn}
import com.wire.network.ErrorResponse
import com.wire.network.Response.Status
import com.wire.otr.Client.Location
import com.wire.reactive.Signal
import com.wire.storage.KeyValueStorage
import com.wire.sync.SyncResult
import com.wire.threading.{SerialDispatchQueue, Serialized, Threading}

import scala.collection.breakOut
import scala.concurrent.Future

trait OtrClientsSyncHandler {
  def syncSelfClients(): Future[SyncResult]
  def registerClient(password: Option[String]): Future[Either[ErrorResponse, (ClientRegistrationState, Option[Client])]]
//  def syncClientsLocation(): Future[SyncResult]
}

class DefaultOtrClientsSyncHandler(accountId:  AccountId,
                                   userId:     UserId,
                                   clientId:   Signal[Option[ClientId]],
                                   netClient:  OtrClient,
                                   otrClients: OtrClientsService,
                                   storage:    OtrClientStorage,
                                   cryptoBox:  CryptoBoxService,
                                   kvStorage:  KeyValueStorage) extends OtrClientsSyncHandler {

  import OtrClientsSyncHandler._

  lazy val sessions = cryptoBox.sessions

  private implicit val dispatcher = new SerialDispatchQueue()

  override def syncSelfClients() = Serialized.future("sync-self-clients", this) { // serialized to avoid races with registration
    verbose(s"syncSelfClients")

    def updatePreKeys(id: ClientId) =
      netClient.loadRemainingPreKeys(id).future flatMap {
        case Right(ids) =>
          verbose(s"remaining prekeys: $ids")
          cryptoBox.generatePreKeysIfNeeded(ids) flatMap {
            case keys if keys.isEmpty => Future.successful(SyncResult.Success)
            case keys => netClient.updateKeys(id, keys).future map {
              case Right(_) => SyncResult.Success
              case Left(error) => SyncResult(error)
            }
          }
        case Left(error) => Future.successful(SyncResult(error))
      }

    netClient.loadClients().future flatMap {
      case Right(clients) =>
        verbose(s"loaded clients: $clients")
        otrClients.updateSelfClients(clients) flatMap { ucs =>
          clientId.head flatMap {
            case Some(id) if ucs.clients.contains(id) => updatePreKeys(id)
            case _ => Future successful SyncResult.Success // FIXME: should we try to register in that case, our device was not registered or deleted from backend
          }
        }
      case Left(error) => Future.successful(SyncResult(error))
    }
  }

  // keeps ZMS_MAJOR_VERSION number of client registration
  // this can be used to detect problematic version updates
  lazy val clientRegVersion = kvStorage.keyValuePref(ClientRegVersionPref, 0)

  override def registerClient(password: Option[String]) = Serialized.future("sync-self-clients", this) {
    import ClientRegistrationState._
    cryptoBox.createClient() flatMap {
      case None => Future successful Left(ErrorResponse.internalError("CryptoBox missing"))
      case Some((c, lastKey, keys)) =>
        netClient.postClient(accountId, c, lastKey, keys, password).future flatMap {
          case Right(cl) =>
            for {
              _ <- clientRegVersion := 1
              _ <- otrClients.updateUserClients(userId, Seq(c.copy(id = cl.id).updated(cl)), replace = false)
            } yield Right((Registered, Some(cl)))
          case Left(error@ErrorResponse(Status.Forbidden, _, "missing-auth")) =>
            warn(s"client registration not allowed: $error, password missing")
            Future successful Right((PasswordMissing, None))
          case Left(error@ErrorResponse(Status.Forbidden, _, "too-many-clients")) =>
            warn(s"client registration not allowed: $error")
            Future successful Right((LimitReached, None))
          case Left(error) =>
            Future.successful(Left(error))
        }
    }
  }

//  override def syncClientsLocation() = {
//    import scala.collection.JavaConverters._
//
//    def loadName(lat: Double, lon: Double) = Future {
//      LoggedTry.local(geocoder.getFromLocation(lat, lon, 1).asScala).toOption.flatMap(_.headOption).flatMap { add =>
//        Option(Seq(Option(add.getLocality), Option(add.getCountryCode)).flatten.mkString(", ")).filter(_.nonEmpty)
//      }
//    } (Threading.BlockingIO)
//
//    def loadNames(locs: Iterable[Location]) =
//      Future.traverse(locs) { l => loadName(l.lat, l.lon).map { (l.lat, l.lon) -> _ } }
//
//    def updateClients(locs: Map[(Double, Double), String])(ucs: UserClients) =
//      ucs.copy(clients = ucs.clients.mapValues { c =>
//        c.regLocation.flatMap { l =>
//          locs.get((l.lat, l.lon)).map(n => l.copy(name = n))
//        }.fold(c) { loc => c.copy(regLocation = Some(loc)) }
//      })
//
//    storage.get(userId) flatMap {
//      case None => Future successful SyncResult.Success
//      case Some(ucs) =>
//        val toSync = ucs.clients.values collect {
//          case Client(_, _, _, _, Some(loc), _, _, _, _) if !loc.hasName => loc
//        }
//        if (toSync.isEmpty) Future successful SyncResult.Success
//        else
//          for {
//            ls <- loadNames(toSync)
//            locations: Map[(Double, Double), String] = ls.collect { case (k, Some(name)) => k -> name }(breakOut)
//            update <- storage.update(userId, updateClients(locations))
//          } yield {
//            update match {
//              case Some((_, UserClients(_, cs))) if cs.values.forall(_.regLocation.forall(_.hasName)) => SyncResult.Success
//              case _ =>
//                verbose(s"user clients were not updated, locations: $locations, toSync: $toSync")
//                SyncResult.failed()
//            }
//          }
//    }
//  }
}

object OtrClientsSyncHandler {
  val ClientRegVersionPref = "otr_client_reg_version"
}