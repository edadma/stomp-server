import * as StompServer from '../target/scala-2.12/scalajs-bundler/main/stomp-server-fastopt.js'
import * as StompJS from 'stompjs'
import * as SockJS from 'sockjs-client'

const authorize = () => {return true}

const stomp = new StompServer( '0.0.0.0', 15674, '/stomp', authorize )

const socket = new SockJS( 'http://127.0.0.1:15674/stomp' )
const client = StompJS.over( socket )

client.connect(
    {
        'accept-version': '1.2'
    },
    frame => {
        console.log( 'connected' )

        client.subscribe(
            'data',
            message => {
                console.log( 'message', message )
            },
            { Authorization: 'Bearer anything' }
        )
    }
)
