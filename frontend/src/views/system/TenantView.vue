<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import PageHeader from '@/components/PageHeader.vue'
import PendingNotice from '@/components/PendingNotice.vue'
import { createTenant, deleteTenant, listTenants } from '@/api/system'
import { isApiError } from '@/api/http'
import { searchVectors } from '@/api/vector'
import { useTenantStore } from '@/stores/tenant'
import { formatScore } from '@/utils/format'
import type { Tenant } from '@/types/domain'

type LoadState = 'loading' | 'ready' | 'pending' | 'error'

interface ProbeRow {
  tenantId: string
  query: string
  hits: number
  topScore: number | null
  note: string
}

const tenant = useTenantStore()

const state = ref<LoadState>('loading')
const tenants = ref<Tenant[]>([])
const detail = ref('')

const probe = reactive({ query: '安全', topK: 10 })
const probing = ref(false)
const probes = ref<ProbeRow[]>([])

const form = reactive({ id: '', name: '' })
const saving = ref(false)
const saveResult = ref<{ ok: boolean; text: string } | null>(null)

async function load() {
  state.value = 'loading'
  detail.value = ''
  try {
    tenants.value = await listTenants()
    state.value = 'ready'
  } catch (error) {
    if (isApiError(error) && error.pending) {
      state.value = 'pending'
      detail.value = `${error.status ?? '-'} ${error.message}`
    } else {
      state.value = 'error'
      detail.value = isApiError(error) ? error.message : String(error)
    }
    tenants.value = []
  }
}

/**
 * 真实的隔离验证：同一个 collection 下用不同 tenantId 各检索一次。
 * VectorService 会拼出 filterExpression "tenantId == '...'"，
 * 返回条数差异就是过滤生效的直接证据。
 */
async function runProbe() {
  if (!probe.query.trim()) {
    ElMessage.warning('请填写探测用的查询文本')
    return
  }
  const targets = Array.from(
    new Set([tenant.tenantId, 'default', ...tenants.value.map((item) => item.id)].filter(Boolean))
  )
  probing.value = true
  probes.value = []
  for (const tenantId of targets) {
    try {
      const hits = await searchVectors({ query: probe.query.trim(), tenantId, topK: probe.topK })
      probes.value.push({
        tenantId,
        query: probe.query.trim(),
        hits: hits.length,
        topScore: hits.length ? hits[0].score : null,
        note: tenantId === tenant.tenantId ? '当前选中租户' : ''
      })
    } catch (error) {
      probes.value.push({
        tenantId,
        query: probe.query.trim(),
        hits: 0,
        topScore: null,
        note: isApiError(error) ? error.message : String(error)
      })
    }
  }
  probing.value = false
}

async function submit() {
  if (!form.id.trim() || !form.name.trim()) {
    ElMessage.warning('租户 ID 与名称都要填写')
    return
  }
  saving.value = true
  saveResult.value = null
  try {
    await createTenant({ id: form.id.trim(), name: form.name.trim() })
    saveResult.value = { ok: true, text: '创建成功。' }
    await load()
  } catch (error) {
    saveResult.value = {
      ok: false,
      text: isApiError(error)
        ? `POST /api/tenants → ${error.status ?? '网络失败'}：${error.message}`
        : String(error)
    }
  } finally {
    saving.value = false
  }
}

async function removeTenant(row: Tenant) {
  try {
    await ElMessageBox.confirm(`删除租户 ${row.id}？该租户下的向量需要后端同步清理。`, '确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteTenant(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    ElMessage.error(
      isApiError(error) && error.pending
        ? 'DELETE /api/tenants/{id} 待开发，后端未提供该接口'
        : isApiError(error)
          ? error.message
          : String(error)
    )
  }
}

function useAsActive(row: ProbeRow) {
  tenant.setTenantId(row.tenantId)
  ElMessage.success(`已把 ${row.tenantId} 设为当前租户，后续检索都会带上它`)
}

onMounted(load)
</script>

<template>
  <div class="app-page">
    <PageHeader
      title="租户管理"
      description="多租户在当前后端只体现为一个元数据字段：VectorService 检索时拼出 filterExpression「tenantId == '...'」，写入时把 tenantId 存进 metadata。租户实体的增删改查接口尚未实现，本页把已生效的过滤行为做成可验证的实验，其余部分标记待开发。"
    >
      <template #actions>
        <el-button :loading="state === 'loading'" @click="load">重新请求</el-button>
      </template>
    </PageHeader>

    <div class="app-panel">
      <div class="app-panel__title">
        <span>当前请求上下文</span>
        <span class="app-muted">这两个值会被所有写入与检索请求携带</span>
      </div>
      <el-form inline>
        <el-form-item label="tenantId">
          <el-input
            :model-value="tenant.tenantId"
            style="width: 200px"
            class="app-mono"
            @update:model-value="tenant.setTenantId"
          />
        </el-form-item>
        <el-form-item label="category">
          <el-input v-model="tenant.category" placeholder="写入向量时的分类标签" style="width: 200px" />
        </el-form-item>
        <el-form-item label="隔离探测">
          <el-input v-model="probe.query" placeholder="查询文本" style="width: 160px" />
          <el-input-number v-model="probe.topK" :min="1" :max="100" style="margin-left: 8px" />
          <el-button :loading="probing" style="margin-left: 8px" @click="runProbe">运行</el-button>
        </el-form-item>
      </el-form>

      <el-table v-if="probes.length" :data="probes" size="small" border>
        <el-table-column prop="tenantId" label="tenantId" min-width="150" class-name="app-mono" />
        <el-table-column prop="query" label="查询" min-width="140" />
        <el-table-column label="召回条数" width="110">
          <template #default="{ row }">{{ row.hits }}</template>
        </el-table-column>
        <el-table-column label="最高分" width="110">
          <template #default="{ row }">{{ row.topScore === null ? '-' : formatScore(row.topScore) }}</template>
        </el-table-column>
        <el-table-column prop="note" label="备注" min-width="200" />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button text size="small" @click="useAsActive(row)">设为当前</el-button>
          </template>
        </el-table-column>
      </el-table>
      <p v-else class="app-muted" style="margin: 0; font-size: 13px">
        运行后会列出各 tenantId 在同一次查询下的真实召回数量——相同查询在不同租户下返回不同结果，说明过滤条件生效。
      </p>
    </div>

    <PendingNotice endpoint-key="tenant.list" title="租户列表" />

    <div class="app-panel">
      <div class="app-panel__title">
        <span>租户列表</span>
        <span class="app-muted">
          {{
            state === 'ready'
              ? `后端返回 ${tenants.length} 条`
              : state === 'loading'
                ? '请求中'
                : state === 'pending'
                  ? `GET /api/tenants → ${detail || '404'}`
                  : `请求失败：${detail}`
          }}
        </span>
      </div>

      <el-table :data="tenants" size="small" border v-loading="state === 'loading'">
        <el-table-column prop="id" label="租户 ID" min-width="180" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="createdAt" label="创建时间" min-width="160" />
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="danger" @click="removeTenant(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="app-muted">
            {{ state === 'ready' ? '后端返回空列表' : 'GET /api/tenants 待开发，列表为空是真实结果' }}
          </span>
        </template>
      </el-table>
    </div>

    <div class="app-panel">
      <div class="app-panel__title">
        <span>新建租户（接口预留）</span>
        <span class="app-muted">点击后会向预留路径发起真实请求</span>
      </div>
      <el-form label-width="96px" style="max-width: 560px">
        <el-form-item label="租户 ID">
          <el-input v-model="form.id" placeholder="唯一标识，检索过滤用它" class="app-mono" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="form.name" placeholder="展示名" />
        </el-form-item>
        <el-form-item>
          <el-button :loading="saving" type="primary" @click="submit">提交请求</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="saveResult"
        :type="saveResult.ok ? 'success' : 'warning'"
        :closable="false"
        show-icon
        :title="saveResult.ok ? '成功' : '接口待开发'"
        :description="saveResult.text"
      />

      <div style="margin-top: 14px; display: grid; gap: 10px">
        <PendingNotice endpoint-key="tenant.create" title="新建租户" />
        <PendingNotice endpoint-key="tenant.delete" title="删除租户" />
      </div>
    </div>
  </div>
</template>
