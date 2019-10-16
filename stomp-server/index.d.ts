export = StompServer;

declare class StompServer {

    constructor( name: string, hostname: string, port: number, path: string, authorized: (headers: any) => boolean, debug: boolean );

    send( queue: string, body: string, contentType: string ): void;

    queues(): string[]

}
