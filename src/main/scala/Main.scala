package xyz.hyperreal.stomp_server

import scala.scalajs.js


object Main extends App {

  def authorize( headers: js.Dictionary[String] ) = true

  val serverHostname = "0.0.0.0"
  val serverPort = 15674
  val serverPath = "/stomp"
  val server = new StompServer( "ShuttleControl/1.0", serverHostname, serverPort, serverPath, authorize, authorize, false )
  val client = new StompClient( serverHostname, serverPort, serverPath, _.connect(Map()), onMessage )

  def onMessage( command: String, headers: Map[String, String], body: String ): Unit = {
    command match {
      case "CONNECTED" =>
        client.subscribe( "data", "subscribe-receipt" )
      case "MESSAGE" =>
        println( s"${headers("destination")}: $body" )
      case "RECEIPT" =>
        if (headers("receipt-id") == "subscribe-receipt") {
          client.send( "data", "this is the message" )
        }
    }
  }

}