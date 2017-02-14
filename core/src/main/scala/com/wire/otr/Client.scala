package com.wire.otr

import com.wire.data.{ClientId, JsonDecoder, JsonEncoder, UserId}
import com.wire.db.{Cursor, Dao, Database}
import com.wire.otr.Client.{Location, OtrClientType, Verification}
import org.json.JSONObject
import org.threeten.bp.Instant

import scala.collection.breakOut

/**
  * Otr client registered on backend, either our own or from other user.
  *
  * @param id
  * @param label
  * @param signalingKey - will only be set for current device
  * @param verified - client verification state, updated when user verifies client fingerprint
  */
case class Client(id:           ClientId,
                  label:        String,
                  model:        String = "",
                  regTime:      Option[Instant] = None,
                  regLocation:  Option[Location] = None,
                  regIpAddress: Option[String] = None,
                  signalingKey: Option[SignalingKey] = None,
                  verified:     Verification = Verification.Unknown,
                  devType:      OtrClientType = OtrClientType.phone) {

  def isVerified = verified == Verification.Verified

  def updated(c: Client) = {
    val location = (regLocation, c.regLocation) match {
      case (Some(loc), Some(l)) if loc.lat == l.lat && loc.lon == l.lon => Some(loc)
      case (_, loc @ Some(_)) => loc
      case (loc, _) => loc
    }
    copy (
      label        = if (c.label.isEmpty) label else c.label,
      model        = if (c.model.isEmpty) model else c.model,
      regTime      = c.regTime.orElse(regTime),
      regLocation  = location,
      regIpAddress = c.regIpAddress.orElse(regIpAddress),
      signalingKey = c.signalingKey.orElse(signalingKey),
      verified     = if (c.verified == Verification.Unknown) verified else c.verified,
      devType      = if (c.devType == OtrClientType.phone) devType else c.devType
    )
  }
}

object Client {

  implicit lazy val Encoder: JsonEncoder[Client] = new JsonEncoder[Client] {
    override def apply(v: Client): JSONObject = JsonEncoder { o =>
      o.put("id", v.id.str)
      o.put("label", v.label)
      o.put("model", v.model)
      v.regTime foreach { t => o.put("regTime", t.toEpochMilli) }
      v.regLocation foreach { l => o.put("regLocation", JsonEncoder.encode(l)) }
      v.regIpAddress foreach { o.put("regIpAddress", _) }
      v.signalingKey foreach { sk => o.put("signalingKey", JsonEncoder.encode(sk)) }
      o.put("verification", v.verified)
      o.put("devType", v.devType)
    }
  }

  implicit lazy val Decoder: JsonDecoder[Client] = new JsonDecoder[Client] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): Client = {
      new Client(decodeId[ClientId]('id), 'label, 'model, 'regTime, opt[Location]('regLocation), 'regIpAddress, opt[SignalingKey]('signalingKey),
        decodeOptString('verification).fold(Verification.Unknown)(Verification.withName),
        decodeOptString('devType).fold(OtrClientType.phone)(OtrClientType.withName)
      )
    }
  }

  case class Location(lon: Double, lat: Double, name: String) {

    def hasName = name != ""
    def getName = if (hasName) name else s"$lat, $lon"
  }

  object Location {
    val Empty = Location(0, 0, "")

    implicit lazy val Encoder: JsonEncoder[Location] = new JsonEncoder[Location] {
      override def apply(v: Location): JSONObject = JsonEncoder { o =>
        o.put("lon", v.lon)
        o.put("lat", v.lat)
        o.put("name", v.name)
      }
    }

    implicit lazy val Decoder: JsonDecoder[Location] = new JsonDecoder[Location] {
      import JsonDecoder._
      override def apply(implicit js: JSONObject): Location = new Location('lon, 'lat, 'name)
    }
  }

  object Verification extends Enumeration {
    val Verified, Unverified, Unknown = Value
  }
  type Verification = Verification.Value

  object OtrClientType extends Enumeration {
    val phone, tablet, desktop = Value
  }
  type OtrClientType = OtrClientType.Value
}

case class UserClients(user: UserId, clients: Map[ClientId, Client]) {
  def -(clientId: ClientId) = UserClients(user, clients - clientId)
}

object UserClients {

  implicit lazy val Encoder: JsonEncoder[UserClients] = new JsonEncoder[UserClients] {
    override def apply(v: UserClients): JSONObject = JsonEncoder { o =>
      o.put("user", v.user.str)
      o.put("clients", JsonEncoder.arr(v.clients.values.toSeq))
    }
  }

  implicit lazy val Decoder: JsonDecoder[UserClients] = new JsonDecoder[UserClients] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): UserClients = new UserClients(decodeId[UserId]('user), decodeSeq[Client]('clients).map(c => c.id -> c)(breakOut))
  }


  implicit object UserClientsDao extends Dao[UserClients, UserId] {
    import com.wire.db.Col._
    val Id = id[UserId]('_id, "PRIMARY KEY").apply(_.user)
    val Data = text('data)(JsonEncoder.encodeString(_))

    override val idCol = Id
    override val table = Table("Clients", Id, Data)

    override def apply(implicit cursor: Cursor): UserClients = JsonDecoder.decode(Data)(Decoder)

    def find(ids: Traversable[UserId])(implicit db: Database): Vector[UserClients] =
      if (ids.isEmpty) Vector.empty
      else list(db.query(table.name, null, s"${Id.name} in (${ids.map(_.str).mkString("'", "','", "'")})", null, null, null, null))
  }
}
