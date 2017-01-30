package com.wire.auth

import java.util.Locale.US
import java.util.UUID
import java.util.regex.Pattern.compile

import com.wire.data.{JsonDecoder, JsonEncoder}
import com.wire.utils.Locales
import com.wire.utils.Locales.currentLocaleOrdering

case class EmailAddress(str: String) extends AnyVal {
  def normalized: Option[EmailAddress] = EmailAddress.parse(str)
  override def toString: String = str
}

object EmailAddress extends (String => EmailAddress) {
  implicit def IsOrdered: Ordering[EmailAddress] = currentLocaleOrdering.on(_.str)

  implicit val Encoder: JsonEncoder[EmailAddress] = JsonEncoder.build(p => js => js.put("email", p.str))
  implicit val Decoder: JsonDecoder[EmailAddress] = JsonDecoder.lift(implicit js => EmailAddress(JsonDecoder.decodeString('email)))

  val pattern = compile(address)

  def parse(input: String): Option[EmailAddress] = {
    val matcher = pattern matcher input.trim
    if (matcher.find()) Option(matcher group 2).orElse(Option(matcher group 1)) map (str => EmailAddress(str.trim.toLowerCase(US)))
    else None
  }

  /** Implements the parts of the RFC 2822 email address grammar that seem practically relevant (as of now ;) ).
    * This excludes address groups, comments, CRLF, domain literals, quoted pairs, quoted local parts and white space
    * around dot atoms. The display name part is relaxed to allow unicode characters. Also, only domain names following
    * the "preferred name syntax" (RFC 1035, 2.3.1) and consisting of at least 2 labels are considered valid.
    *
    * Here be dragons (i.e. watch out for catastrophic exponential backtracking) !
    */

  private def address = s"^(?:$mailbox)$$" // ignores "group"
  private def mailbox = s"(?:$addrSpec)|(?:$nameAddr)"
  private def nameAddr = s"(?:$displayName)?(?:$angleAddr)"
  private def angleAddr = s"$wsp*<$addrSpec>$wsp*"
  private def wsp = "[ \t]" // see 2.2.2
  private def addrSpec = s"((?:$localPart)@(?:$domain))"
  private def localPart = s"""(?:$atext)(?:\\.(?:$atext))*""" // dot-atom-text
  private def atext = "[a-zA-Z0-9!#$%&'*+\\-/=?^_`\\{|\\}~]+"
  private def displayName = s"(?:$word)+"
  private def word = s"(?:$atom)|$wsp|(?:$quotedString)"
  private def atom = "[\\p{L}\\p{M}\\p{N}\\p{P}\\p{S}&&[^()<>\\[\\]:;@\\\\,.\"]]"
  private def quotedString = "\"[^\"]*\""
  private def domain = s"""(?:$label)(?:\\.(?:$label))+"""
  private def label = s"$letter(?:$ldhStr$letDig)?"
  private def ldhStr = "[a-zA-Z0-9-]*"
  private def letDig = "[a-zA-Z0-9]"
  private def letter = "[a-zA-Z]"
}

case class PhoneNumber(str: String) extends AnyVal {
  override def toString: String = str
}
object PhoneNumber extends (String => PhoneNumber) {
  implicit def IsOrdered: Ordering[PhoneNumber] = currentLocaleOrdering.on(_.str)
  implicit val Encoder: JsonEncoder[PhoneNumber] = JsonEncoder.build(p => js => js.put("phone", p.str))
  implicit val Decoder: JsonDecoder[PhoneNumber] = JsonDecoder.lift(implicit js => PhoneNumber(JsonDecoder.decodeString('phone)))
}

case class Handle(string: String) extends AnyVal{
  override def toString : String = string
  def containsQuery(query: String): Boolean = {
    string.contains(Handle.transliterated(Handle.stripSymbol(query)).toLowerCase)
  }
}

object Handle extends (String => Handle){
  def apply(): Handle = Handle("")
  def random: Handle = Handle(UUID.randomUUID().toString)
  val handlePattern = """@(.+)""".r
  def transliterated(s: String): String = s//Locales.transliteration.transliterate(s).trim
  def containsSymbol(input: String): Boolean = input match {
    case Handle.handlePattern(handle) => true
    case _ => false
  }
  def stripSymbol(input: String): String = input match {
    case Handle.handlePattern(handle) => handle
    case _ => input
  }
}