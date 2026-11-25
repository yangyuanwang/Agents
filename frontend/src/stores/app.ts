import { ref, watch } from 'vue'
import { defineStore } from 'pinia'

const STORAGE_KEY = 'rag-admin.ui'

interface UiState {
  sidebarCollapsed: boolean
}

function load(): UiState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed = JSON.parse(raw) as Partial<UiState>
      return { sidebarCollapsed: Boolean(parsed.sidebarCollapsed) }
    }
  } catch {
    // 配置损坏就退回默认值，不影响使用
  }
  return { sidebarCollapsed: false }
}

export const useAppStore = defineStore('app', () => {
  const sidebarCollapsed = ref(load().sidebarCollapsed)

  function toggleSidebar() {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }

  watch(
    sidebarCollapsed,
    (value) => {
      localStorage.setItem(STORAGE_KEY, JSON.stringify({ sidebarCollapsed: value } satisfies UiState))
    },
    { flush: 'post' }
  )

  return { sidebarCollapsed, toggleSidebar }
})
