/**
 * 接口状态：available = 后端已实现并且前端已真实接入；
 * pending = 后端尚未提供，前端只做契约预留，页面必须显式标记为待开发。
 */
export type ApiStatus = 'available' | 'pending'

export type ApiMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH'

/** 后端 ApiExceptionHandler 返回的错误结构。 */
export interface ErrorPayload {
  timestamp?: string
  error?: string
  message?: string
}

export interface ApiEndpoint {
  key: string
  /** 业务动作名称 */
  name: string
  method: ApiMethod
  path: string
  status: ApiStatus
  /** 契约说明或待开发原因 */
  note: string
  /** 使用该接口的页面 */
  usedBy: string[]
}
