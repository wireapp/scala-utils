package com.wire.network

import java.io.{File, InputStream}

import com.wire.data.JsonEncoder
import com.wire.network.ContentEncoder.RequestContent
import org.json.JSONObject


trait ContentEncoder[A] {
  self =>
  def apply(data: A): RequestContent

  def map[B](f: B => A): ContentEncoder[B] = new ContentEncoder[B] {
    override def apply(data: B): RequestContent = self(f(data))
  }
}

object ContentEncoder {

  sealed trait RequestContent

  case object EmptyRequestContent extends RequestContent

  trait ByteArrayRequestContent extends RequestContent

  case class BinaryRequestContent(data: Array[Byte], contentType: String) extends ByteArrayRequestContent

  case class GzippedRequestContent(bytes: Array[Byte], contentType: String) extends ByteArrayRequestContent

  case class StreamRequestContent(stream: InputStream, contentType: String, length: Int) extends RequestContent

  //TODO this string comes from some Koushikdutta dependency
  class MultipartRequestContent(parts: Seq[Part], contentType: String = "multipart/form-data") extends RequestContent

  object MultipartRequestContent {
    def apply(files: Seq[(String, File)]): MultipartRequestContent = new MultipartRequestContent(files.map { case (name, file) => new FilePart(name, file) })
  }

  implicit object RequestContentEncoder extends ContentEncoder[RequestContent] {
    override def apply(data: RequestContent) = data
  }

  implicit object BinaryContentEncoder extends ContentEncoder[BinaryRequestContent] {
    override def apply(data: BinaryRequestContent) = data
  }

  implicit object MultipartContentEncoder extends ContentEncoder[MultipartRequestContent] {
    override def apply(data: MultipartRequestContent) = data
  }

  implicit object GzippedContentEncoder extends ContentEncoder[GzippedRequestContent] {
    override def apply(data: GzippedRequestContent) = data
  }

  implicit object EmptyContentEncoder extends ContentEncoder[Unit] {
    override def apply(data: Unit) = EmptyRequestContent
  }

  implicit object StringContentEncoder extends ContentEncoder[String] {
    override def apply(data: String) = BinaryRequestContent(data.getBytes("utf8"), "text/plain")
  }

  implicit object JsonContentEncoder extends ContentEncoder[JSONObject] {
    override def apply(data: JSONObject) = GzippedRequestContent(data.toString.getBytes("utf8"), "application/json")
  }

  implicit def json[A: JsonEncoder]: ContentEncoder[A] = JsonContentEncoder.map(implicitly[JsonEncoder[A]].apply)

  def gzipJson[A: JsonEncoder]: ContentEncoder[A] = new ContentEncoder[A] {
    override def apply(data: A): RequestContent = GzippedRequestContent(implicitly[JsonEncoder[A]].apply(data).toString.getBytes("utf8"), "application/json")
  }

  //TODO import protobuf
//  def protobuf(msg: MessageNano) = BinaryRequestContent(MessageNano.toByteArray(msg), "application/x-protobuf")
}
