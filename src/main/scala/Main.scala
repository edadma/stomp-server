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
import scala.collection.mutable.ListBuffer


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
      server.send( "data", body.toString )
    } )

}

object StompServer {

  private val DEFAULT_CONTENT_TYPE = "text/plain"

}

class StompServer( name: String, authorize: String => Boolean, debug: Boolean = false ) {

  import StompServer._

  case class Subscriber( client: Client, subscriptionId: String )
  case class Subscription( queue: String, ack: String )

  case class Client( removeAddress: String, remotePort: Double )

  case class StompConnection( conn: Connection, beats: Int )

  case class Message( queue: String, body: String, contentType: String )

  private val sockjs_echo = sockjs.sockjsMod.createServer()
  private val subscriptions = new mutable.HashMap[Subscriber, Subscription]
  private val connections = new mutable.HashMap[Client, StompConnection]
  private val queues = new mutable.HashMap[String, mutable.HashSet[Subscriber]]
  private val transactions = new mutable.HashMap[String, ListBuffer[Message]]

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
              sendMessage( conn, "CONNECTED",
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

            if (subscriptions contains subscriber)
              dbg( s"*** subscription duplicate: $subscriber" )
            else {
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
          case ("UNSUBSCRIBE", headers, _) =>
            val subscriber = Subscriber( connectionKey, headers("id") )

            dbg( s"unsubscribe: $headers" )

            subscriptions get subscriber match {
              case Some( Subscription(queue, _) ) =>
                subscriptions -= subscriber

                val set = queues(queue)

                set -= subscriber

                if (set isEmpty)
                  queues -= queue
              case None =>
                dbg( s"*** subscription not found: $subscriber" )
            }
          case ("DISCONNECT", headers, _) =>
            dbg( s"disconnect: $headers, $connectionKey" )
            sendMessage( conn, "RECEIPT", List("receipt-id" -> headers("receipt")) )
            // todo: close the connection after a small delay
            conn.close
          case ("SEND", headers, body) =>
            dbg( s"send: $headers, $body" )

            headers get "transaction" match {
              case Some( tx ) => addToTransaction( tx, headers, body )
              case None => send( headers("destination"), body, headers.getOrElse("content-type", "text/plain") )
            }
          case ("BEGIN", headers, _) =>
            dbg( s"begin: $headers" )

            val tx = headers("transaction")

            if (transactions contains tx) {
              // todo: error: transaction already begun
            } else
              transactions(tx) = new ListBuffer[Message]
          case ("COMMIT", headers, _) =>
            dbg( s"commit: $headers" )

            transactions get
          case ("ABORT", headers, _) =>
            dbg( s"abort: $headers" )

            val tx = headers("transaction")

            if (transactions contains tx)
              transactions -= tx
            else {
              // todo: error: transaction not begun
            }
          case ("ACK", headers, _) =>
          case ("NACK", headers, _) =>
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

  private def addToTransaction( transaction: String, headers: Map[String, String], body: String ) = {
    transactions(transaction) += Message( headers("destination"), body, headers.getOrElse("content-type"), DEFAULT_CONTENT_TYPE )
  }

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

  def send( queue: String, body: String, contentType: String = "text/plain" ): Unit = {
    queues get queue match {
      case None =>
      case Some( subs ) =>
        for (Subscriber(client, subscriptionId) <- subs) {
          dbg( s"messaging $client, queue $queue: '$body'")
          sendMessage( connections(client).conn, "MESSAGE",
            List(
              "subscription" -> subscriptionId,
              "message-id" -> uuidMod.^.v1,
              "destination" -> queue,
              "content-type" -> contentType,
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
