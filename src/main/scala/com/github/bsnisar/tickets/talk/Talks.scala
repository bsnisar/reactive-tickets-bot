package com.github.bsnisar.tickets.talk


import akka.actor.{Actor, ActorContext, ActorRef, Props}
import com.github.bsnisar.tickets.talk.Talks.BotFactory
import com.github.bsnisar.tickets.telegram.TelegramUpdates.{Update, Updates}
import com.typesafe.scalalogging.LazyLogging

object Talks {
  def props(factory: BotFactory, stationsSearcher: ActorRef): Props =
    Props(classOf[Talks], factory, stationsSearcher)
  def props(props: Props, stationsSearcher: ActorRef): Props =
    Props(classOf[Talks], ByContext(props), stationsSearcher)

  trait BotFactory {
    def create(name: String)(implicit ac: ActorContext): ActorRef
  }

  final case class ByContext(props: Props) extends BotFactory {
    override def create(name: String)(implicit ac: ActorContext): ActorRef =
      ac.actorOf(props, name)
  }

}

/**
  * Chats router. Delegate each message separate handler.
  * If there is no available bot for given chat, create new one.
  */
final class Talks(factory: BotFactory, val stationsSearcher: ActorRef) extends Actor with LazyLogging {
  private var chats = Map.empty[String, ActorRef]

  override def receive: Receive = {
    case update: Update =>
      val chatID = update.chat
      chats.get(chatID) match {
        case Some(ref) =>
          ref ! update
        case None =>
          val botName = s"chat::$chatID"
          logger.debug("new bot {} created", botName)
          val ref = factory.create(botName)
          chats = chats + (chatID -> ref)
          ref ! update
      }
  }
}
