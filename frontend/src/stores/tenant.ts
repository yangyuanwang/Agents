import { ref, watch } from 'vue'
import { defineStore } from 'pinia'

/**
 * tenantId 是后端 POST /api/vector/search 的必填过滤参数
 * （VectorService 拼成 filterExpression "tenantId == '...'"）。
 * 这里的默认值只是输入框初值，不代表系统里真的存在这个租户——
 * 租户 CRUD 接口 GET /api/tenants 仍是待开发状态。
 */
export const DEFAULT_TENANT_ID = 'default'

const TENANT_KEY = 'rag-admin.tenantId'
const CATEGORY_KEY = 'rag-admin.category'

export const useTenantStore = defineStore('tenant', () => {
  const tenantId = ref(localStorage.getItem(TENANT_KEY) || DEFAULT_TENANT_ID)
  const category = ref(localStorage.getItem(CATEGORY_KEY) || '')

  watch(tenantId, (value) => localStorage.setItem(TENANT_KEY, value))
  watch(category, (value) => localStorage.setItem(CATEGORY_KEY, value))

  return { tenantId, category, setTenantId: (value: string) => (tenantId.value = value) }
})
