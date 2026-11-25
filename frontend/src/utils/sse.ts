import { ApiError } from '@/api/http'

export interface SseEvent {
  event: string
  data: string
}

export interface StreamHandlers {
  /** 每收到一个完整 SSE 事件回调一次。 */
  onEvent: (event: SseEvent) => void
  /** 收到响应头后回调，用于尽早切换 UI 状态。 */
  onOpen?: (status: number, contentType: string) => void
}

const PENDING_STATUS = [404, 405, 415, 501]

/** 把一个 SSE 事件块（已按空行切好）解析成 event/data。 */
export function parseSseBlock(block: string): SseEvent | null {
  const lines = block.split(/\r?\n/)
  let event = 'message'
  const dataLines: string[] = []

  for (const line of lines) {
    if (!line || line.startsWith(':')) {
      continue
    }
    const separator = line.indexOf(':')
    const field = separator === -1 ? line : line.slice(0, separator)
    let value = separator === -1 ? '' : line.slice(separator + 1)
    if (value.startsWith(' ')) {
      value = value.slice(1)
    }
    if (field === 'event') {
      event = value || 'message'
    } else if (field === 'data') {
      dataLines.push(value)
    }
  }

  if (dataLines.length === 0) {
    return null
  }
  return { event, data: dataLines.join('\n') }
}

/**
 * 用 fetch + ReadableStream 读取 SSE。
 * 选 fetch 而不是 EventSource，是因为后端对话接口需要 POST 一个 JSON 请求体，
 * EventSource 只能发 GET。断线、取消和错误都由调用方通过 signal / 异常处理。
 */
export async function streamSse(
  options: {
    url: string
    body?: unknown
    headers?: Record<string, string>
    signal?: AbortSignal
  } & StreamHandlers
): Promise<void> {
  const { url, body, headers, signal, onEvent, onOpen } = options

  let response: Response
  try {
    response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...headers
      },
      body: body === undefined ? undefined : JSON.stringify(body),
      signal
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') {
      throw new ApiError('生成已中断', { pending: false })
    }
    throw new ApiError('无法连接对话接口', {
      detail: '请确认后端服务与代理地址可用',
      pending: false
    })
  }

  const contentType = response.headers.get('content-type') ?? ''
  onOpen?.(response.status, contentType)

  if (!response.ok) {
    const pending = PENDING_STATUS.includes(response.status)
    let detail = ''
    try {
      const text = await response.text()
      detail = text.slice(0, 200)
    } catch {
      detail = ''
    }
    throw new ApiError(
      pending ? `对话接口尚未实现（HTTP ${response.status}）` : `对话接口返回 HTTP ${response.status}`,
      { status: response.status, pending, detail }
    )
  }

  if (!response.body) {
    throw new ApiError('当前环境不支持流式响应体', { pending: false })
  }

  if (contentType && !contentType.includes('text/event-stream')) {
    throw new ApiError('响应不是 SSE 事件流', {
      detail: `Content-Type: ${contentType}`,
      pending: false
    })
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  for (;;) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    // 统一换行，避免 \r\n 被切成两个数据块时漏掉分隔符。
    buffer = buffer.replace(/\r\n/g, '\n')

    // SSE 以空行分隔事件，末尾不完整的一段留在 buffer 里等下一个数据块。
    let boundary = buffer.indexOf('\n\n')
    while (boundary !== -1) {
      const block = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      const parsed = parseSseBlock(block)
      if (parsed) {
        onEvent(parsed)
      }
      boundary = buffer.indexOf('\n\n')
    }
  }


  const tail = buffer.trim()
  if (tail) {
    const parsed = parseSseBlock(tail)
    if (parsed) {
      onEvent(parsed)
    }
  }
}
