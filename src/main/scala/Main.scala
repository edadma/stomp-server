package xyz.hyperreal.stomp_server

import typings.node.{nodeStrings, process}


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
