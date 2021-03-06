package com.github.bsnisar.tickets.misc

import java.util.Base64

import com.github.bsnisar.tickets.misc.StationId.Id
import com.google.common.base.Charsets
import org.hashids.Hashids

import scala.util.{Failure, Try}
import scala.util.matching.Regex

object StationId {
  val StationsPointerCommands: Regex = "^(/from_|/to_).*".r

  val FromKey = "/from_"
  val ToKey = "/to_"
  val FromKeyPattern: Regex = "^/from_(.*?)".r
  val ToKeyPattern: Regex = "^/to_(.*?)".r

  case class Id(id: String, from: Boolean)
}

/**
  * Encoder and decoder, add mnemonic information to a telegram command.
  */
trait StationId {
  def encode(id: String, from: Boolean): String
//  def decode(id: String): Try[Id]
  def decoder: PartialFunction[String, Try[Id]]
}

class StationIdHashids extends StationId {
  private val hashids = new Hashids

  override def encode(id: String, isDeparture: Boolean): String = {
    val cmdPrefix = if (isDeparture) StationId.FromKey else StationId.ToKey
    val encoded = hashids.encode(id.toLong)
    s"$cmdPrefix$encoded"
  }

  /*override def decode(id: String): Try[Id] = Try {
    id match {
      case StationId.ToKeyPattern(toDecode) =>
        Id(transform(toDecode), from = false)
      case StationId.FromKeyPattern(toDecode) =>
        Id(transform(toDecode), from = true)
      case _ =>
        throw new IllegalArgumentException(s"unexpected encoded id format [$id]")
    }
  }*/

  private def transform(data: String): String = {
    val raw = hashids.decode(data)
    String.valueOf(raw(0))
  }

  override val decoder: PartialFunction[String, Try[Id]] = {
    case StationId.ToKeyPattern(toDecode) =>
      Try { Id(transform(toDecode), from = false) }
    case StationId.FromKeyPattern(toDecode) =>
      Try { Id(transform(toDecode), from = true) }
    case id =>
      Failure(new IllegalArgumentException(s"unexpected encoded id format [$id]"))
  }
}

/**
  * Base64 encoder.
  */
class StationIdBase64() extends StationId {
  override def encode(id: String, isDeparture: Boolean): String = {
    val cmdPrefix = if (isDeparture) StationId.FromKey else StationId.ToKey
    val encoded = Base64.getEncoder.encodeToString(id.getBytes(Charsets.UTF_8))
    s"$cmdPrefix$encoded"
  }

  override val decoder: PartialFunction[String, Try[Id]] = {
    case StationId.ToKeyPattern(toDecode) =>
      Try { Id(transform(toDecode), from = false) }
    case StationId.FromKeyPattern(toDecode) =>
      Try { Id(transform(toDecode), from = true) }
    case id =>
      Failure(new IllegalArgumentException(s"unexpected encoded id format [$id]"))
  }

  private def transform(data: String): String = {
    val raw = Base64.getDecoder.decode(data)
    new String(raw, Charsets.UTF_8)
  }

}
