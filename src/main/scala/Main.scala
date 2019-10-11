package xyz.hyperreal.stomp_server

import typings.node.httpMod
import typings.node.httpMod.{ClientRequest, ServerResponse}
import typings.uuid
import typings.sockjs
import typings.sockjs.sockjsMod.ServerOptions
import typings.sockjs.sockjsStrings

import scala.scalajs.js


object Main extends App {

  val sockjs_echo = sockjs.sockjsMod.createServer()

  sockjs_echo.on_connection( sockjsStrings.connection, conn => {
    println("connection")
    conn.on( "data", (message: String) => {
      conn.write( message )
      println(message)
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
  server.listen( 15674, "0.0.0.0" )

}