/**
 * 与后端 DTO 一一对应，字段名保持 camelCase 不变：
 * VectorAddRequest / VectorSearchRequest / VectorSearchResult / AddResponse。
 */

/** com.example.rag.dto.VectorAddRequest：四个字段都是 @NotBlank。 */
export interface VectorAddRequest {
  id: string
  content: string
  tenantId: string
  category: string
}

/** VectorController.AddResponse(id, status)。 */
export interface VectorAddResponse {
  id: string
  status: string
}

/** com.example.rag.dto.VectorSearchRequest：topK 受 @Min(1) @Max(100) 约束。 */
export interface VectorSearchRequest {
  query: string
  tenantId: string
  topK: number
}

/** com.example.rag.dto.VectorSearchResult(id, content, score, metadata)。 */
export interface VectorSearchResult {
  id: string
  content: string
  score: number
  metadata: Record<string, unknown>
}

/**
 * /actuator/health 的最小可读结构，实际字段由 Spring Boot 决定。
 * 注意：components 只有在后端 management.endpoint.health.show-details=always 时才会出现，
 * 默认 never 时匿名请求只返回 {status:"UP"}。
 */
export interface HealthResponse {
  status?: string
  groups?: string[]
  components?: Record<string, { status?: string; details?: Record<string, unknown> }>
}
