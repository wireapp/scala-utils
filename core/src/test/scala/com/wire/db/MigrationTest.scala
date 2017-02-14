package com.wire.db

import java.io.File

import com.wire.accounts.AccountData
import com.wire.accounts.AccountData.{AccountDataDao, ClientRegistrationState}
import com.wire.accounts.AccountData.AccountDataDao.{Table, delete, iterating}
import com.wire.accounts.AccountData.ClientRegistrationState.Value
import com.wire.auth.AuthenticationManager.Cookie
import com.wire.auth.Credentials.{EmailCredentials, PhoneCredentials}
import com.wire.auth.{Credentials, EmailAddress, Handle, PhoneNumber}
import com.wire.data._
import com.wire.db.OldAccountData.OldAccountDataDao
import com.wire.network.AccessTokenProvider.Token
import com.wire.testutils.FullFeatureSpec

class MigrationTest extends FullFeatureSpec {

  scenario("ZGlobal DB Copied from main project") {



    val db: Database = new SQLiteDatabase(new File("core/src/test/resources/database.db"), Seq(OldAccountDataDao)) {

      override val migrations = Seq(
        Migration(1, 2) { db =>
          db.execSQL("ALTER TABLE Accounts ADD COLUMN handle TEXT DEFAULT ''")
          db.execSQL("ALTER TABLE Accounts ADD COLUMN private_mode BOOL DEFAULT false")
        }
      )

    }

    db.dropAllTables()

    db.onCreate()


    db.onUpgrade(1, 2)

  }

}

case class OldAccountData(id:             AccountId,
                     email:          Option[EmailAddress]          = None,
                     hash:           String                        = "",
                     phone:          Option[PhoneNumber]           = None,
                     activated:      Boolean                       = false,
                     cookie:         Cookie                        = None,
                     password:       Option[String]                = None,
                     accessToken:    Option[Token]                 = None,
                     userId:         Option[UserId]                = None,
                     clientId:       Option[ClientId]              = None,
                     clientRegState: ClientRegistrationState.Value = ClientRegistrationState.Unknown) {

  override def toString: String =
    s"""
       |AccountData:
       | id:             $id
       | email:          $email
       | hash:           $hash
       | phone:          $phone
       | activated:      $activated
       | cookie:         $cookie
       | password:       $password
       | accessToken:    $accessToken
       | userId:         $userId
       | clientId:       $clientId
       | clientRegState: $clientRegState
    """.stripMargin

  def authorized(credentials: Credentials) = credentials match {
    case EmailCredentials(Some(e), Some(passwd)) if email.contains(e) && AccountData.computeHash(id, passwd) == hash =>
      Some(copy(password = Some(passwd)))
    case _ =>
      None
  }

  def updated(credentials: Credentials) = credentials match {
    case EmailCredentials(Some(e), Some(passwd)) =>
      copy(email = Some(e), hash = AccountData.computeHash(id, passwd), password = Some(passwd))
    case EmailCredentials(Some(e), None) =>
      copy(email = Some(e))
    case PhoneCredentials(Some(number), _) =>
      copy(phone = Some(number))
    case _ => this
  }

  def credentials: Credentials = (email, phone, password) match {
    case (None, Some(p), _)   => PhoneCredentials(Some(p), None)
    case (Some(e), _, passwd) => EmailCredentials(Some(e), passwd)
    case _ => Credentials.Empty
  }

  //  def updated(user: UserInfo) =
  //    copy(userId = Some(user.id), email = user.email.orElse(email), phone = user.phone.orElse(phone), activated = true, handle = user.handle.orElse(handle), privateMode = user.privateMode.getOrElse(privateMode))

  //  def updated(userId: Option[UserId], activated: Boolean, clientId: Option[ClientId], clientRegState: ClientRegistrationState) =
  //    copy(userId = userId orElse this.userId, activated = this.activated | activated, clientId = clientId orElse this.clientId, clientRegState = clientRegState)
}



object OldAccountData {

  def apply(id: AccountId, email: String, hash: String): AccountData = AccountData(id, email = Some(EmailAddress(email)), hash, phone = None, handle = None)  // used only for testing

  def apply(id: AccountId, credentials: Credentials): AccountData =
    new AccountData(id, credentials.email, "", phone = credentials.phone, password = credentials.password, handle = credentials.handle)

  def apply(email: EmailAddress, password: String): AccountData = {
    val id = AccountId()
    AccountData(id, Some(email), computeHash(id, password), password = Some(password), phone = None, handle = None)
  }

  def computeHash(id: AccountId, password: String) = password

  implicit object OldAccountDataDao extends Dao[OldAccountData, AccountId] {
    import com.wire.db.Col._

    val Id             = id[AccountId]('_id, "PRIMARY KEY").apply(_.id)
    val Email          = opt(emailAddress('email))(_.email)
    val Hash           = text('hash)(_.hash)
    val Activated      = bool('verified)(_.activated)
    val Cookie         = opt(text('cookie))(_.cookie)
    val Phone          = opt(phoneNumber('phone))(_.phone)
    val Token          = opt(text[Token]('access_token, JsonEncoder.encodeString[Token], JsonDecoder.decode[Token]))(_.accessToken)
    val UserId         = opt(id[UserId]('user_id)).apply(_.userId)
    val ClientId       = opt(id[ClientId]('client_id))(_.clientId)
    val ClientRegState = text[ClientRegistrationState]('reg_state, _.toString, ClientRegistrationState.withName)(_.clientRegState)

    override val idCol = Id

    override val table = Table("Accounts", Id, Email, Hash, Activated, Cookie, Phone, Token, UserId, ClientId, ClientRegState)

    override def apply(implicit cursor: Cursor): OldAccountData = OldAccountData(Id, Email, Hash, Phone, Activated, Cookie, None, Token, UserId, ClientId, ClientRegState)

    def findByEmail(email: EmailAddress)(implicit db: Database) =
      iterating(db.query(table.name, selection = s"${Email.name} = ?", selectionArgs = Seq(email.str)))

    def findByUser(user: UserId)(implicit db: Database) =
      iterating(db.query(table.name, selection = s"${UserId.name} = ?", selectionArgs = Array(user.str)))

    def findByPhone(phone: PhoneNumber)(implicit db: Database) =
      iterating(db.query(table.name, null, s"${Phone.name} = ?", Array(phone.str), null, null, null))

    def deleteForEmail(email: EmailAddress)(implicit db: Database) = delete(Email, Some(email))
  }

}

