import type { VectorSearchResult } from './vector'

export interface KnowledgeBase {
  id: string
  tenantId: string
  name: string
  description: string | null
  status?: string
  createdBy?: string
  createdAt?: string
  updatedAt?: string
}

export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  createdAt: string
  /** 检索到的引用来源，来自已实现的 POST /api/vector/search。 */
  references?: VectorSearchResult[]
  streaming?: boolean
  error?: string
}

/** 会话列表与历史由后端提供后才为业务事实，当前仅本地暂存。 */
export interface Conversation {
  id: string
  title: string
  tenantId: string
  createdAt: string
  updatedAt: string
  messages: ChatMessage[]
  /** true 表示只存在浏览器 localStorage，后端会话接口待开发。 */
  localOnly: boolean
}

export interface EvaluationCase {
  id: string
  question: string
  /** 期望在 topK 命中结果里出现的片段 id 或关键词，任一命中即算 hit。 */
  expect: string
}

export interface EvaluationCaseResult {
  caseId: string
  question: string
  hit: boolean
  rank: number | null
  latencyMs: number
  topK: number
  results: VectorSearchResult[]
  error?: string
}

export interface Tenant {
  id: string
  name: string
  createdAt?: string
}

export interface AppUser {
  id: string
  username: string
  displayName?: string
  role?: string
  tenantId?: string
}
