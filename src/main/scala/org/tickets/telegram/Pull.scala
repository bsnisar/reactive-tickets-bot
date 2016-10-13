package org.tickets.telegram

import akka.actor.{Actor, ActorRef, Props}
import akka.http.scaladsl.unmarshalling._
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import org.tickets.misc.LogSlf4j
import org.tickets.telegram.Method.TgReq
import org.tickets.telegram.Pull.{Ack, NotFetch, Tick}
import org.tickets.telegram.Telegram.{BotToken, HttpFlow}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object Pull {

  /**
    * Cron trigger
    */
  case object Tick

  /**
    * Pull failed, and should be repeat
    */
  case object NotFetch

  /**
    * Next peace of updates have been consumed
    * @param seqNum next sequence number.
    */
  case class Ack(seqNum: Int)

  def props(httpFlow: HttpFlow, botToken: MethodBindings, dest: ActorRef)(implicit mt: Materializer): Props =
    Props(classOf[Pull], httpFlow, botToken, mt, dest)
}

/**
  * Periodically pull updates from Telegram API. Do it in a classic manure with scheduled cron ticks.
  * @param httpFlow connection flow to Telegram API
  * @param token bot token
  * @param mt materializer
  * @param dest destination of messages
  */
class Pull(val httpFlow: HttpFlow,
           val token: MethodBindings,
           val mt: Materializer, dest: ActorRef) extends Actor with LogSlf4j {

  import context.dispatcher

  /**
    * Offset of messages that was consumed.
    */
  private var offset: Int = -1

  override def receive: Receive = pulling()

  private def ack(): Receive = {
    case Ack(seqNum) =>
      log.debug("#ack got acknowledge by {}", seqNum)
      offset = seqNum + 1
      context become pulling()
    case NotFetch =>
      context become pulling()
  }

  private def pulling(): Receive = {
    case Tick =>
      fetchUpdates.onComplete(deliverUpdates)
      context become ack()
  }

  /**
    * Send updates to destination or recall updates again.
    * @param tryUpdates result of computation
    */
  private def deliverUpdates(tryUpdates: Try[Updates]): Unit = tryUpdates match {
    case Success(updates) if updates.empty =>
      log.debug("#deliverUpdates no updates available")
      context become pulling()
    case Success(updates) =>
      log.debug("#deliverUpdates fetched next updates[{}], resend to [{}]", updates.size, dest)
      dest ! updates
      self ! Ack(updates.lastId)
    case Failure(ex) =>
      log.error("#deliverUpdates failed", ex)
      self ! NotFetch
  }


  /**
    * Call host for updates and pars it into [[Updates]]
    * @return async result of updates.
    */
  private def fetchUpdates: Future[Updates] = {
    val req: TgReq = if (offset < 0) token.createGetUpdates() else token.createGetUpdates(Some(offset))
    log.debug("#fetchUpdates: require updates with offset > {}", offset)

    implicit val materializer = mt
    implicit val um: FromEntityUnmarshaller[Updates] = UpdatesJVal.fromEntityToJson4s

    Source.single(req)
      .via(httpFlow)
      .mapAsync(1) {
        case (Success(httpResponse), method) =>
          Unmarshal(httpResponse.entity).to[Updates]
        case (Failure(error), method) =>
          Future.failed(error)
      }.runWith(Sink.head)
  }

}
