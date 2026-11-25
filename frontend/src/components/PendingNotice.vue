<script setup lang="ts">
import { computed } from 'vue'

import { endpoint } from '@/api/registry'

const props = defineProps<{
  /** registry.ts 里的接口 key，传了就走清单渲染，未传则用下面的自由文案。 */
  endpointKey?: string
  title?: string
  description?: string
  severity?: 'info' | 'warning'
}>()

const meta = computed(() => (props.endpointKey ? endpoint(props.endpointKey) : undefined))

const heading = computed(() => props.title ?? meta.value?.name ?? '该能力待开发')

const detail = computed(() => {
  if (meta.value) {
    return `${meta.value.method} ${meta.value.path} —— ${meta.value.note}`
  }
  return props.description ?? ''
})
</script>

<template>
  <el-alert :type="severity ?? 'info'" :closable="false" show-icon class="pending-notice">
    <template #title>
      <span class="pending-notice__title">
        <el-tag size="small" type="warning" effect="plain">待开发</el-tag>
        <span>{{ heading }}</span>
        <el-tag v-if="meta" size="small" effect="plain" class="app-mono">
          {{ meta.method }} {{ meta.path }}
        </el-tag>
      </span>
    </template>
    <template #default>
      <div class="pending-notice__detail">{{ detail }}</div>
      <div v-if="$slots.default" class="pending-notice__slot">
        <slot />
      </div>
    </template>
  </el-alert>
</template>

<style scoped>
.pending-notice {
  border-radius: 8px;
}

.pending-notice__title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-weight: 600;
}

.pending-notice__detail {
  margin-top: 4px;
  font-size: 12.5px;
  line-height: 1.65;
  color: #475569;
}

.pending-notice__slot {
  margin-top: 8px;
}
</style>
