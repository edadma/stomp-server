package xyz.hyperreal.stomp_server

import typings.node.NodeJS.Timeout
import typings.node.httpMod.ClientRequest
import typings.node.{clearInterval, httpMod, setInterval, setTimeout}
import typings.sockjs
import typings.sockjs.sockjsMod.{Connection, ServerOptions}
import typings.sockjs.sockjsStrings
import typings.uuid.uuidMod

import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.scalajs.js
import js.annotation.{JSExport, JSExportTopLevel}
import js.JSConverters._

object StompServer {

  private val DEFAULT_CONTENT_TYPE = "text/plain"
  private val CONNECTION_LINGERING_DELAY = 1000

}

@JSExportTopLevel("StompServer")
class StompServer( name: String, hostname: String, port: Int, path: String, connectAuthorize: js.Function1[js.Dictionary[String], Boolean],
                   subscribeAuthorize: js.Function1[js.Dictionary[String], Boolean], debug: Boolean = false ) {

  import StompServer._

  case class Subscriber( client: String, subscriptionId: String )
  case class Subscription( queue: String, ack: String )
  case class StompConnection( conn: Connection, sendBeats: Int, receiveBeats: Int, var lastReceived: Long, timer: Timeout )
  case class Message( queue: String, body: String, contentType: String )

  private val sock = sockjs.sockjsMod.createServer()
  private val subscriptions = new mutable.HashMap[Subscriber, Subscription]
  private val connections = new mutable.HashMap[String, StompConnection]
  private val queueMap = new mutable.HashMap[String, mutable.HashSet[Subscriber]]
  private val transactions = new mutable.HashMap[String, ListBuffer[Message]]

  sock.on_connection( sockjsStrings.connection, conn => {
    dbg( s"sockjs connection: ${conn.remoteAddress}, ${conn.remotePort}, ${conn.url}, $conn" )

    conn.on( "data", listener = (message: String) => {
      if (message == "\n" || message == "\r\n") {
        dbg( s"heart beat received from ${conn.remoteAddress}:${conn.remotePort}/$conn" )
        connections(conn.id).lastReceived = System.currentTimeMillis
      } else {
        dbg( s"parse message: ${escape(message)}, $conn" )

        parseMessage( message ) match {
          case None => error( conn, Map(), "couldn't parse message" )
          case Some( ("CONNECT"|"STOMP", headers, _) ) =>
            dbg( s"stomp connection: $headers over $conn" )
            //required( conn, message, headers, "accept-version", "host" )// todo: shuttlecontrol frontend doesn't put 'host' header
            required( conn, message, headers, "accept-version" )

            def heartBeat( a: js.Any ) = {
              conn.write( "\n" )
              dbg( s"heart beat sent to ${conn.remoteAddress}:${conn.remotePort}/$conn" )

              connections(conn.id) match {
                case StompConnection(_, _, 0, _, _) =>
                case StompConnection(_, _, receiveBeats, lastReceived, timer) =>
                  val time = System.currentTimeMillis

                  if (time - lastReceived > receiveBeats + 100) {
                    val id = conn.id

                    dbg( s"dead connection: ${conn.remoteAddress}:${conn.remotePort}/$conn" )

                    if (timer ne null)
                      clearInterval( timer )

                    conn.close
                    connections -= id
                  }
              }
            }

            if (connectAuthorize( headers.toJSDictionary )) {
              val Array(cx, cy) = headers.getOrElse("heart-beat", "0,0") split "," map (_.toInt)
              val send = cy min 10000
              val rec = cx min 10000

              connections(conn.id) =
                if (send == 0) {
                  dbg( s"send heart beats never" )
                  StompConnection( conn, 0, rec, System.currentTimeMillis, null )
                } else {
                  dbg( s"send heart beats every $send milliseconds" )
                  StompConnection( conn, send, rec, System.currentTimeMillis, setInterval(heartBeat, send) )
                }

              sendMessage( conn, "CONNECTED",
                List(
                  "version" -> "1.2",
                  "heart-beat" -> "10000,10000",
                  "session" -> createSession,
                  "server" -> name) )
            } else {
              dbg( s"*** not authorized to connect" )
              error( conn, headers, "not authorized" )
            }
          case Some( ("SUBSCRIBE", headers, _) ) =>
            dbg( s"subscribe: $headers, $conn" )
            required( conn, message, headers, "destination", "id" )

            if (subscribeAuthorize( headers.toJSDictionary )) {
              val subscriber = Subscriber(conn.id, headers("id"))

              if (subscriptions contains subscriber) {
                dbg(s"*** duplicate subscription: $subscriber")
                error( conn, headers, "duplicate subscription" )
              } else {
                val queue = headers("destination")

                subscriptions(subscriber) = Subscription( headers("destination"), headers.getOrElse("ack", "auto") )

                val subscribers =
                  queueMap get queue match {
                    case None =>
                      dbg(s"created queue '$queue' for $subscriber")
                      mutable.HashSet[Subscriber]( subscriber )
                    case Some(subs) => subs += subscriber
                  }

                queueMap(queue) = subscribers
                receipt( conn, headers )
              }
            } else {
              dbg( s"*** not authorized to subscribe" )
              error( conn, headers, "not authorized" )
            }
          case Some( ("UNSUBSCRIBE", headers, _) ) =>
            dbg( s"unsubscribe: $headers")
            required( conn, message, headers, "id" )

            val subscriber = Subscriber( conn.id, headers("id") )

            subscriptions get subscriber match {
              case Some(Subscription(queue, _)) =>
                subscriptions -= subscriber

                val set = queueMap(queue)

                set -= subscriber

                if (set isEmpty)
                  queueMap -= queue
              case None =>
                dbg( s"*** subscription not found: $subscriber" )
                error( conn, headers, "subscription not found" )
            }

            receipt( conn, headers )
          case Some( ("DISCONNECT", headers, _) ) =>
            dbg( s"disconnect: $headers, $conn" )
            receipt( conn, headers )
            disconnect( conn )
          case Some( ("SEND", headers, body) ) =>
            dbg( s"send: $headers, $body" )
            required( conn, message, headers, "destination" )

            headers get "transaction" match {
              case Some(tx) => addToTransaction(tx, headers, body)
              case None => send( headers("destination"), body, headers.getOrElse("content-type", "text/plain") )
            }

            receipt( conn, headers )
          case Some( ("BEGIN", headers, _) ) =>
            dbg( s"begin: $headers" )
            required( conn, message, headers, "transaction" )

            val tx = headers("transaction")

            if (transactions contains tx)
              error( conn, headers, "transaction already begun" )
            else
              transactions(tx) = new ListBuffer[Message]

            receipt( conn, headers )
          case Some( ("COMMIT", headers, _) ) =>
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
          case Some( ("ABORT", headers, _) ) =>
            dbg( s"abort: $headers" )
            required( conn, message, headers, "transaction" )

            val tx = headers("transaction")

            if (transactions contains tx)
              transactions -= tx
            else {
              // todo: error: transaction not begun
            }

            receipt( conn, headers )
          case Some( ("ACK", headers, _) ) =>
            dbg( s"ack: $headers" )

          case Some( ("NACK", headers, _) ) =>
            dbg( s"nack: $headers" )
        }
      }
    } )
  } )

  private val server = httpMod.createServer()

  server.addListener("upgrade", (reqres: js.Any) => {
    dbg( "upgrade" )
    reqres.asInstanceOf[Array[js.Any]](0).asInstanceOf[ClientRequest].end()
  } )

  sock.installHandlers( server, js.Dynamic.literal(prefix = path).asInstanceOf[ServerOptions] )
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

    connections get conn.id match {
      case None | Some( StompConnection(_, _, _, _, null) ) =>
      case Some( StompConnection(_, _, _, _, timer) ) => timer.refresh
    }
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
      connections get conn.id match {
        case None | Some( StompConnection(_, _, _, _, null) ) =>
        case Some( c ) => clearInterval( c.timer )
      }

      conn.close
    }, CONNECTION_LINGERING_DELAY )
  }

  private def parseClientMessage( message: String, conn: Connection ) = {
    dbg( s"parse message: ${escape(message)}, $conn" )

     parseMessage( message ) match {
      case None =>
      case Some( res ) => res
    }
  }

  private def error( conn: Connection, headers: Map[String, String], message: String, body: String = "" ): Unit = {
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

  @JSExport
  def send( queue: String, body: String, contentType: String = "text/plain" ): Unit =
    queueMap get queue match {
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

  @JSExport
  def queues() = queueMap.keysIterator.toJSArray.sorted

}
