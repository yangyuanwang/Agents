import { request } from './http'
import type { KnowledgeBase } from '@/types/domain'

export interface KnowledgeBasePage {
  items: KnowledgeBase[]
  total: number
  page: number
  size: number
}

/** GET /api/knowledge-bases?tenantId=... returns the server page envelope. */
export function listKnowledgeBases(params: { tenantId: string; page?: number; size?: number }): Promise<KnowledgeBasePage> {
  return request<KnowledgeBasePage>({
    method: 'get',
    url: '/knowledge-bases',
    params: { page: 1, size: 100, ...params },
    silent: true
  })
}

/** Development identity fields are required until authentication supplies them. */
export interface KnowledgeBaseCreatePayload {
  tenantId: string
  createdBy: string
  name: string
  description: string | null
}

export function createKnowledgeBase(payload: KnowledgeBaseCreatePayload): Promise<KnowledgeBase> {
  return request<KnowledgeBase>({ method: 'post', url: '/knowledge-bases', data: payload, silent: true })
}

/** PUT /api/knowledge-bases/{id}. */
export function updateKnowledgeBase(
  id: string,
  payload: { name: string; description: string | null }
): Promise<KnowledgeBase> {
  return request<KnowledgeBase>({ method: 'put', url: `/knowledge-bases/${id}`, data: payload, silent: true })
}

/** DELETE /api/knowledge-bases/{id}. */
export function deleteKnowledgeBase(id: string): Promise<void> {
  return request<void>({ method: 'delete', url: `/knowledge-bases/${id}`, silent: true })
}
