export = StompServer;

declare class StompServer {

    constructor( name: string, hostname: string, port: number, path: string, authorized: Any => boolean, debug: boolean = false );

    send( queue: string, body: string, contentType: string = 'text/plain' ): void;

}