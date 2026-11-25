import { API_BASE_URL, request } from './http'
import { streamSse, type SseEvent } from '@/utils/sse'
import type { Conversation } from '@/types/domain'

/**
 * 对话生成接口。后端目前没有 ChatController，也没有把 application.yml 里的
 * ChatModel（ollama / openai）接到任何 REST 入口，所以这些调用会返回 404，
 * 页面据此显示"待开发"。契约按 Spring 常用写法预留，后端实现时对齐即可。
 */
export interface ChatStreamPayload {
  conversationId: string
  message: string
  tenantId: string
  topK: number
}

export const CHAT_STREAM_URL = `${API_BASE_URL}/chat/stream`

export function streamChat(
  payload: ChatStreamPayload,
  handlers: { onEvent: (event: SseEvent) => void; onOpen?: (status: number, type: string) => void },
  signal?: AbortSignal
): Promise<void> {
  return streamSse({ url: CHAT_STREAM_URL, body: payload, signal, ...handlers })
}

/** GET /api/conversations —— 待开发。 */
export function listConversations(tenantId: string): Promise<Conversation[]> {
  return request<Conversation[]>({ method: 'get', url: '/conversations', params: { tenantId }, silent: true })
}

/** GET /api/conversations/{id} —— 待开发。 */
export function fetchConversation(id: string): Promise<Conversation> {
  return request<Conversation>({ method: 'get', url: `/conversations/${id}`, silent: true })
}

/** DELETE /api/conversations/{id} —— 待开发。 */
export function removeConversation(id: string): Promise<void> {
  return request<void>({ method: 'delete', url: `/conversations/${id}`, silent: true })
}
