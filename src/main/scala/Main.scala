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

  case class SubscriptionKey( removeAddress: String, remotePort: Double, id: String )
  case class Subscription( destination: String, ack: String )

  private val sockjs_echo = sockjs.sockjsMod.createServer()
  private val subscriptions = new mutable.HashMap[SubscriptionKey, Subscription]

  sockjs_echo.on_connection( sockjsStrings.connection, conn => {
    dbg( s"sockjs connection: ${conn.remoteAddress}, ${conn.remotePort}, ${conn.url}" )
    conn.on( "data", (message: String) => {
      parseMessage( message ) match {
        case ("CONNECT", headers, _) =>
          dbg( s"stomp connection: $headers" )

          if (headers get "Authorization" match {
            case None => true
            case Some( token ) => authorize( token )
          }) {
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
          val key = SubscriptionKey( conn.remoteAddress, conn.remotePort, headers("id") )

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
    private val HeaderRegex = """([a-z0-9\\]+):(.+)"""r

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
