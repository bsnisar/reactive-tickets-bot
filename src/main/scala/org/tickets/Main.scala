package org.tickets

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.slf4j.bridge.SLF4JBridgeHandler
import org.tickets.misc.LogSlf4j

object Main extends App with LogSlf4j {
  val init = System.currentTimeMillis()
  SLF4JBridgeHandler.removeHandlersForRootLogger()
  SLF4JBridgeHandler.install()

  val cfg = ConfigFactory.load().resolve()

  implicit val as = ActorSystem("bot")
  implicit val mt = ActorMaterializer()


  log.info(
    """
      | ---------------------------------------------------
      |   Main started in {} ms
      | ---------------------------------------------------
    """.stripMargin, System.currentTimeMillis() - init)
}
