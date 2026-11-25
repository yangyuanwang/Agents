import { request } from './http'
import type { KnowledgeBase } from '@/types/domain'

/**
 * 知识库 CRUD 全部待开发：后端只有一个固定 collection（application.yml 里
 * spring.ai.vectorstore.qdrant.collection-name=knowledge_base），
 * 没有知识库维度的表和接口。这里保留调用是为了让页面显示真实状态，
 * 而不是摆一组假数据。
 */

/** GET /api/knowledge-bases —— 待开发。 */
export function listKnowledgeBases(): Promise<KnowledgeBase[]> {
  return request<KnowledgeBase[]>({ method: 'get', url: '/knowledge-bases', silent: true })
}

/** POST /api/knowledge-bases —— 待开发。 */
export function createKnowledgeBase(payload: {
  name: string
  description: string
}): Promise<KnowledgeBase> {
  return request<KnowledgeBase>({ method: 'post', url: '/knowledge-bases', data: payload, silent: true })
}

/** PUT /api/knowledge-bases/{id} —— 待开发。 */
export function updateKnowledgeBase(
  id: string,
  payload: { name: string; description: string }
): Promise<KnowledgeBase> {
  return request<KnowledgeBase>({ method: 'put', url: `/knowledge-bases/${id}`, data: payload, silent: true })
}

/** DELETE /api/knowledge-bases/{id} —— 待开发。 */
export function deleteKnowledgeBase(id: string): Promise<void> {
  return request<void>({ method: 'delete', url: `/knowledge-bases/${id}`, silent: true })
}
