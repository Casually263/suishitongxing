import { describe, expect, it } from 'vitest'
import { readSse, type StreamEvent } from '../src/api/sse'

function stream(text: string, size = 1) { const bytes = new TextEncoder().encode(text); return new ReadableStream<Uint8Array>({ start(controller) { for (let offset = 0; offset < bytes.length; offset += size) controller.enqueue(bytes.slice(offset, offset + size)); controller.close() } }) }
describe('AI SSE framing and confirmation', () => {
  it('decodes Chinese across every UTF-8 byte boundary and CRLF', async () => { const events: StreamEvent[] = []; await readSse(stream('event: delta\r\ndata: {"text":"你好，小满。"}\r\n\r\nevent: done\r\ndata: {"id":"1"}\r\n\r\n'), event => events.push(event)); expect(events.map(event => event.event)).toEqual(['delta', 'done']); expect(events[0].data.text).toBe('你好，小满。') })
  it('supports comments and multiline JSON data', async () => { const events: StreamEvent[] = []; await readSse(stream(': heartbeat\n\nevent: meta\ndata: {"mode":\ndata: "SCRIPT"}\n\nevent: done\ndata: {}\n\n', 7), event => events.push(event)); expect(events[0].data.mode).toBe('SCRIPT') })
  it('keeps reset distinct so a fallback replaces partial model text', async () => { const events: StreamEvent[] = []; await readSse(stream('event: delta\ndata: {"text":"partial"}\n\nevent: reset\ndata: {"mode":"SCRIPT"}\n\nevent: delta\ndata: {"text":"fallback"}\n\nevent: done\ndata: {}\n\n', 13), event => events.push(event)); expect(events.map(event => event.event)).toEqual(['delta', 'reset', 'delta', 'done']) })
  it('rejects an interrupted stream instead of pretending it finished', async () => { await expect(readSse(stream('event: delta\ndata: {"text":"partial"}\n\n'), () => {})).rejects.toThrow('interrupted') })
  it('reports server error events', async () => { await expect(readSse(stream('event: error\ndata: {"msg":"Service unavailable"}\n\n'), () => {})).rejects.toThrow('Service unavailable') })
  it('rejects malformed JSON and non-object event data', async () => { for (const data of ['{bad}', '[]', 'null']) await expect(readSse(stream(`event: delta\ndata: ${data}\n\n`), () => {})).rejects.toThrow() })
})
