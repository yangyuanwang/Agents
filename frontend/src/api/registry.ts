import type { ApiEndpoint } from '@/types/api'

/**
 * 接口清单的唯一来源：后端加接口时改这里，Dashboard 与页面的状态标记都跟着变。
 * available 判定依据是 src/main/java/com/example/rag/controller/VectorController.java
 * 与 application.yml 里暴露的 actuator 端点。
 */
export const API_ENDPOINTS: ApiEndpoint[] = [
  {
    key: 'vector.add',
    name: '写入向量片段',
    method: 'POST',
    path: '/api/vector/add',
    status: 'available',
    note: '入参 id/content/tenantId/category 全部 @NotBlank，返回 {id, status:"stored"}，状态码 201。',
    usedBy: ['文档管理', '向量写入']
  },
  {
    key: 'vector.search',
    name: '相似度检索',
    method: 'POST',
    path: '/api/vector/search',
    status: 'available',
    note: '入参 query/tenantId/topK(1..100)，返回 [{id, content, score, metadata}]；租户过滤在 Qdrant 侧执行。',
    usedBy: ['切片管理', 'AI 对话（引用来源）', 'RAG 评估']
  },
  {
    key: 'system.health',
    name: '服务健康检查',
    method: 'GET',
    path: '/actuator/health',
    status: 'available',
    note: 'application.yml 暴露 health,info。',
    usedBy: ['首页 Dashboard', '顶部导航状态灯']
  },
  {
    key: 'system.info',
    name: '构建信息',
    method: 'GET',
    path: '/actuator/info',
    status: 'available',
    note: '默认无 build/git 信息时返回空对象，页面按空值处理。',
    usedBy: ['首页 Dashboard']
  },
  {
    key: 'chat.stream',
    name: '对话生成（SSE）',
    method: 'POST',
    path: '/api/chat/stream',
    status: 'pending',
    note: '后端没有 ChatController，也没有把 ChatClient/ChatModel 接到任何 REST 入口。',
    usedBy: ['AI 对话']
  },
  {
    key: 'conversation.list',
    name: '会话列表 / 历史',
    method: 'GET',
    path: '/api/conversations',
    status: 'pending',
    note: '需要后端持久化会话与消息；当前对话记录只存在浏览器 localStorage。',
    usedBy: ['AI 对话']
  },
  {
    key: 'knowledge.list',
    name: '知识库列表',
    method: 'GET',
    path: '/api/knowledge-bases',
    status: 'available',
    note: 'GET /api/knowledge-bases?tenantId={uuid}&page=1&size=20 返回 {items,total,page,size}；开发期租户输入，不代表可信授权。',
    usedBy: ['知识库管理', '首页 Dashboard']
  },
  {
    key: 'knowledge.create',
    name: '创建知识库',
    method: 'POST',
    path: '/api/knowledge-bases',
    status: 'available',
    note: 'POST 请求 tenantId/createdBy/name/description，成功 201 并返回知识库 DTO；tenantId/createdBy 为临时开发上下文。',
    usedBy: ['知识库管理']
  },
  {
    key: 'knowledge.update',
    name: '编辑知识库',
    method: 'PUT',
    path: '/api/knowledge-bases/{id}',
    status: 'available',
    note: 'PUT 请求 name/description，返回更新后的知识库 DTO；已删除或不存在返回 404。',
    usedBy: ['知识库管理']
  },
  {
    key: 'knowledge.delete',
    name: '删除知识库',
    method: 'DELETE',
    path: '/api/knowledge-bases/{id}',
    status: 'available',
    note: 'DELETE 返回 204；执行逻辑删除并保留关联数据，重复删除或不存在返回 404。',
    usedBy: ['知识库管理']
  },
  {
    key: 'document.upload',
    name: '文件上传并创建 FileObject',
    method: 'POST',
    path: '/api/files/upload',
    status: 'available',
    note: 'multipart/form-data：knowledgeBaseId、file；201 返回 FileObject DTO。上传仅保存文件对象，不代表 DocumentVersion 已创建。',
    usedBy: ['文档管理']
  },
  {
    key: 'document.list',
    name: '文档列表与处理状态',
    method: 'GET',
    path: '/api/documents',
    status: 'available',
    note: 'GET /api/documents?knowledgeBaseId&page&size&keyword；返回 {items,total,page,size}，page 从 1 开始。',
    usedBy: ['文档管理', '首页 Dashboard']
  },
  {
    key: 'document.delete',
    name: '删除文档',
    method: 'DELETE',
    path: '/api/documents/{id}',
    status: 'available',
    note: 'DELETE /api/documents/{id}?knowledgeBaseId=...；成功返回 204。',
    usedBy: ['文档管理']
  },
  {
    key: 'document.create',
    name: '创建文档',
    method: 'POST',
    path: '/api/documents',
    status: 'available',
    note: '请求 {knowledgeBaseId,displayName}；成功 201 返回 Document DTO。',
    usedBy: ['文档管理']
  },
  {
    key: 'document.detail',
    name: '文档详情',
    method: 'GET',
    path: '/api/documents/{id}',
    status: 'available',
    note: '查询参数 knowledgeBaseId；返回 Document DTO 及服务端当前版本信息。',
    usedBy: ['文档管理']
  },
  {
    key: 'document.update',
    name: '编辑文档',
    method: 'PUT',
    path: '/api/documents/{id}',
    status: 'available',
    note: '查询参数 knowledgeBaseId，请求 {displayName}；返回更新后的 Document DTO。',
    usedBy: ['文档管理']
  },
  {
    key: 'document.version.list',
    name: '文档版本列表',
    method: 'GET',
    path: '/api/documents/{id}/versions',
    status: 'available',
    note: '返回该文档版本 DTO 数组；版本号及状态来自服务端。',
    usedBy: ['文档管理']
  },
  {
    key: 'document.version.detail',
    name: '文档版本详情',
    method: 'GET',
    path: '/api/documents/{id}/versions/{versionId}',
    status: 'available',
    note: '返回版本 DTO，包括 fileObjectId、SHA256、大小、原文件名和状态。',
    usedBy: ['文档管理']
  },
  {
    key: 'document.version.create',
    name: '创建文档版本',
    method: 'POST',
    path: '/api/documents/{id}/versions',
    status: 'available',
    note: '请求 {fileObjectId,originalFilename}；成功 201，状态为 UPLOADED，版本号由后端生成。',
    usedBy: ['文档管理']
  },
  {
    key: 'document.version.activate',
    name: '切换活动版本',
    method: 'PUT',
    path: '/api/documents/{id}/active-version',
    status: 'available',
    note: '请求 {versionId}；仅 COMPLETED 版本可切换，返回更新后的 Document DTO。',
    usedBy: ['文档管理']
  },
  {
    key: 'chunk.list',
    name: '按文档分页查切片',
    method: 'GET',
    path: '/api/documents/{id}/chunks',
    status: 'pending',
    note: '当前切片视图基于 POST /api/vector/search 的真实结果，缺少按文档遍历与分页能力。',
    usedBy: ['切片管理']
  },
  {
    key: 'chunk.delete',
    name: '删除 / 停用切片',
    method: 'DELETE',
    path: '/api/chunks/{id}',
    status: 'pending',
    note: '待开发。',
    usedBy: ['切片管理']
  },
  {
    key: 'evaluation.run',
    name: '评估任务（指标）',
    method: 'POST',
    path: '/api/evaluations',
    status: 'pending',
    note: 'Faithfulness / Answer Relevancy / 幻觉风险需要 LLM 判定接口。',
    usedBy: ['RAG 评估']
  },
  {
    key: 'evaluation.list',
    name: '评估历史',
    method: 'GET',
    path: '/api/evaluations',
    status: 'pending',
    note: '待开发。',
    usedBy: ['RAG 评估']
  },
  {
    key: 'dashboard.stats',
    name: '统计概览',
    method: 'GET',
    path: '/api/dashboard/stats',
    status: 'pending',
    note: '知识库数 / 文档数 / 对话数 / 检索次数需要后端聚合，前端不做估算。',
    usedBy: ['首页 Dashboard']
  },
  {
    key: 'tenant.list',
    name: '租户管理',
    method: 'GET',
    path: '/api/tenants',
    status: 'pending',
    note: '后端已有 tenantId 过滤字段，但没有租户 CRUD 与鉴权。',
    usedBy: ['租户管理']
  },
  {
    key: 'user.list',
    name: '用户与权限',
    method: 'GET',
    path: '/api/users',
    status: 'pending',
    note: '后端无 Spring Security / 登录接口，前端也没有伪造 token。',
    usedBy: ['用户管理']
  },
  {
    key: 'user.create',
    name: '新建用户',
    method: 'POST',
    path: '/api/users',
    status: 'pending',
    note: '待后端定义用户表与角色；前端按钮会真实发起请求并收到 404。',
    usedBy: ['用户管理']
  },
  {
    key: 'user.delete',
    name: '删除用户',
    method: 'DELETE',
    path: '/api/users/{id}',
    status: 'pending',
    note: '待开发。',
    usedBy: ['用户管理']
  },
  {
    key: 'tenant.create',
    name: '新建租户',
    method: 'POST',
    path: '/api/tenants',
    status: 'pending',
    note: '租户隔离目前是 VectorService 里的 tenantId 元数据过滤条件，没有租户实体与配额接口。',
    usedBy: ['租户管理']
  },
  {
    key: 'tenant.delete',
    name: '删除租户',
    method: 'DELETE',
    path: '/api/tenants/{id}',
    status: 'pending',
    note: '待开发，需同步清理该 tenantId 下的向量。',
    usedBy: ['租户管理']
  }
]

const ENDPOINT_INDEX = new Map(API_ENDPOINTS.map((item) => [item.key, item]))

export function endpoint(key: string): ApiEndpoint | undefined {
  return ENDPOINT_INDEX.get(key)
}

export function availableEndpoints(): ApiEndpoint[] {
  return API_ENDPOINTS.filter((item) => item.status === 'available')
}

export function pendingEndpoints(): ApiEndpoint[] {
  return API_ENDPOINTS.filter((item) => item.status === 'pending')
}
