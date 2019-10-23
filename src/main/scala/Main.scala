package xyz.hyperreal.stomp_server

import typings.node.{nodeStrings, process, setTimeout}
import typings.sockjsDashClient.sockjsDashClientMod
import typings.std.{Event, MessageEvent, WebSocket}

import scala.scalajs.js.ThisFunction1
//import typings.stompjs.stompjsMod
//import typings.stompjs.stompjsMod.{Client, Message}

import scala.concurrent.Promise
import scala.scalajs.js
import scala.scalajs.js.JSON


object Main extends App {

  def authorize( headers: js.Dictionary[String] ) = true

  val serverHostname = "0.0.0.0"
  val serverPort = 15674
  val serverPath = "/stomp"
  val server = new StompServer( "ShuttleControl/1.0", serverHostname, serverPort, serverPath, authorize, authorize, true )

  val sock = new sockjsDashClientMod.^( s"http://$serverHostname:$serverPort$serverPath" )

  val onopenCallback: ThisFunction1[WebSocket, Event, Any] =
    (_: WebSocket, _: Event) => {
      send( "CONNECT", "", "accept-version" -> "1.2" )
    }

  val onmessageCallback: ThisFunction1[WebSocket, MessageEvent, Any] =
    (_: WebSocket, ev: MessageEvent) => {
      println( ev.data )
    }

  sock.onopen = onopenCallback
  sock.onmessage = onmessageCallback

  def send( command: String, body: String, headers: (String, String)* ) = {
    val buf = new StringBuilder( s"$command\n" )

    buf ++= headers map {case (k, v) => s"$k:$v"} mkString "\n"
    buf ++= "\n\n"
    buf ++= body
    buf += '\u0000'

    sock.send( buf.toString )
  }
}
