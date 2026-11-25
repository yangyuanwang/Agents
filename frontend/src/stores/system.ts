import { defineStore } from 'pinia'
import { computed, ref } from 'vue'

import { fetchHealth, fetchInfo } from '@/api/system'
import { isApiError } from '@/api/http'
import type { HealthResponse } from '@/types/vector'

export type BackendState = 'checking' | 'online' | 'offline' | 'unknown'

/**
 * 后端连通性状态：真实调用 GET /actuator/health。
 * 顶部导航和 Dashboard 共用一份，避免每个页面各发一次请求。
 */
export const useSystemStore = defineStore('system', () => {
  const state = ref<BackendState>('checking')
  const health = ref<HealthResponse | null>(null)
  const info = ref<Record<string, unknown> | null>(null)
  const latencyMs = ref<number | null>(null)
  const checkedAt = ref<string | null>(null)
  const lastError = ref<string | null>(null)

  const statusText = computed<string>(() => {
    if (state.value === 'online') {
      return health.value?.status ? `在线（${health.value.status}）` : '在线'
    }
    if (state.value === 'offline') {
      return '不可达'
    }
    if (state.value === 'checking') {
      return '检测中'
    }
    return '未知'
  })

  async function refresh(): Promise<void> {
    state.value = 'checking'
    const start = performance.now()
    try {
      health.value = await fetchHealth()
      latencyMs.value = Math.round(performance.now() - start)
      state.value = health.value?.status === 'DOWN' ? 'offline' : 'online'
      lastError.value = null
      checkedAt.value = new Date().toISOString()
      try {
        info.value = await fetchInfo()
      } catch {
        // info 端点内容为空或未配置时不影响在线判定
        info.value = {}
      }
    } catch (error) {
      latencyMs.value = Math.round(performance.now() - start)
      health.value = null
      state.value = 'offline'
      lastError.value = isApiError(error) ? error.message : String(error)
      checkedAt.value = new Date().toISOString()
    }
  }

  return { state, health, info, latencyMs, checkedAt, lastError, statusText, refresh }
})
