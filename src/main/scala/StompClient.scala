package xyz.hyperreal.stomp_server

import typings.sockjsDashClient.sockjsDashClientMod
import typings.std.{Event, MessageEvent, WebSocket}
import typings.uuid.uuidMod

import scala.scalajs.js


class StompClient( hostname: String, port: Int, path: String, onOpen: StompClient => Unit, onMessage: (StompClient, String, Map[String, String], String) => Unit ) {

  val sock = new sockjsDashClientMod.^( s"http://$hostname:$port$path" )

  val onopenCallback: js.ThisFunction1[WebSocket, Event, Any] =
    (_: WebSocket, _: Event) => {
      onOpen( this )
    }

  val onmessageCallback: js.ThisFunction1[WebSocket, MessageEvent, Any] =
    (_: WebSocket, ev: MessageEvent) => {
      parseMessage( ev.data.toString ) match {
        case None => sys.error( s"unparsable message from stomp server: ${ev.data}" )
        case Some( (command, headers, body) ) => onMessage( this, command, headers, body )
      }
    }

  sock.onopen = onopenCallback
  sock.onmessage = onmessageCallback

  def connect( headers: Map[String, String] ) = frame( "CONNECT", headers + ("accept-version" -> "1.2") )

  def disconnect( receipt: String = null ) = frame( "DISCONNECT", if (receipt eq null) Map() else Map("receipt" -> receipt) )

  def subscribe( queue: String, receipt: String = null ) =
    frame( "SUBSCRIBE",
      Map("destination" -> queue, "id" -> queue, "accept-version" -> "1.2") ++ (if (receipt eq null) Nil else List("receipt" -> receipt)) )

  def send( queue: String, body: String, contentType: String = "text/plain" ) = {
    val s = if (body eq null) "null" else body

    frame( "SEND", Map(
      "subscription" -> queue,
      "destination" -> queue,
      "content-type" -> contentType,
      "content-length" -> s.length.toString
    ), s )
  }

  def frame( command: String, headers: Map[String, String], body: String = "" ) = {
    val buf = new StringBuilder( s"$command\n" )

    buf ++= headers map {case (k, v) => s"$k:$v"} mkString "\n"
    buf ++= "\n\n"
    buf ++= body
    buf += '\u0000'

    sock.send( buf.toString )
  }

  def close = sock.close

}