/*
 * Wire
 * Copyright (C) 2016 Wire Swiss GmbH

 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
  package com.wire.data

import scala.PartialFunction._

case class Mime(str: String) {

  def extension = Mime.mimeTypeToExtensionMap.getOrElse(str, str.drop(str.indexOf('/') + 1))

  def isEmpty = str.isEmpty

  def orElse(default: => Mime) = if (isEmpty) default else this
  def orDefault = if (isEmpty) Mime.Default else this
}

object Mime {
  val Unknown = Mime("")
  val Default = Mime("application/octet-stream")

//  def fromFileName(fileName: String) = extensionOf(fileName).fold2(Unknown, fromExtension)
//  def fromExtension(ext: String) = Option(MimeTypeMap.getSingleton.getMimeTypeFromExtension(ext)).fold2(Unknown, Mime(_))
  def extensionOf(fileName: String): Option[String] = fileName.lastIndexOf(".") match {
    case -1 | 0 => None
    case n  => Some(fileName.substring(n + 1))
  }

  object Video {
    val MP4 = Mime("video/mp4")
    val `3GPP` = Mime("video/3gpp")

    def unapply(mime: Mime): Boolean = cond(mime) {
      case MP4 => true
      case `3GPP` => true
    }
  }

  object Image {
    val PNG = Mime("image/png")

    def unapply(mime: Mime): Boolean = mime.str.startsWith("image/")
  }

  object Audio {
    val MP3 = Mime("audio/mp3")
    val MP4 = Mime("audio/mp4")
    val AAC = Mime("audio/aac")
    val `3GPP` = Mime("audio/3gpp")
    val AMR_NB = Mime("audio/amr-nb")
    val AMR_WB = Mime("audio/amr-wb")
    val Ogg = Mime("audio/ogg")
    val FLAC = Mime("audio/flac")
    val WAV = Mime("audio/wav")
    val PCM = Mime("audio/pcm-s16le;rate=44100;channels=1")

    def unapply(mime: Mime): Boolean = supported(mime)

    val supported = Set(MP3, Mime("audio/mpeg3"), Mime("audio/mpeg"), MP4, Mime("audio/x-m4a"), AAC, `3GPP`, AMR_NB, AMR_WB, Ogg, FLAC, WAV)
  }

  //TODO Dean: Not perfect, but should give us something
  //Also don't forget to attribute
  private val mimeTypeToExtensionMap: Map[String, String] = Map (
    "application/andrew-inset" -> "ez",
    "application/dsptype" -> "tsp",
    "application/hta" -> "hta",
    "application/mac-binhex40" -> "hqx",
    "application/mathematica" -> "nb",
    "application/msaccess" -> "mdb",
    "application/oda" -> "oda",
    "application/ogg" -> "ogg",
    "application/ogg" -> "oga",
    "application/pdf" -> "pdf",
    "application/pgp-keys" -> "key",
    "application/pgp-signature" -> "pgp",
    "application/pics-rules" -> "prf",
    "application/pkix-cert" -> "cer",
    "application/rar" -> "rar",
    "application/rdf+xml" -> "rdf",
    "application/rss+xml" -> "rss",
    "application/zip" -> "zip",
    "application/vnd.android.package-archive" -> "apk",
    "application/vnd.cinderella" -> "cdy",
    "application/vnd.ms-pki.stl" -> "stl",
    "application/vnd.oasis.opendocument.database" -> "odb",
    "application/vnd.oasis.opendocument.formula" -> "odf",
    "application/vnd.oasis.opendocument.graphics" -> "odg",
    "application/vnd.oasis.opendocument.graphics-template" -> "otg",
    "application/vnd.oasis.opendocument.image" -> "odi",
    "application/vnd.oasis.opendocument.spreadsheet" -> "ods",
    "application/vnd.oasis.opendocument.spreadsheet-template" -> "ots",
    "application/vnd.oasis.opendocument.text" -> "odt",
    "application/vnd.oasis.opendocument.text-master" -> "odm",
    "application/vnd.oasis.opendocument.text-template" -> "ott",
    "application/vnd.oasis.opendocument.text-web" -> "oth",
    "application/vnd.google-earth.kml+xml" -> "kml",
    "application/vnd.google-earth.kmz" -> "kmz",
    "application/msword" -> "doc",
    "application/msword" -> "dot",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx",
    "application/vnd.openxmlformats-officedocument.wordprocessingml.template" -> "dotx",
    "application/vnd.ms-excel" -> "xls",
    "application/vnd.ms-excel" -> "xlt",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.template" -> "xltx",
    "application/vnd.ms-powerpoint" -> "ppt",
    "application/vnd.ms-powerpoint" -> "pot",
    "application/vnd.ms-powerpoint" -> "pps",
    "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx",
    "application/vnd.openxmlformats-officedocument.presentationml.template" -> "potx",
    "application/vnd.openxmlformats-officedocument.presentationml.slideshow" -> "ppsx",
    "application/vnd.rim.cod" -> "cod",
    "application/vnd.smaf" -> "mmf",
    "application/vnd.stardivision.calc" -> "sdc",
    "application/vnd.stardivision.draw" -> "sda",
    "application/vnd.stardivision.impress" -> "sdd",
    "application/vnd.stardivision.impress" -> "sdp",
    "application/vnd.stardivision.math" -> "smf",
    "application/vnd.stardivision.writer" -> "sdw",
    "application/vnd.stardivision.writer" -> "vor",
    "application/vnd.stardivision.writer-global" -> "sgl",
    "application/vnd.sun.xml.calc" -> "sxc",
    "application/vnd.sun.xml.calc.template" -> "stc",
    "application/vnd.sun.xml.draw" -> "sxd",
    "application/vnd.sun.xml.draw.template" -> "std",
    "application/vnd.sun.xml.impress" -> "sxi",
    "application/vnd.sun.xml.impress.template" -> "sti",
    "application/vnd.sun.xml.math" -> "sxm",
    "application/vnd.sun.xml.writer" -> "sxw",
    "application/vnd.sun.xml.writer.global" -> "sxg",
    "application/vnd.sun.xml.writer.template" -> "stw",
    "application/vnd.visio" -> "vsd",
    "application/x-abiword" -> "abw",
    "application/x-apple-diskimage" -> "dmg",
    "application/x-bcpio" -> "bcpio",
    "application/x-bittorrent" -> "torrent",
    "application/x-cdf" -> "cdf",
    "application/x-cdlink" -> "vcd",
    "application/x-chess-pgn" -> "pgn",
    "application/x-cpio" -> "cpio",
    "application/x-debian-package" -> "deb",
    "application/x-debian-package" -> "udeb",
    "application/x-director" -> "dcr",
    "application/x-director" -> "dir",
    "application/x-director" -> "dxr",
    "application/x-dms" -> "dms",
    "application/x-doom" -> "wad",
    "application/x-dvi" -> "dvi",
    "application/x-font" -> "pfa",
    "application/x-font" -> "pfb",
    "application/x-font" -> "gsf",
    "application/x-font" -> "pcf",
    "application/x-font" -> "pcf.Z",
    "application/x-freemind" -> "mm",
    // application/futuresplash isn't IANA, so application/x-futuresplash should come first.
    "application/x-futuresplash" -> "spl",
    "application/futuresplash" -> "spl",
    "application/x-gnumeric" -> "gnumeric",
    "application/x-go-sgf" -> "sgf",
    "application/x-graphing-calculator" -> "gcf",
    "application/x-gtar" -> "tgz",
    "application/x-gtar" -> "gtar",
    "application/x-gtar" -> "taz",
    "application/x-hdf" -> "hdf",
    "application/x-ica" -> "ica",
    "application/x-internet-signup" -> "ins",
    "application/x-internet-signup" -> "isp",
    "application/x-iphone" -> "iii",
    "application/x-iso9660-image" -> "iso",
    "application/x-jmol" -> "jmz",
    "application/x-kchart" -> "chrt",
    "application/x-killustrator" -> "kil",
    "application/x-koan" -> "skp",
    "application/x-koan" -> "skd",
    "application/x-koan" -> "skt",
    "application/x-koan" -> "skm",
    "application/x-kpresenter" -> "kpr",
    "application/x-kpresenter" -> "kpt",
    "application/x-kspread" -> "ksp",
    "application/x-kword" -> "kwd",
    "application/x-kword" -> "kwt",
    "application/x-latex" -> "latex",
    "application/x-lha" -> "lha",
    "application/x-lzh" -> "lzh",
    "application/x-lzx" -> "lzx",
    "application/x-maker" -> "frm",
    "application/x-maker" -> "maker",
    "application/x-maker" -> "frame",
    "application/x-maker" -> "fb",
    "application/x-maker" -> "book",
    "application/x-maker" -> "fbdoc",
    "application/x-mif" -> "mif",
    "application/x-ms-wmd" -> "wmd",
    "application/x-ms-wmz" -> "wmz",
    "application/x-msi" -> "msi",
    "application/x-ns-proxy-autoconfig" -> "pac",
    "application/x-nwc" -> "nwc",
    "application/x-object" -> "o",
    "application/x-oz-application" -> "oza",
    "application/x-pem-file" -> "pem",
    "application/x-pkcs12" -> "p12",
    "application/x-pkcs12" -> "pfx",
    "application/x-pkcs7-certreqresp" -> "p7r",
    "application/x-pkcs7-crl" -> "crl",
    "application/x-quicktimeplayer" -> "qtl",
    "application/x-shar" -> "shar",
    "application/x-shockwave-flash" -> "swf",
    "application/x-stuffit" -> "sit",
    "application/x-sv4cpio" -> "sv4cpio",
    "application/x-sv4crc" -> "sv4crc",
    "application/x-tar" -> "tar",
    "application/x-texinfo" -> "texinfo",
    "application/x-texinfo" -> "texi",
    "application/x-troff" -> "t",
    "application/x-troff" -> "roff",
    "application/x-troff-man" -> "man",
    "application/x-ustar" -> "ustar",
    "application/x-wais-source" -> "src",
    "application/x-wingz" -> "wz",
    "application/x-webarchive" -> "webarchive",
    "application/x-webarchive-xml" -> "webarchivexml",
    "application/x-x509-ca-cert" -> "crt",
    "application/x-x509-user-cert" -> "crt",
    "application/x-x509-server-cert" -> "crt",
    "application/x-xcf" -> "xcf",
    "application/x-xfig" -> "fig",
    "application/xhtml+xml" -> "xhtml",
    "audio/3gpp" -> "3gpp",
    "audio/aac" -> "aac",
    "audio/aac-adts" -> "aac",
    "audio/amr" -> "amr",
    "audio/amr-wb" -> "awb",
    "audio/basic" -> "snd",
    "audio/flac" -> "flac",
    "application/x-flac" -> "flac",
    "audio/imelody" -> "imy",
    "audio/midi" -> "mid",
    "audio/midi" -> "midi",
    "audio/midi" -> "ota",
    "audio/midi" -> "kar",
    "audio/midi" -> "rtttl",
    "audio/midi" -> "xmf",
    "audio/mobile-xmf" -> "mxmf",
    // add ".mp3" first so it will be the default for guessExtensionFromMimeType
    "audio/mpeg" -> "mp3",
//    "audio/mpeg" -> "mpga",
//    "audio/mpeg" -> "mpega",
//    "audio/mpeg" -> "mp2",
//    "audio/mpeg" -> "m4a",
    "audio/mpegurl" -> "m3u",
    "audio/prs.sid" -> "sid",
    "audio/x-aiff" -> "aif",
    "audio/x-aiff" -> "aiff",
    "audio/x-aiff" -> "aifc",
    "audio/x-gsm" -> "gsm",
    "audio/x-matroska" -> "mka",
    "audio/x-mpegurl" -> "m3u",
    "audio/x-ms-wma" -> "wma",
    "audio/x-ms-wax" -> "wax",
    "audio/x-pn-realaudio" -> "ra",
    "audio/x-pn-realaudio" -> "rm",
    "audio/x-pn-realaudio" -> "ram",
    "audio/x-realaudio" -> "ra",
    "audio/x-scpls" -> "pls",
    "audio/x-sd2" -> "sd2",
    "audio/x-wav" -> "wav",
    // image/bmp isn't IANA, so image/x-ms-bmp should come first.
    "image/x-ms-bmp" -> "bmp",
    "image/bmp" -> "bmp",
    "image/gif" -> "gif",
    // image/ico isn't IANA, so image/x-icon should come first.
    "image/x-icon" -> "ico",
    "image/ico" -> "cur",
    "image/ico" -> "ico",
    "image/ief" -> "ief",
    "image/jpeg" -> "jpeg",
//    "image/jpeg" -> "jpg",
//    "image/jpeg" -> "jpe",
    "image/pcx" -> "pcx",
    "image/png" -> "png",
    "image/svg+xml" -> "svg",
    "image/svg+xml" -> "svgz",
    "image/tiff" -> "tiff",
    "image/tiff" -> "tif",
    "image/vnd.djvu" -> "djvu",
    "image/vnd.djvu" -> "djv",
    "image/vnd.wap.wbmp" -> "wbmp",
    "image/webp" -> "webp",
    "image/x-cmu-raster" -> "ras",
    "image/x-coreldraw" -> "cdr",
    "image/x-coreldrawpattern" -> "pat",
    "image/x-coreldrawtemplate" -> "cdt",
    "image/x-corelphotopaint" -> "cpt",
    "image/x-jg" -> "art",
    "image/x-jng" -> "jng",
    "image/x-photoshop" -> "psd",
    "image/x-portable-anymap" -> "pnm",
    "image/x-portable-bitmap" -> "pbm",
    "image/x-portable-graymap" -> "pgm",
    "image/x-portable-pixmap" -> "ppm",
    "image/x-rgb" -> "rgb",
    "image/x-xbitmap" -> "xbm",
    "image/x-xpixmap" -> "xpm",
    "image/x-xwindowdump" -> "xwd",
    "model/iges" -> "igs",
    "model/iges" -> "iges",
    "model/mesh" -> "msh",
    "model/mesh" -> "mesh",
    "model/mesh" -> "silo",
    "text/calendar" -> "ics",
    "text/calendar" -> "icz",
    "text/comma-separated-values" -> "csv",
    "text/css" -> "css",
    "text/html" -> "htm",
    "text/html" -> "html",
    "text/h323" -> "323",
    "text/iuls" -> "uls",
    "text/mathml" -> "mml",
    // add ".txt" first so it will be the default for guessExtensionFromMimeType
    "text/plain" -> "txt",
//    "text/plain" -> "asc",
//    "text/plain" -> "text",
//    "text/plain" -> "diff",
    "text/plain" -> "po", // reserve "pot", for vnd.ms-powerpoint
    "text/richtext" -> "rtx",
    "text/rtf" -> "rtf",
    "text/text" -> "phps",
    "text/tab-separated-values" -> "tsv",
    "text/xml" -> "xml",
    "text/x-bibtex" -> "bib",
    "text/x-boo" -> "boo",
    "text/x-c++hdr" -> "hpp",
    "text/x-c++hdr" -> "h++",
    "text/x-c++hdr" -> "hxx",
    "text/x-c++hdr" -> "hh",
    "text/x-c++src" -> "cpp",
    "text/x-c++src" -> "c++",
    "text/x-c++src" -> "cc",
    "text/x-c++src" -> "cxx",
    "text/x-chdr" -> "h",
    "text/x-component" -> "htc",
    "text/x-csh" -> "csh",
    "text/x-csrc" -> "c",
    "text/x-dsrc" -> "d",
    "text/x-haskell" -> "hs",
    "text/x-java" -> "java",
    "text/x-literate-haskell" -> "lhs",
    "text/x-moc" -> "moc",
    "text/x-pascal" -> "p",
    "text/x-pascal" -> "pas",
    "text/x-pcs-gcd" -> "gcd",
    "text/x-setext" -> "etx",
    "text/x-tcl" -> "tcl",
    "text/x-tex" -> "tex",
    "text/x-tex" -> "ltx",
    "text/x-tex" -> "sty",
    "text/x-tex" -> "cls",
    "text/x-vcalendar" -> "vcs",
    "text/x-vcard" -> "vcf",
    "video/3gpp" -> "3gpp",
    "video/3gpp" -> "3gp",
    "video/3gpp2" -> "3gpp2",
    "video/3gpp2" -> "3g2",
    "video/avi" -> "avi",
    "video/dl" -> "dl",
    "video/dv" -> "dif",
    "video/dv" -> "dv",
    "video/fli" -> "fli",
    "video/m4v" -> "m4v",
    "video/mp2ts" -> "ts",
    "video/mpeg" -> "mpeg",
    "video/mpeg" -> "mpg",
    "video/mpeg" -> "mpe",
    "video/mp4" -> "mp4",
    "video/mpeg" -> "VOB",
    "video/quicktime" -> "qt",
    "video/quicktime" -> "mov",
    "video/vnd.mpegurl" -> "mxu",
    "video/webm" -> "webm",
    "video/x-la-asf" -> "lsf",
    "video/x-la-asf" -> "lsx",
    "video/x-matroska" -> "mkv",
    "video/x-mng" -> "mng",
    "video/x-ms-asf" -> "asf",
    "video/x-ms-asf" -> "asx",
    "video/x-ms-wm" -> "wm",
    "video/x-ms-wmv" -> "wmv",
    "video/x-ms-wmx" -> "wmx",
    "video/x-ms-wvx" -> "wvx",
    "video/x-sgi-movie" -> "movie",
    "video/x-webex" -> "wrf",
    "x-conference/x-cooltalk" -> "ice",
    "x-epoc/x-sisx-app" -> "sisx"
  )
}
