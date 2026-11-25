/**
 * 这些类型描述的是后端尚未提供的能力，仅用于把契约固定下来：
 * 页面调用对应接口时会收到 404，被识别为 pending 并显示"待开发"，
 * 前端不会用假数据填充它们。
 */
import type { VectorSearchResult } from './vector'

export type DocumentStatus = 'pending' | 'parsing' | 'chunking' | 'embedding' | 'completed' | 'failed'

export interface KnowledgeBase {
  id: string
  name: string
  description: string
  documentCount?: number
  chunkCount?: number
  embeddingModel?: string
  collectionName?: string
  createdAt?: string
  updatedAt?: string
}

export interface RagDocument {
  id: string
  knowledgeBaseId: string
  fileName: string
  fileType: 'pdf' | 'docx' | 'txt' | 'md' | 'unknown'
  sizeBytes?: number
  status: DocumentStatus
  chunkCount?: number
  errorMessage?: string
  uploadedAt?: string
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
