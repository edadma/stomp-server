package xyz.hyperreal.stomp_server

import typings.node.{nodeStrings, process, setTimeout}
import typings.sockjsDashClient.sockjsDashClientMod
import typings.stompjs.stompjsMod
import typings.stompjs.stompjsMod.{Client, Message}

import scala.concurrent.Promise
import scala.scalajs.js
import scala.scalajs.js.JSON



object Main extends App {

  def authorize( headers: Map[String, String] ) = {
    true
  }

  val serverHostname = "0.0.0.0"
  val serverPort = 15674
  val serverPath = "/stomp"
  val server = new StompServer( "ShuttleControl/1.0", serverHostname, serverPort, serverPath, authorize, true )

//  println( "type message" )
//
//  process.stdin.setEncoding( "utf-8" )
//
//  process.stdin.on_data( nodeStrings.data,
//    body => {
//      println( "sending..." )
//      server.send( "data", body.toString )
//    } )

  val client = stompjsMod.over( new sockjsDashClientMod.^(s"http://$serverHostname:$serverPort$serverPath") )

  client.connect(js.Dynamic.literal(), frame => {
    println( "connected" )
  } )

}
