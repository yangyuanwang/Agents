<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  label: string
  /** tone=pending 时数值一律显示占位符，避免把"没有接口"渲染成"值为 0"。 */
  value?: string | number | null
  hint?: string
  tone?: 'real' | 'pending' | 'muted'
  icon?: string
}>()

const display = computed(() => {
  if (props.tone === 'pending') {
    return '待接入'
  }
  if (props.value === null || props.value === undefined || props.value === '') {
    return '-'
  }
  return String(props.value)
})
</script>

<template>
  <div class="stat-card" :class="`is-${tone ?? 'real'}`">
    <div class="stat-card__head">
      <span class="stat-card__label">{{ label }}</span>
      <el-tag v-if="tone === 'pending'" size="small" type="warning" effect="plain">待开发</el-tag>
    </div>
    <div class="stat-card__value">{{ display }}</div>
    <div v-if="hint" class="stat-card__hint">{{ hint }}</div>
  </div>
</template>

<style scoped>
.stat-card {
  background: var(--app-panel);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  padding: 16px 18px;
  box-shadow: var(--app-shadow);
  min-height: 104px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.stat-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.stat-card__label {
  color: var(--app-text-muted);
  font-size: 13px;
}

.stat-card__value {
  font-size: 26px;
  font-weight: 650;
  line-height: 1.2;
}

.stat-card__hint {
  font-size: 12px;
  color: #94a3b8;
  line-height: 1.55;
}

.is-pending .stat-card__value {
  font-size: 18px;
  color: #b45309;
  font-weight: 600;
}

.is-muted .stat-card__value {
  color: var(--app-text-muted);
}
</style>
