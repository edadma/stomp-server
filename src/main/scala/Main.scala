package xyz.hyperreal.stomp_server

import typings.node.httpMod
import typings.node.httpMod.{ClientRequest, ServerResponse}
import typings.uuid.uuidMod
import typings.sockjs
import typings.sockjs.sockjsMod.{Connection, ServerOptions}
import typings.sockjs.sockjsStrings

import scala.scalajs.js
import scala.scalajs.js.RegExp
import collection.mutable


object Main extends App {

  def authorize( token: String ) = {
    true
  }

  val server = new StompServer( "ShuttleControl/1.0", authorize, true )

}

class StompServer( name: String, authorize: String => Boolean, debug: Boolean = false ) {

  case class SubscriptionKey( conn: ConnectionKey, id: String )
  case class Subscription( destination: String, ack: String )

  case class ConnectionKey( removeAddress: String, remotePort: Double )

  private val sockjs_echo = sockjs.sockjsMod.createServer()
  private val subscriptions = new mutable.HashMap[SubscriptionKey, Subscription]
  private val connections = new mutable.HashMap[ConnectionKey, Int]

  sockjs_echo.on_connection( sockjsStrings.connection, conn => {
    val connectionKey = ConnectionKey( conn.remoteAddress, conn.remotePort )

    dbg( s"sockjs connection: ${conn.remoteAddress}, ${conn.remotePort}, ${conn.url}" )
    conn.on( "data", (message: String) => {
      if (message == "\n" || message == "\r\n") {
        dbg( s"heart beat received" )
      } else
        parseMessage( message ) match {
          case ("CONNECT", headers, _) =>
            dbg( s"stomp connection: $headers" )

            if (headers get "Authorization" match {
              case None => true
              case Some( token ) => authorize( token )
            }) {
              val beats = headers.getOrElse("heart-beat", "0,0").split(",")(1).toInt

              connections(connectionKey) = beats
              dbg( s"send heart beats every $beats millisends" )
              sendMessage(conn, "CONNECTED",
                List(
                  "version" -> "1.2",
                  "heart-beat" -> "10000,10000",
                  "session" -> createSession,
                  "server" -> name))
            } else {
              dbg( s"*** not authorized" )
            }
          case ("SUBSCRIBE", headers, _) =>
            val key = SubscriptionKey( connectionKey, headers("id") )

            dbg( s"subscribe: $headers" )
            subscriptions get key match {
              case Some( _ ) =>
                dbg( s"*** subscription duplicate: $key" )
              case None =>
                subscriptions(key) = Subscription( headers("destination"), headers.getOrElse("ack", "auto") )
            }
        }
    } )
  } )

  private val server = httpMod.createServer()

  server.addListener("upgrade", (reqres: js.Any) => {
    println( "upgrade" )
    reqres.asInstanceOf[Array[js.Any]](0).asInstanceOf[ClientRequest].end()
  } )

  sockjs_echo.installHandlers( server, js.Dynamic.literal(prefix = "/ws").asInstanceOf[ServerOptions] )
  println(" [*] Listening on 0.0.0.0:15674")
  server.listen( 15674, "0.0.0.0" )

  private def sendMessage( conn: Connection, command: String, headers: List[(String, String)], body: String = "" ) = {
    val escaped = headers map {case (k, v) => s"${escape(k)}:${escape( v )}"} mkString "\n"

    conn.write( s"$command\n$escaped\n\n${body}\u0000" )
  }

  private def createSession = {
    val id = uuidMod.^.v4

    id
  }

  private def escape( s: String ) = s.
    replace( "\\", "\\\\" ).
    replace( "\r", "\\r" ).
    replace( "\n", "\\n" ).
    replace( ":", "\\c" )

  private def dbg( s: String ) =
    if (debug)
      println( s"DEBUG $s" )

  //def send( destination: )

  object parseMessage extends {

    private val stompMessageRegex = RegExp( """([A-Z]+)\r?\n(.*?)\r?\n\r?\n([^\00]*)\00(?:\r?\n)*""", "s" )
    private val HeaderRegex = """([a-zA-Z0-9-\\]+):(.+)"""r

    def apply( message: String ) = {
      dbg( s"parseMessage: ${escape(message)}" )

      val List(_, command, headers, body ) = stompMessageRegex.exec( message ).toList
      val headerMap = HeaderRegex findAllMatchIn headers.toString map (m => unescape( m.group(1) ) -> unescape( m.group(2) )) toMap

      (command.toString, headerMap, body.toString)
    }

    private def unescape( s: String ) = s.
      replace( "\\r", "\r" ).
      replace( "\\n", "\n" ).
      replace( "\\c", ":" ).
      replace( "\\\\", "\\" )

  }

}
