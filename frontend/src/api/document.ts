import { request } from './http'
import type { RagDocument } from '@/types/domain'

/**
 * 文档上传 / 解析 / 分块 / 向量化状态：后端没有 multipart 接口，也没有
 * PDF、Word 解析器与分块器，所以以下调用都会 404，页面标记为待开发。
 * 唯一已实现的能力是 POST /api/vector/add，可以手工写入单个片段。
 */

/** POST /api/documents/upload —— 待开发。 */
export function uploadDocument(options: {
  file: File
  knowledgeBaseId: string
  tenantId: string
  category: string
}): Promise<RagDocument> {
  const form = new FormData()
  form.append('file', options.file)
  form.append('knowledgeBaseId', options.knowledgeBaseId)
  form.append('tenantId', options.tenantId)
  form.append('category', options.category)

  return request<RagDocument>({
    method: 'post',
    url: '/documents/upload',
    data: form,
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000,
    silent: true
  })
}

/** GET /api/documents —— 待开发（文档列表与处理状态）。 */
export function listDocuments(params?: { knowledgeBaseId?: string; status?: string }): Promise<RagDocument[]> {
  return request<RagDocument[]>({ method: 'get', url: '/documents', params, silent: true })
}

/** DELETE /api/documents/{id} —— 待开发。 */
export function deleteDocument(id: string): Promise<void> {
  return request<void>({ method: 'delete', url: `/documents/${id}`, silent: true })
}

/** GET /api/documents/{id}/status —— 待开发（解析 / 分块 / 向量化进度）。 */
export function fetchDocumentStatus(id: string): Promise<RagDocument> {
  return request<RagDocument>({ method: 'get', url: `/documents/${id}/status`, silent: true })
}

/** GET /api/documents/{id}/chunks —— 待开发（按文档遍历切片）。 */
export function listDocumentChunks(id: string, params?: { page?: number; size?: number }): Promise<unknown[]> {
  return request<unknown[]>({ method: 'get', url: `/documents/${id}/chunks`, params, silent: true })
}
