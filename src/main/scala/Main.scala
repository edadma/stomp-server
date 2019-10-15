package xyz.hyperreal.stomp_server

import typings.node.{nodeStrings, process, setTimeout}
import typings.sockjsDashClient.sockjsDashClientMod
import typings.stompjs.stompjsMod
import typings.stompjs.stompjsMod.{Client, Message}

import scala.concurrent.Promise
import scala.scalajs.js


object Main extends App {

  def authorize( headers: Map[String, String] ) = {
    true
  }

  val server = new StompServer( "ShuttleControl/1.0", "0.0.0.0", 15674, "/stomp", authorize, true )

//  println( "type message" )
//
//  process.stdin.setEncoding( "utf-8" )
//
//  process.stdin.on_data( nodeStrings.data,
//    body => {
//      println( "sending..." )
//      server.send( "data", body.toString )
//    } )

  val serverHostname = "0.0.0.0"
  val serverPort = 15674
  var client1: Client = null
  var client2: Client = null

//  setTimeout(
//    _ => {
      client1 = stompjsMod.over( new sockjsDashClientMod.^(s"http://$serverHostname:$serverPort/stomp") )
      println( js.Object.keys(client1) )
//      setTimeout(
//        _ => {
          client2 = stompjsMod.over(new sockjsDashClientMod.^(s"http://$serverHostname:$serverPort/stomp"))

          setTimeout(
            _ => {
              println( js.Object.keys(client1) )
              client1.connect(js.Dynamic.literal(), frame => {
                println(frame)
                client2.connect(js.Dynamic.literal(), frame => {
                  client2.subscribe("data", (message: Message) => {
                    println(message)
                  }, js.Dynamic.literal())
                  client1.send("data")
                })
              })
            }, 500 )
//        }, 500 )
//    }, 500 )

}
