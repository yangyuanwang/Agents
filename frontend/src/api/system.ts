import { ACTUATOR_BASE_URL, API_BASE_URL, request } from './http'
import type { HealthResponse } from '@/types/vector'
import type { AppUser, Tenant } from '@/types/domain'

/** GET /actuator/health —— 已实现。 */
export function fetchHealth(): Promise<HealthResponse> {
  return request<HealthResponse>({
    method: 'get',
    url: '/health',
    baseURL: ACTUATOR_BASE_URL,
    timeout: 8000,
    silent: true
  })
}

/** GET /actuator/info —— 已暴露，但没有配置 build/git 信息时返回空对象。 */
export function fetchInfo(): Promise<Record<string, unknown>> {
  return request<Record<string, unknown>>({
    method: 'get',
    url: '/info',
    baseURL: ACTUATOR_BASE_URL,
    timeout: 8000,
    silent: true
  })
}

/** GET /api/dashboard/stats —— 待开发，后端没有统计聚合接口。 */
export function fetchDashboardStats(): Promise<Record<string, number>> {
  return request<Record<string, number>>({ method: 'get', url: '/dashboard/stats', silent: true })
}

/** GET /api/users —— 待开发，后端没有 Spring Security 与用户表。 */
export function listUsers(params?: { tenantId?: string }): Promise<AppUser[]> {
  return request<AppUser[]>({ method: 'get', url: '/users', params, silent: true })
}

/** GET /api/tenants —— 待开发，后端只有 tenantId 过滤字段，没有租户 CRUD。 */
export function listTenants(): Promise<Tenant[]> {
  return request<Tenant[]>({ method: 'get', url: '/tenants', silent: true })
}

/** POST /api/users —— 待开发。 */
export function createUser(payload: Partial<AppUser>): Promise<AppUser> {
  return request<AppUser>({ method: 'post', url: '/users', data: payload, silent: true })
}

/** DELETE /api/users/{id} —— 待开发。 */
export function deleteUser(id: string): Promise<void> {
  return request<void>({ method: 'delete', url: `/users/${id}`, silent: true })
}

/** POST /api/tenants —— 待开发。 */
export function createTenant(payload: Partial<Tenant>): Promise<Tenant> {
  return request<Tenant>({ method: 'post', url: '/tenants', data: payload, silent: true })
}

/** DELETE /api/tenants/{id} —— 待开发。 */
export function deleteTenant(id: string): Promise<void> {
  return request<void>({ method: 'delete', url: `/tenants/${id}`, silent: true })
}

export const systemApiBase = API_BASE_URL
