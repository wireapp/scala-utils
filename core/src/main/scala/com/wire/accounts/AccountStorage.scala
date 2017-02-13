package com.wire.accounts

import com.wire.accounts.AccountData.AccountDataDao
import com.wire.auth.{Credentials, EmailAddress, PhoneNumber}
import com.wire.auth.Credentials.{EmailCredentials, PhoneCredentials}
import com.wire.data.AccountId
import com.wire.db.Database
import com.wire.storage.LRUCacheStorage
import com.wire.logging.ZLog.ImplicitTag._

import scala.concurrent.Future

class AccountStorage(db: Database) extends LRUCacheStorage[AccountId, AccountData](100, AccountDataDao, db) {

  def findByEmail(email: EmailAddress) = find(_.email.contains(email), AccountDataDao.findByEmail(email)(_), identity).map(_.headOption)

  def findByPhone(phone: PhoneNumber) = find(_.phone.contains(phone), AccountDataDao.findByPhone(phone)(_), identity).map(_.headOption)

  def find(credentials: Credentials): Future[Option[AccountData]] = credentials match {
    case EmailCredentials(Some(email), _) => findByEmail(email)
    case PhoneCredentials(Some(phone), _) => findByPhone(phone)
    case _ => Future successful None
  }

}
