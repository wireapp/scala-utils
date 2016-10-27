package com.wire.storage

import com.wire.storage.KeyValueStorage.KeyValuePref
import com.wire.storage.Preference.PrefCodec

import scala.concurrent.{ExecutionContext, Future}

trait KeyValueStorage {
  def getPref(key: String): Future[Option[String]]
  def setPref(key: String, value: String): Future[KeyValueData]
  def delPref(key: String): Future[Unit]
  def decodePref[A](key: String, dec: String => A): Future[Option[A]]

  def keyValuePref[A: PrefCodec](key: String, default: A)(implicit executionContext: ExecutionContext) = new KeyValuePref[A](this, key, default)
}

object KeyValueStorage {

  class KeyValuePref[A](storage: KeyValueStorage, key: String, val default: A)(implicit val trans: PrefCodec[A], implicit val dispatcher: ExecutionContext) extends Preference[A] {
    def apply(): Future[A] = storage.decodePref(key, trans.decode).map(_.getOrElse(default))
    def :=(value: A): Future[Unit] = {
      storage.setPref(key, trans.encode(value)) .map { _ => signal ! value }
    }
  }
}

case class KeyValueData(key: String, value: String)