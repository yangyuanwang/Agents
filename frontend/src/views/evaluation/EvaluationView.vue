<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import PageHeader from '@/components/PageHeader.vue'
import PendingNotice from '@/components/PendingNotice.vue'
import SearchResultList from '@/components/SearchResultList.vue'
import StatCard from '@/components/StatCard.vue'
import { judgeHit, summarize } from '@/api/evaluation'
import { isApiError } from '@/api/http'
import { searchVectors } from '@/api/vector'
import { useTenantStore } from '@/stores/tenant'
import { formatPercent, newId } from '@/utils/format'
import type { EvaluationCase, EvaluationCaseResult } from '@/types/domain'

const CASES_KEY = 'rag-admin.evaluation.cases'

const tenant = useTenantStore()

const cases = ref<EvaluationCase[]>([])
const running = ref(false)
const progress = ref(0)
const results = ref<EvaluationCaseResult[]>([])
const config = reactive({ topK: 5 })

const summary = computed(() => summarize(results.value))
const hasRun = computed(() => results.value.length > 0)

function defaultCases(): EvaluationCase[] {
  return [
    { id: newId('case'), question: '危险品库二甲苯最大储量是多少', expect: '二甲苯' },
    { id: newId('case'), question: '总图布置需要符合哪些规范', expect: 'GB 50016' }
  ]
}

function loadCases() {
  try {
    const raw = localStorage.getItem(CASES_KEY)
    cases.value = raw ? (JSON.parse(raw) as EvaluationCase[]) : defaultCases()
  } catch {
    cases.value = defaultCases()
  }
}

function persistCases() {
  localStorage.setItem(CASES_KEY, JSON.stringify(cases.value))
}

function addCase() {
  cases.value.push({ id: newId('case'), question: '', expect: '' })
  persistCases()
}

function removeCase(id: string) {
  cases.value = cases.value.filter((item) => item.id !== id)
  persistCases()
}

/**
 * 命中率与 MRR 完全由真实检索结果计算：
 * 每条用例调用一次 POST /api/vector/search，再在返回的片段里找期望值。
 */
async function run() {
  const usable = cases.value.filter((item) => item.question.trim() && item.expect.trim())
  if (usable.length === 0) {
    ElMessage.warning('请先填写至少一条用例的问题与期望命中内容')
    return
  }

  running.value = true
  progress.value = 0
  results.value = []

  for (let index = 0; index < usable.length; index += 1) {
    const item = usable[index]
    const started = performance.now()
    try {
      const hits = await searchVectors({ query: item.question, tenantId: tenant.tenantId, topK: config.topK })
      const judgement = judgeHit(hits, item.expect)
      results.value.push({
        caseId: item.id,
        question: item.question,
        hit: judgement.hit,
        rank: judgement.rank,
        latencyMs: Math.round(performance.now() - started),
        topK: config.topK,
        results: hits
      })
    } catch (error) {
      results.value.push({
        caseId: item.id,
        question: item.question,
        hit: false,
        rank: null,
        latencyMs: Math.round(performance.now() - started),
        topK: config.topK,
        results: [],
        error: isApiError(error) ? error.message : String(error)
      })
    }
    progress.value = Math.round(((index + 1) / usable.length) * 100)
  }

  running.value = false
  ElMessage.success(`完成 ${usable.length} 条用例，命中 ${summary.value.hits} 条`)
}

onMounted(loadCases)
</script>

<template>
  <div class="app-page">
    <PageHeader
      title="RAG 评估"
      description="检索命中率与 MRR 是真实计算出来的：每条用例调用一次 POST /api/vector/search，在返回片段里匹配期望的 id 或关键词。Faithfulness、Answer Relevancy 和幻觉风险需要 LLM 判定接口，后端尚未提供，这里只保留指标位，不填任何估算值。"
    >
      <template #actions>
        <el-button :loading="running" type="primary" @click="run">运行评估</el-button>
        <el-button @click="addCase">新增用例</el-button>
      </template>
    </PageHeader>

    <div class="app-grid app-grid--4">
      <StatCard
        label="检索命中率"
        :value="hasRun ? formatPercent(summary.hitRate) : '-'"
        :hint="hasRun ? `${summary.hits} / ${summary.total} 条用例在 topK=${config.topK} 内命中` : '尚未运行'"
      />
      <StatCard
        label="MRR（平均倒数排名）"
        :value="hasRun && summary.mrr !== null ? summary.mrr.toFixed(3) : '-'"
        hint="命中位置越靠前，取值越接近 1"
      />
      <StatCard
        label="平均检索耗时"
        :value="hasRun && summary.avgLatencyMs !== null ? `${Math.round(summary.avgLatencyMs)} ms` : '-'"
        hint="含网络与后端 Embedding 调用"
      />
      <StatCard
        label="失败用例"
        :value="hasRun ? summary.failed : '-'"
        tone="muted"
        hint="接口报错或用例未填写"
      />
    </div>

    <div class="app-panel">
      <div class="app-panel__title">
        <span>评估指标（需要 LLM 判定）</span>
        <span class="app-muted">以下三项依赖后端对话与判定链路</span>
      </div>
      <div class="app-grid app-grid--2">
        <StatCard label="Faithfulness 忠实度" tone="pending" hint="待判定接口：回答中的每条陈述能否被引用片段支撑" />
        <StatCard label="Answer Relevancy 答案相关性" tone="pending" hint="待判定接口：回答与问题的语义相关度" />
        <StatCard label="幻觉风险" tone="pending" hint="待判定接口：无引用支撑的内容占比" />
        <StatCard label="引用覆盖率" tone="pending" hint="待对话接口返回引用来源后统计" />
      </div>
      <div style="margin-top: 14px">
        <PendingNotice endpoint-key="evaluation.run" title="评估任务接口" />
      </div>
    </div>

    <div class="app-panel">
      <div class="app-panel__title">
        <span>测试用例</span>
        <span class="app-muted">
          用例保存在浏览器本地（{{ cases.length }} 条），期望值可以填片段 id，也可以填内容关键词
        </span>
      </div>

      <el-form inline>
        <el-form-item label="topK">
          <el-input-number v-model="config.topK" :min="1" :max="50" />
        </el-form-item>
        <el-form-item label="tenantId">
          <el-input :model-value="tenant.tenantId" disabled style="width: 160px" class="app-mono" />
        </el-form-item>
      </el-form>

      <el-table :data="cases" size="small" border>
        <el-table-column type="index" label="#" width="50" />
        <el-table-column label="问题" min-width="260">
          <template #default="{ row }">
            <el-input v-model="row.question" placeholder="要检索的问题" @blur="persistCases" />
          </template>
        </el-table-column>
        <el-table-column label="期望命中（id 或关键词）" min-width="200">
          <template #default="{ row }">
            <el-input v-model="row.expect" placeholder="例如：二甲苯" @blur="persistCases" />
          </template>
        </el-table-column>
        <el-table-column label="上次结果" width="140">
          <template #default="{ row }">
            <span v-if="!hasRun" class="app-muted">-</span>
            <el-tag
              v-else
              size="small"
              :type="results.find((item) => item.caseId === row.id)?.hit ? 'success' : 'danger'"
              effect="light"
            >
              {{
                results.find((item) => item.caseId === row.id)?.hit
                  ? `命中第 ${results.find((item) => item.caseId === row.id)?.rank} 位`
                  : '未命中'
              }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="80" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="danger" @click="removeCase(row.id)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="app-muted">还没有用例，点击右上角「新增用例」</span>
        </template>
      </el-table>

      <el-progress v-if="running" :percentage="progress" style="margin-top: 12px" />
    </div>

    <div v-if="hasRun" class="app-panel">
      <div class="app-panel__title">
        <span>逐条结果</span>
        <span class="app-muted">展开可看该次真实召回的片段与分数</span>
      </div>

      <el-table :data="results" size="small" border>
        <el-table-column type="expand">
          <template #default="{ row }">
            <div style="padding: 8px 16px">
              <SearchResultList :results="row.results" :keyword="row.question" empty-text="没有召回片段" />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="question" label="问题" min-width="240" />
        <el-table-column label="命中" width="90">
          <template #default="{ row }">
            <el-tag size="small" :type="row.hit ? 'success' : 'danger'" effect="light">
              {{ row.hit ? '是' : '否' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="排名" width="80">
          <template #default="{ row }">{{ row.rank ?? '-' }}</template>
        </el-table-column>
        <el-table-column label="召回数" width="90">
          <template #default="{ row }">{{ row.results.length }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">{{ row.latencyMs }} ms</template>
        </el-table-column>
        <el-table-column label="错误" min-width="160">
          <template #default="{ row }">
            <span class="app-muted">{{ row.error ?? '-' }}</span>
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 14px">
        <PendingNotice endpoint-key="evaluation.list" title="评估历史" />
      </div>
    </div>
  </div>
</template>
