<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import PageHeader from '@/components/PageHeader.vue'
import PendingNotice from '@/components/PendingNotice.vue'
import SearchResultList from '@/components/SearchResultList.vue'
import { isApiError } from '@/api/http'
import { searchVectors } from '@/api/vector'
import { useTenantStore } from '@/stores/tenant'
import type { VectorSearchResult } from '@/types/vector'

const tenant = useTenantStore()

const form = reactive({ query: '', topK: 10 })
const loading = ref(false)
const results = ref<VectorSearchResult[]>([])
const errorText = ref('')
const searched = ref(false)
const latencyMs = ref<number | null>(null)

const drawer = reactive({ visible: false, title: '', body: '' })

/** el-input-number 清空后是 null，这里兜到后端允许的 1..100 区间，避免请求被 @Min/@Max 挡下。 */
const safeTopK = computed(() => {
  const value = Number(form.topK)
  if (!Number.isFinite(value) || value < 1) return 1
  return Math.min(100, Math.round(value))
})

const payloadPreview = computed(() =>
  JSON.stringify({ query: form.query || '<查询文本>', tenantId: tenant.tenantId, topK: safeTopK.value }, null, 2)
)

const groupedByCategory = computed(() => {
  const map = new Map<string, number>()
  for (const item of results.value) {
    const key = String(item.metadata?.category ?? '(无 category)')
    map.set(key, (map.get(key) ?? 0) + 1)
  }
  return [...map.entries()].map(([category, count]) => ({ category, count }))
})

async function search() {
  if (!form.query.trim()) {
    ElMessage.warning('请输入查询文本')
    return
  }
  loading.value = true
  errorText.value = ''
  const start = performance.now()
  try {
    results.value = await searchVectors({
      query: form.query.trim(),
      tenantId: tenant.tenantId,
      topK: safeTopK.value
    })
    searched.value = true
    latencyMs.value = Math.round(performance.now() - start)
  } catch (error) {
    results.value = []
    errorText.value = isApiError(error) ? `${error.message}${error.detail ? `：${error.detail}` : ''}` : String(error)
  } finally {
    loading.value = false
  }
}

function inspect(item: VectorSearchResult) {
  drawer.title = item.id
  drawer.body = JSON.stringify(
    { id: item.id, score: item.score, metadata: item.metadata, content: item.content },
    null,
    2
  )
  drawer.visible = true
}

async function copyId(id: string) {
  try {
    await navigator.clipboard.writeText(id)
    ElMessage.success('片段 id 已复制')
  } catch {
    ElMessage.warning('浏览器拒绝了剪贴板访问，请手动复制')
  }
}
</script>

<template>
  <div class="app-page">
    <PageHeader
      title="文档切片管理"
      description="切片视图基于已实现的 POST /api/vector/search：输入查询词就能看到 Qdrant 里真实的片段内容、相似度分数和 payload 元数据。按文档遍历、分页、停用或删除切片需要后端补 GET /api/documents/{id}/chunks 与 DELETE /api/chunks/{id}。"
    />

    <div class="app-panel">
      <div class="app-panel__title">
        <span>检索切片</span>
        <span v-if="latencyMs !== null" class="app-muted">本次耗时 {{ latencyMs }} ms，命中 {{ results.length }} 条</span>
      </div>

      <div class="chunk-layout">
        <div>
          <el-form label-position="top">
            <el-form-item label="查询文本">
              <el-input
                v-model="form.query"
                type="textarea"
                :rows="2"
                placeholder="输入要核对的内容，例如：二甲苯最大储量"
                @keyup.enter.ctrl="search"
              />
            </el-form-item>
            <el-form-item label="topK（1..100，后端校验）">
              <el-input-number v-model="form.topK" :min="1" :max="100" />
            </el-form-item>
          </el-form>
          <el-button type="primary" :loading="loading" @click="search">查询切片</el-button>
          <span class="app-muted" style="margin-left: 10px; font-size: 12px">Ctrl + Enter 也可触发</span>

          <el-alert
            v-if="errorText"
            :title="errorText"
            type="error"
            show-icon
            :closable="false"
            style="margin-top: 14px"
          />
        </div>

        <div class="chunk-side">
          <div class="chunk-side__label">实际请求体</div>
          <pre class="chunk-side__code app-mono">{{ payloadPreview }}</pre>

          <template v-if="searched && results.length > 0">
            <div class="chunk-side__label" style="margin-top: 14px">按 category 分布</div>
            <el-tag
              v-for="row in groupedByCategory"
              :key="row.category"
              size="small"
              effect="plain"
              class="chunk-side__tag"
            >
              {{ row.category }} · {{ row.count }}
            </el-tag>
          </template>
        </div>
      </div>
    </div>

    <div class="app-panel">
      <div class="app-panel__title">
        <span>切片列表</span>
        <span class="app-muted">点击任意一行查看完整内容与 metadata</span>
      </div>

      <SearchResultList
        v-if="searched"
        :results="results"
        :loading="loading"
        :keyword="form.query"
        empty-text="该 tenantId 下没有召回到片段，可在「文档管理」写入一个片段后重试"
      />
      <el-empty v-else description="还没有执行查询" />

      <el-table v-if="results.length > 0" :data="results" size="small" border class="chunk-table">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column label="片段 id" min-width="220">
          <template #default="{ row }">
            <span class="app-mono">{{ row.id }}</span>
          </template>
        </el-table-column>
        <el-table-column label="相似度" width="110">
          <template #default="{ row }">{{ row.score.toFixed(4) }}</template>
        </el-table-column>
        <el-table-column label="category" width="140">
          <template #default="{ row }">{{ row.metadata?.category ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="tenantId" width="150">
          <template #default="{ row }">{{ row.metadata?.tenantId ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="170" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="inspect(row)">详情</el-button>
            <el-button text size="small" @click="copyId(row.id)">复制 id</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <PendingNotice endpoint-key="chunk.list" title="按文档遍历 / 分页切片" />
    <PendingNotice endpoint-key="chunk.delete" title="停用 / 删除切片" />

    <el-drawer v-model="drawer.visible" :title="drawer.title" size="46%">
      <pre class="app-mono drawer-body">{{ drawer.body }}</pre>
    </el-drawer>
  </div>
</template>

<style scoped>
.chunk-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(0, 1fr);
  gap: 20px;
}

.chunk-side {
  background: #f8fafc;
  border: 1px solid var(--app-border);
  border-radius: 8px;
  padding: 12px 14px;
}

.chunk-side__label {
  font-size: 12px;
  color: var(--app-text-muted);
  margin-bottom: 6px;
}

.chunk-side__code {
  margin: 0;
  background: #fff;
  border: 1px solid var(--app-border);
  border-radius: 6px;
  padding: 10px;
  white-space: pre-wrap;
  word-break: break-all;
}

.chunk-side__tag {
  margin: 0 6px 6px 0;
}

.chunk-table {
  margin-top: 16px;
}

.drawer-body {
  white-space: pre-wrap;
  word-break: break-word;
  background: #f8fafc;
  padding: 12px;
  border-radius: 8px;
  max-height: 100%;
  overflow: auto;
}

@media (max-width: 1000px) {
  .chunk-layout {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
