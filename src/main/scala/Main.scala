package xyz.hyperreal.stomp_server

import typings.node.NodeJS.Timeout
import typings.node.{clearInterval, httpMod, nodeStrings, process, setInterval, setTimeout}
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

  def authorize( headers: Map[String, String] ) = {
    true
  }

  val server = new StompServer( "ShuttleControl/1.0", "0.0.0.0", 15674, "/stomp", authorize, true )

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
  private val CONNECTION_LINGERING_DELAY = 1000

}

class StompServer( name: String, hostname: String, port: Int, path: String, authorized: Map[String, String] => Boolean, debug: Boolean = false ) {

  import StompServer._

  case class Subscriber( client: String, subscriptionId: String )
  case class Subscription( queue: String, ack: String )

  case class StompConnection( conn: Connection, beats: Int, timer: Timeout )

  case class Message( queue: String, body: String, contentType: String )

  private val sockjs_echo = sockjs.sockjsMod.createServer()
  private val subscriptions = new mutable.HashMap[Subscriber, Subscription]
  private val connections = new mutable.HashMap[String, StompConnection]
  private val queues = new mutable.HashMap[String, mutable.HashSet[Subscriber]]
  private val transactions = new mutable.HashMap[String, ListBuffer[Message]]

  sockjs_echo.on_connection( sockjsStrings.connection, conn => {
    dbg( s"sockjs connection: ${conn.remoteAddress}, ${conn.remotePort}, ${conn.url}" )

    conn.on( "data", listener = (message: String) => {
      if (message == "\n" || message == "\r\n") {
        dbg(s"heart beat received")
      } else
        parseMessage( message ) match {
          case ("CONNECT"|"STOMP", headers, _) =>
            dbg(s"stomp connection: $headers")
//            required( conn, message, headers, "accept-version", "host" )// todo: shuttlecontrol frontend doesn't put 'host' header
            required( conn, message, headers, "accept-version" )

            def heartBeat( a: js.Any ) = {
              conn.write( "\n" )
              dbg( "send heart beat" )
            }

            if (authorized( headers )) {
              val beats = headers.getOrElse("heart-beat", "0,0").split(",")(1).toInt
              val timer = setInterval( heartBeat, beats )

              connections(conn.id) = StompConnection(conn, beats, timer)

              dbg( s"send heart beats every $beats millisends" )
              sendMessage( conn, "CONNECTED",
                List(
                  "version" -> "1.2",
                  "heart-beat" -> "10000,10000",
                  "session" -> createSession,
                  "server" -> name) )
            } else {
              dbg( s"*** not authorized" )
              error( conn, headers, "not authorized" )
            }
          case ("SUBSCRIBE", headers, _) =>
            dbg( s"subscribe: $headers" )
            required( conn, message, headers, "destination", "id" )

            val subscriber = Subscriber(conn.id, headers("id"))

            if (subscriptions contains subscriber) {
              dbg(s"*** duplicate subscription: $subscriber")
              error( conn, headers, "duplicate subscription" )
            } else {
              val queue = headers("destination")

              subscriptions(subscriber) = Subscription( headers("destination"), headers.getOrElse("ack", "auto") )

              val subscribers =
                queues get queue match {
                  case None =>
                    dbg(s"created queue '$queue' for $subscriber")
                    mutable.HashSet[Subscriber]( subscriber )
                  case Some(subs) => subs += subscriber
                }

              queues(queue) = subscribers
              receipt( conn, headers )
            }
          case ("UNSUBSCRIBE", headers, _) =>
            dbg( s"unsubscribe: $headers")
            required( conn, message, headers, "id" )

            val subscriber = Subscriber( conn.id, headers("id") )

            subscriptions get subscriber match {
              case Some(Subscription(queue, _)) =>
                subscriptions -= subscriber

                val set = queues(queue)

                set -= subscriber

                if (set isEmpty)
                  queues -= queue
              case None =>
                dbg( s"*** subscription not found: $subscriber" )
                error( conn, headers, "subscription not found" )
            }

            receipt( conn, headers )
          case ("DISCONNECT", headers, _) =>
            dbg( s"disconnect: $headers, $conn" )
            receipt( conn, headers )
            disconnect( conn )
          case ("SEND", headers, body) =>
            dbg( s"send: $headers, $body" )
            required( conn, message, headers, "destination" )

            headers get "transaction" match {
              case Some(tx) => addToTransaction(tx, headers, body)
              case None => send( headers("destination"), body, headers.getOrElse("content-type", "text/plain") )
            }

            receipt( conn, headers )
          case ("BEGIN", headers, _) =>
            dbg( s"begin: $headers" )
            required( conn, message, headers, "transaction" )

            val tx = headers("transaction")

            if (transactions contains tx)
              error( conn, headers, "transaction already begun" )
            else
              transactions(tx) = new ListBuffer[Message]

            receipt( conn, headers )
          case ("COMMIT", headers, _) =>
            dbg( s"commit: $headers" )
            required( conn, message, headers, "transaction" )

            val tx = headers("transaction")

            transactions get tx match {
              case None => error( conn, headers, "transaction doesn't exist" )
              case Some( msgs ) =>
                for (Message( queue, body, contentType ) <- msgs)
                  send( queue, body, contentType )

                transactions -= tx
            }

            receipt( conn, headers )
          case ("ABORT", headers, _) =>
            dbg( s"abort: $headers" )
            required( conn, message, headers, "transaction" )

            val tx = headers("transaction")

            if (transactions contains tx)
              transactions -= tx
            else {
              // todo: error: transaction not begun
            }

            receipt( conn, headers )
          case ("ACK", headers, _) =>
            dbg( s"ack: $headers" )

          case ("NACK", headers, _) =>
            dbg( s"nack: $headers" )
        }
    } )
  } )

  private val server = httpMod.createServer()

  server.addListener("upgrade", (reqres: js.Any) => {
    dbg( "upgrade" )
    reqres.asInstanceOf[Array[js.Any]](0).asInstanceOf[ClientRequest].end()
  } )

  sockjs_echo.installHandlers( server, js.Dynamic.literal(prefix = path).asInstanceOf[ServerOptions] )
  println( s"Listening on $hostname:$port")
  server.listen( port, hostname )

  private def addToTransaction( transaction: String, headers: Map[String, String], body: String ) = {
    transactions(transaction) += Message( headers("destination"), body, headers.getOrElse("content-type", DEFAULT_CONTENT_TYPE) )
  }

  private def sendMessage( conn: Connection, command: String, headers: List[(String, String)], body: String = "" ) = {
    val escapedHeaders = headers map { case (k, v) => s"${escape( k )}:${escape( v )}" } mkString "\n"
    val message = s"$command\n$escapedHeaders\n\n${body}\u0000"

    dbg( s"sending message '${escape(message)}' to $conn" )
    conn.write( message )
    connections(conn.id).timer.refresh
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

  private def receipt( conn: Connection, headers: Map[String, String] ): Unit = {
    headers get "receipt" match {
      case None =>
      case Some( r ) => sendMessage( conn, "RECEIPT", List("receipt-id" -> r) )
    }
  }

  private def required( conn: Connection, message: String, headers: Map[String, String], requiredHeaders: String* ) = {
    val requiredSet = requiredHeaders.toSet
    val missingSet = requiredSet -- (headers.keySet intersect requiredSet)

    if (missingSet nonEmpty) {
      val missing = missingSet.head

      dbg( s"*** missing headers: $missingSet" )
      error( conn, headers, "malformed frame received",
        s"""
           |The message:
           |-----
           |$message
           |-----
           |Did not contain a $missing header, which is REQUIRED
           |""".stripMargin )
      false
    } else
      true
  }

  private def disconnect( conn: Connection ) = {
    setTimeout( _ => {
      conn.close
      clearInterval( connections(conn.id).timer )
    }, CONNECTION_LINGERING_DELAY )
  }

  def error( conn: Connection, headers: Map[String, String], message: String, body: String = "" ): Unit = {
    val errorHeaders =
      List(
        "content-type" -> "text/plain",
        "content-length" -> body.length.toString,
        "message" -> message
      ) ++ (headers get "receipt" match {
        case None => Nil
        case Some( r ) => List( "receipt-id" -> r )
      })

    sendMessage( conn, "ERROR", errorHeaders, body )
    disconnect( conn )
  }

  def send( queue: String, body: String, contentType: String = "text/plain" ): Unit =
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
