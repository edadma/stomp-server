export class StompServer {
  constructor(
    name: string,
    hostname: string,
    port: number,
    path: string,
    connectAuthorize: (headers: any) => boolean,
    subscribeAuthorize: (headers: any) => boolean,
    debug: boolean
  )

  send(queue: string, body: string, contentType?: string): void

  queues(): string[]
}