package com.wire.otr

import javax.crypto.spec.SecretKeySpec

import com.wire.cryptography.{AESKey, AESUtils}
import com.wire.data.{JsonDecoder, JsonEncoder}
import org.json.JSONObject

case class MsgAuthCode(str: String) {
  lazy val bytes = AESUtils.base64(str)
}

object MsgAuthCode {
  def apply(bytes: Array[Byte]) = new MsgAuthCode(AESUtils.base64(bytes))
}

/*
 * The MAC uses HMAC-SHA256.
 */
case class SignalingKey(encKey: AESKey, macKey: String) {

  lazy val encKeyBytes = encKey.bytes
  lazy val macKeyBytes = AESUtils.base64(macKey)
  lazy val mac = new SecretKeySpec(macKeyBytes, "HmacSHA256")
}

object SignalingKey {

  def apply(): SignalingKey = new SignalingKey(AESKey(), AESKey().str)

  def apply(enc: Array[Byte], mac: Array[Byte]): SignalingKey = new SignalingKey(AESKey(enc), AESUtils.base64(mac))

  implicit lazy val Decoder: JsonDecoder[SignalingKey] = new JsonDecoder[SignalingKey] {
    import JsonDecoder._
    override def apply(implicit js: JSONObject): SignalingKey = SignalingKey('enckey, 'mackey)
  }

  implicit lazy val Encoder: JsonEncoder[SignalingKey] = new JsonEncoder[SignalingKey] {
    override def apply(v: SignalingKey): JSONObject = JsonEncoder { o =>
      o.put("enckey", v.encKey.str)
      o.put("mackey", v.macKey)
    }
  }
}
