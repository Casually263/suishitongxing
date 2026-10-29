export interface StreamEvent { event: string; data: Record<string, unknown> }

/** Incremental UTF-8 and SSE framing, independent of HTTP chunks and CRLF boundaries. */
export async function readSse(stream: ReadableStream<Uint8Array>, onEvent: (event: StreamEvent) => void) {
  const reader = stream.getReader(), decoder = new TextDecoder('utf-8', { fatal: true })
  let pending = '', completed = false
  try {
    while (true) {
      const { value, done } = await reader.read()
      pending += done ? decoder.decode() : decoder.decode(value, { stream: true })
      if (pending.length > 131072) throw new Error('Stream event too large')
      let boundary: RegExpExecArray | null
      while ((boundary = /\r?\n\r?\n/.exec(pending))) {
        const frame = pending.slice(0, boundary.index)
        pending = pending.slice(boundary.index + boundary[0].length)
        let event = 'message'; const data: string[] = []
        for (const line of frame.split(/\r?\n/)) {
          if (line.startsWith('event:')) event = line.slice(6).trim()
          if (line.startsWith('data:')) data.push(line.slice(5).replace(/^ /, ''))
        }
        if (!data.length) continue
        const decoded: unknown = JSON.parse(data.join('\n'))
        if (!decoded || typeof decoded !== 'object' || Array.isArray(decoded)) throw new Error('Invalid stream event')
        const payload = decoded as Record<string, unknown>
        if (event === 'error') throw new Error(typeof payload.msg === 'string' ? payload.msg : 'Chat stream failed')
        onEvent({ event, data: payload })
        if (event === 'done') { completed = true; await reader.cancel(); return }
      }
      if (done) break
    }
    if (!completed) throw new Error('Chat interrupted before confirmation / 对话未完成，请刷新后重试')
  } finally { reader.releaseLock() }
}
