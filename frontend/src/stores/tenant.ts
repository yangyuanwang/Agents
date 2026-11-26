import { ref, watch } from 'vue'
import { defineStore } from 'pinia'

/** The unauthenticated development context must contain actual database UUIDs. */
export const DEFAULT_TENANT_ID = ''
export const DEFAULT_CREATED_BY = ''
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

const TENANT_KEY = 'rag-admin.tenantId'
const CREATED_BY_KEY = 'rag-admin.createdBy'
const CATEGORY_KEY = 'rag-admin.category'

export function isUuid(value: string | null | undefined): value is string {
  return Boolean(value && UUID_PATTERN.test(value.trim()))
}

function readStoredUuid(key: string, fallback: string): string {
  const stored = localStorage.getItem(key)?.trim() ?? ''
  if (isUuid(stored)) return stored
  if (stored) localStorage.removeItem(key)
  return fallback
}

export const useTenantStore = defineStore('tenant', () => {
  const tenantId = ref(readStoredUuid(TENANT_KEY, DEFAULT_TENANT_ID))
  const createdBy = ref(readStoredUuid(CREATED_BY_KEY, DEFAULT_CREATED_BY))
  const category = ref(localStorage.getItem(CATEGORY_KEY) || '')

  watch(tenantId, (value) => localStorage.setItem(TENANT_KEY, value))
  watch(createdBy, (value) => localStorage.setItem(CREATED_BY_KEY, value))
  watch(category, (value) => localStorage.setItem(CATEGORY_KEY, value))

  function setTenantId(value: string): boolean {
    const normalized = value.trim()
    if (normalized && !isUuid(normalized)) return false
    tenantId.value = normalized
    return true
  }

  function setCreatedBy(value: string): boolean {
    const normalized = value.trim()
    if (normalized && !isUuid(normalized)) return false
    createdBy.value = normalized
    return true
  }

  return { tenantId, createdBy, category, setTenantId, setCreatedBy }
})
