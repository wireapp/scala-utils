package com.wire.accounts

import com.wire.assets.AssetStorage
import com.wire.db.Database
import com.wire.otr.OtrClientStorage
import com.wire.storage.KeyValueStorage
import com.wire.users.UserStorage

trait StorageModule {

  def db: Database

  def keyValues:   KeyValueStorage
  def users:       UserStorage
  def otrClients:  OtrClientStorage
  def assets:      AssetStorage
}

