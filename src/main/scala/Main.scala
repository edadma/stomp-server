package xyz.hyperreal.stomp_server

import typings.node.httpMod
import typings.node.httpMod.{ClientRequest, ServerResponse}
import typings.uuid
import typings.sockjs
import typings.sockjs.sockjsMod.ServerOptions
import typings.sockjs.sockjsStrings

import scala.scalajs.js
import scala.scalajs.js.RegExp


object Main extends App {

  val sockjs_echo = sockjs.sockjsMod.createServer()

  sockjs_echo.on_connection( sockjsStrings.connection, conn => {
    println("connection")
    conn.on( "data", (message: String) => {
      parseMessage( message ) match {
        case ("CONNECT", headers, _) =>
          println( s"connecting ${headers("login")}" )
      }
    } )
  } )

  val server = httpMod.createServer()

//  server.addListener("request", (req: ClientRequest, res: ServerResponse) => {
//    println( "static" )
//  } )

  server.addListener("upgrade", (reqres: js.Any) => {
    println( "upgrade" )
    reqres.asInstanceOf[Array[js.Any]](0).asInstanceOf[ClientRequest].end()
  } )

  sockjs_echo.installHandlers( server, js.Dynamic.literal(prefix = "/ws").asInstanceOf[ServerOptions] )
  println(" [*] Listening on 0.0.0.0:15674")
  //server.listen( 15674, "0.0.0.0" )

}

object parseMessage extends {

  private val stompMessageRegex = RegExp( """([A-Z]+)\r?\n(.*?)\r?\n\r?\n([^\00]*)\00""", "s" )
  private val HeaderRegex = """([a-z0-9\]+):(.+)"""r

  def apply( message: String ) = {
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