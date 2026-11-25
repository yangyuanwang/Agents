<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'

import EndpointTable from '@/components/EndpointTable.vue'
import PageHeader from '@/components/PageHeader.vue'
import PendingNotice from '@/components/PendingNotice.vue'
import SearchResultList from '@/components/SearchResultList.vue'
import StatCard from '@/components/StatCard.vue'
import StatusLight from '@/components/StatusLight.vue'
import { API_ENDPOINTS, availableEndpoints, pendingEndpoints } from '@/api/registry'
import { isApiError } from '@/api/http'
import { searchVectors } from '@/api/vector'
import { fetchDashboardStats } from '@/api/system'
import { useSystemStore } from '@/stores/system'
import { useTenantStore } from '@/stores/tenant'
import { formatClock } from '@/utils/format'
import type { VectorSearchResult } from '@/types/vector'

const system = useSystemStore()
const tenant = useTenantStore()

const available = computed(() => availableEndpoints())
const pending = computed(() => pendingEndpoints())

/** 检索自检：真实调用 POST /api/vector/search，统计数字来自返回值本身。 */
const probe = reactive({ query: '安全评价', topK: 5, running: false, error: '' })
const probeResults = ref<VectorSearchResult[]>([])
const probedAt = ref<string>('')

const probeSummary = computed(() => {
  if (probeResults.value.length === 0) {
    return null
  }
  const scores = probeResults.value.map((item) => item.score).filter((value) => Number.isFinite(value))
  const avg = scores.reduce((sum, value) => sum + value, 0) / (scores.length || 1)
  return {
    hits: probeResults.value.length,
    avgScore: avg,
    topScore: Math.max(...scores),
    bottomScore: Math.min(...scores)
  }
})

/** 统计概览接口目前待开发；万一后端上线了，就把真实返回值原样展示出来。 */
const statsState = ref<'pending' | 'ready' | 'error'>('pending')
const stats = ref<Record<string, number> | null>(null)

async function runProbe() {
  probe.running = true
  probe.error = ''
  try {
    probeResults.value = await searchVectors({
      query: probe.query,
      tenantId: tenant.tenantId,
      topK: probe.topK
    })
    probedAt.value = formatClock(new Date())
  } catch (error) {
    probeResults.value = []
    probe.error = isApiError(error) ? `${error.message}${error.detail ? `：${error.detail}` : ''}` : String(error)
  } finally {
    probe.running = false
  }
}

async function probeStatsEndpoint() {
  try {
    const data = await fetchDashboardStats()
    stats.value = data ?? {}
    statsState.value = 'ready'
  } catch (error) {
    statsState.value = isApiError(error) && error.pending ? 'pending' : 'error'
  }
}

onMounted(() => {
  void system.refresh()
  void probeStatsEndpoint()
})
</script>

<template>
  <div class="app-page">
    <PageHeader
      title="首页 Dashboard"
      description="所有数字都来自真实接口调用：后端状态取自 GET /actuator/health，检索统计取自 POST /api/vector/search。知识库 / 文档 / 对话三项统计接口尚未实现，卡片明确标记为待接入，不做估算。"
    >
      <template #actions>
        <el-button :loading="system.state === 'checking'" @click="system.refresh()">
          <el-icon><Refresh /></el-icon>
          <span>重新检测后端</span>
        </el-button>
      </template>
    </PageHeader>

    <div class="app-grid app-grid--4">
      <StatCard
        label="后端服务"
        :value="system.state === 'online' ? '在线' : system.state === 'offline' ? '不可达' : '检测中'"
        :hint="
          system.lastError
            ? system.lastError
            : `GET /actuator/health${system.latencyMs !== null ? ` · ${system.latencyMs} ms` : ''}`
        "
      />
      <StatCard label="知识库数量" tone="pending" hint="待 GET /api/dashboard/stats 或 GET /api/knowledge-bases" />
      <StatCard label="文档数量" tone="pending" hint="待 GET /api/documents" />
      <StatCard label="对话数量" tone="pending" hint="待 GET /api/conversations" />
    </div>

    <div class="app-grid app-grid--4">
      <StatCard label="已接入接口" :value="available.length" :hint="`共 ${API_ENDPOINTS.length} 项契约`" />
      <StatCard label="待开发接口" :value="pending.length" tone="muted" hint="页面均按真实 404 结果标记" />
      <StatCard
        label="最近一次检索命中"
        :value="probeSummary ? probeSummary.hits : '-'"
        :hint="probedAt ? `${probedAt} · topK=${probe.topK} · tenantId=${tenant.tenantId}` : '尚未执行检索自检'"
      />
      <StatCard
        label="平均相似度"
        :value="probeSummary ? probeSummary.avgScore.toFixed(3) : '-'"
        :hint="probeSummary ? `最高 ${probeSummary.topScore.toFixed(3)} / 最低 ${probeSummary.bottomScore.toFixed(3)}` : '相似度为 Cosine，取值 0..1'"
      />
    </div>

    <div class="app-grid app-grid--2">
      <div class="app-panel">
        <div class="app-panel__title">
          <span>检索连通性自检</span>
          <StatusLight :state="system.state" :text="system.statusText" />
        </div>

        <el-form label-position="top">
          <el-form-item label="查询文本（作为 query 发给后端）">
            <el-input v-model="probe.query" placeholder="例如：危险品库二甲苯最大储量" @keyup.enter="runProbe" />
          </el-form-item>
          <el-form-item label="topK（后端限制 1..100）">
            <el-input-number v-model="probe.topK" :min="1" :max="100" />
            <span class="app-muted" style="margin-left: 12px">
              tenantId = <b class="app-mono">{{ tenant.tenantId }}</b>（在右上角切换）
            </span>
          </el-form-item>
          <el-button type="primary" :loading="probe.running" @click="runProbe">执行检索</el-button>
          <span v-if="probedAt" class="app-muted" style="margin-left: 10px">最近执行 {{ probedAt }}</span>
        </el-form>

        <el-alert
          v-if="probe.error"
          :title="probe.error"
          type="error"
          show-icon
          :closable="false"
          style="margin-top: 14px"
        >
          <template #default>
            <div class="app-muted">
              检索失败通常有两种情况：后端没启动，或 Qdrant / Embedding 服务不可用。
              这一项是真实调用结果，前端不会用假数据顶替。
            </div>
          </template>
        </el-alert>

        <div style="margin-top: 14px">
          <SearchResultList :results="probeResults" :loading="probe.running" :keyword="probe.query" empty-text="执行一次检索，这里会显示 Qdrant 返回的真实片段" />
        </div>
      </div>

      <div class="app-panel">
        <div class="app-panel__title">
          <span>后端与数据链路</span>
          <span v-if="system.checkedAt" class="app-muted">检测时间 {{ formatClock(system.checkedAt) }}</span>
        </div>

        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="服务状态">
            <StatusLight :state="system.state" :text="system.statusText" />
          </el-descriptions-item>
          <el-descriptions-item label="响应耗时">
            {{ system.latencyMs !== null ? `${system.latencyMs} ms` : '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="health.components">
            <span class="app-mono">{{ Object.keys(system.health?.components ?? {}).join(', ') || '未返回组件信息' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="actuator/info">
            <span class="app-mono">{{ JSON.stringify(system.info ?? {}) || '{}' }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="向量库">
            Qdrant（collection 名由后端配置项
            <span class="app-mono">spring.ai.vectorstore.qdrant.collection-name</span> 固定，
            前端无法枚举，需后端提供列表接口）
          </el-descriptions-item>
        </el-descriptions>

        <div style="margin-top: 14px">
          <el-descriptions
            v-if="statsState === 'ready'"
            :column="1"
            border
            size="small"
            title="统计概览（来自 GET /api/dashboard/stats）"
          >
            <el-descriptions-item v-for="(value, key) in stats" :key="key" :label="String(key)">
              {{ value }}
            </el-descriptions-item>
          </el-descriptions>
          <PendingNotice v-else endpoint-key="dashboard.stats" title="统计概览接口" />
        </div>

        <el-alert
          v-if="statsState === 'error'"
          type="warning"
          show-icon
          :closable="false"
          title="统计接口返回了非 404 的错误，请查看后端日志"
          style="margin-top: 12px"
        />
      </div>
    </div>

    <div class="app-panel">
      <div class="app-panel__title">
        <span>接口清单</span>
        <span class="app-muted">共 {{ API_ENDPOINTS.length }} 项：已接入 {{ available.length }}，待开发 {{ pending.length }}</span>
      </div>
      <EndpointTable :items="API_ENDPOINTS" />
    </div>
  </div>
</template>
