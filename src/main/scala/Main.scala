package xyz.hyperreal.stomp_server

import typings.node.process
import typings.node.nodeStrings
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

  println( "type message" )

  process.stdin.setEncoding( "utf-8" )

  process.stdin.on_data( nodeStrings.data,
    body => {
      println( "sending..." )
      server.send( "data", uuidMod.^.v1, body.toString )
    } )

}

class StompServer( name: String, authorize: String => Boolean, debug: Boolean = false ) {

  case class Subscriber( client: Client, subscriptionId: String )
  case class Subscription( queue: String, ack: String )

  case class Client( removeAddress: String, remotePort: Double )

  case class StompConnection( conn: Connection, beats: Int )

  private val sockjs_echo = sockjs.sockjsMod.createServer()
  private val subscriptions = new mutable.HashMap[Subscriber, Subscription]
  private val connections = new mutable.HashMap[Client, StompConnection]
  private val queues = new mutable.HashMap[String, mutable.HashSet[Subscriber]]

  sockjs_echo.on_connection( sockjsStrings.connection, conn => {
    val connectionKey = Client( conn.remoteAddress, conn.remotePort )

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

              connections(connectionKey) = StompConnection( conn, beats )

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
            val subscriber = Subscriber( connectionKey, headers("id") )

            dbg( s"subscribe: $headers" )
            subscriptions get subscriber match {
              case Some( _ ) =>
                dbg( s"*** subscription duplicate: $subscriber" )
              case None =>
                val queue = headers("destination")

                subscriptions(subscriber) = Subscription( headers("destination"), headers.getOrElse("ack", "auto") )

                val subscribers =
                  queues get queue match {
                    case None =>
                      dbg( s"created queue '$queue' for $subscriber" )
                      new mutable.HashSet[Subscriber]
                    case Some( subs ) => subs += subscriber
                  }

                queues(queue) = subscribers
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
    val escapedHeaders = headers map {case (k, v) => s"${escape(k)}:${escape( v )}"} mkString "\n"
    val message = s"$command\n$escapedHeaders\n\n${body}\u0000"

    dbg( s"sending message '${escape(message)}' to $conn" )
    conn.write( message )
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

  def send( queue: String, messageId: String, body: String, mime: String = "text/plain" ): Unit = {
    queues get queue match {
      case None =>
      case Some( subs ) =>
        for (Subscriber(client, subscriptionId) <- subs) {
          dbg( s"messaging $client, queue $queue: '$body'")
          sendMessage( connections(client).conn, "MESSAGE",
            List(
              "subscription" -> subscriptionId,
              "message-id" -> messageId,
              "destination" -> queue,
              "content-type" -> mime,
              "content-length" -> body.length.toString),
            body )
        }
    }
  }

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
