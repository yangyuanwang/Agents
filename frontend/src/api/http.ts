import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'

import type { ErrorPayload } from '@/types/api'

declare module 'axios' {
  export interface AxiosRequestConfig {
    /** 页面自己处理错误时置为 true，不弹全局提示。 */
    silent?: boolean
  }
}

export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'
export const ACTUATOR_BASE_URL = import.meta.env.VITE_ACTUATOR_BASE_URL || '/actuator'

/** 后端还没实现这些路由时，Spring 返回的就是这几个状态码，视为"待开发"而不是故障。 */
const PENDING_STATUS = [404, 405, 415, 501]

export class ApiError extends Error {
  readonly status?: number
  readonly pending: boolean
  readonly detail?: string

  constructor(message: string, options: { status?: number; pending?: boolean; detail?: string } = {}) {
    super(message)
    this.name = 'ApiError'
    this.status = options.status
    this.pending = options.pending ?? false
    this.detail = options.detail
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

/** 接口未实现：页面据此渲染"待开发"面板，而不是当成报错或编造数据。 */
export function isPendingError(error: unknown): boolean {
  return isApiError(error) && error.pending
}

function toApiError(error: unknown): ApiError {
  if (error instanceof ApiError) {
    return error
  }

  if (axios.isCancel(error)) {
    return new ApiError('请求已取消', { pending: false })
  }

  if (error instanceof AxiosError) {
    const status = error.response?.status
    const payload = (error.response?.data ?? undefined) as ErrorPayload | undefined
    const pending = status !== undefined && PENDING_STATUS.includes(status)

    if (status === undefined) {
      const reason = error.code === 'ECONNABORTED' ? '请求超时' : '网络不可达'
      return new ApiError(`无法连接后端服务（${reason}）`, {
        detail: '请确认 Spring Boot 已启动，且 .env.development 的 VITE_PROXY_TARGET 指向正确端口',
        pending: false
      })
    }

    return new ApiError(payload?.message || payload?.error || error.message || '请求失败', {
      status,
      pending,
      detail: payload?.timestamp
    })
  }

  return new ApiError(error instanceof Error ? error.message : '未知错误')
}

export const http = axios.create({
  baseURL: API_BASE_URL,
  timeout: 20000,
  headers: { 'Content-Type': 'application/json' }
})

/**
 * 统一异常处理：后端 ApiExceptionHandler 返回 {timestamp, error, message}，
 * 这里收敛成 ApiError，页面只需要判断 pending。
 */
http.interceptors.response.use(
  (response) => response,
  (error: unknown) => {
    const apiError = toApiError(error)
    const silent = (error instanceof AxiosError ? error.config?.silent : false) ?? false

    if (!silent && !apiError.pending) {
      ElMessage.error(apiError.detail ? `${apiError.message}：${apiError.detail}` : apiError.message)
    }

    return Promise.reject(apiError)
  }
)

/** 发请求并直接拿业务数据；异常一律以 ApiError 抛出。 */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await http.request<T>(config)
  return response.data
}

export { toApiError }
