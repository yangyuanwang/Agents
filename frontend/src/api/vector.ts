import { request } from './http'
import type {
  VectorAddRequest,
  VectorAddResponse,
  VectorSearchRequest,
  VectorSearchResult
} from '@/types/vector'

/** POST /api/vector/add —— 后端已实现（VectorController#add）。 */
export function addVector(payload: VectorAddRequest): Promise<VectorAddResponse> {
  return request<VectorAddResponse>({
    method: 'post',
    url: '/vector/add',
    data: payload
  })
}

/** POST /api/vector/search —— 后端已实现（VectorController#search）。 */
export function searchVectors(payload: VectorSearchRequest): Promise<VectorSearchResult[]> {
  return request<VectorSearchResult[]>({
    method: 'post',
    url: '/vector/search',
    data: {
      query: payload.query,
      tenantId: payload.tenantId,
      // 后端 topK 受 @Min(1) @Max(100) 约束，越界会直接 400。
      topK: Math.min(100, Math.max(1, payload.topK))
    }
  })
}
