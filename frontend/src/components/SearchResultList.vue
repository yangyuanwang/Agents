<script setup lang="ts">
import { computed } from 'vue'

import { formatScore, readableMetadata, truncate } from '@/utils/format'
import type { VectorSearchResult } from '@/types/vector'

const props = defineProps<{
  results: VectorSearchResult[]
  loading?: boolean
  /** 命中高亮用的关键词，只做纯文本包含判断，不改写内容。 */
  keyword?: string
  emptyText?: string
}>()

const items = computed(() =>
  props.results.map((item, index) => ({
    ...item,
    rank: index + 1,
    meta: readableMetadata(item.metadata),
    highlighted: props.keyword
      ? item.content.toLowerCase().includes(props.keyword.toLowerCase())
      : false
  }))
)

function scorePercent(score: number): number {
  if (!Number.isFinite(score)) {
    return 0
  }
  return Math.round(Math.max(0, Math.min(1, score)) * 100)
}

function scoreColor(score: number): string {
  if (score >= 0.8) {
    return '#16a34a'
  }
  if (score >= 0.6) {
    return '#d97706'
  }
  return '#94a3b8'
}
</script>

<template>
  <div v-loading="loading" class="result-list">
    <el-empty v-if="!loading && items.length === 0" :description="emptyText ?? '没有检索结果'" />

    <div v-for="item in items" :key="item.id" class="result-item">
      <div class="result-item__head">
        <span class="result-item__rank">#{{ item.rank }}</span>
        <span class="app-mono result-item__id" :title="item.id">{{ item.id }}</span>
        <el-tag v-if="item.highlighted" size="small" type="success" effect="plain">含关键词</el-tag>
        <span class="result-item__spacer" />
        <span class="result-item__score" :style="{ color: scoreColor(item.score) }">
          {{ formatScore(item.score) }}
        </span>
        <el-progress
          class="result-item__bar"
          :percentage="scorePercent(item.score)"
          :stroke-width="6"
          :show-text="false"
          :color="scoreColor(item.score)"
        />
      </div>

      <div class="result-item__content">{{ truncate(item.content, 420) }}</div>

      <div v-if="item.meta.length" class="result-item__meta">
        <el-tag v-for="[key, value] in item.meta" :key="key" size="small" type="info" effect="plain">
          {{ key }}: {{ value }}
        </el-tag>
      </div>
    </div>
  </div>
</template>

<style scoped>
.result-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 60px;
}

.result-item {
  border: 1px solid var(--app-border);
  border-radius: 8px;
  padding: 12px 14px;
  background: #fff;
}

.result-item__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.result-item__rank {
  font-weight: 600;
  color: var(--app-brand);
  font-size: 12.5px;
}

.result-item__id {
  color: #64748b;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.result-item__spacer {
  flex: 1;
}

.result-item__score {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-weight: 600;
  min-width: 62px;
  text-align: right;
}

.result-item__bar {
  width: 120px;
}

.result-item__content {
  font-size: 13px;
  line-height: 1.7;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-word;
}

.result-item__meta {
  margin-top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
