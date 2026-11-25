<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  state: 'online' | 'offline' | 'checking' | 'unknown'
  text?: string
}>()

const color = computed(() => {
  if (props.state === 'online') {
    return '#16a34a'
  }
  if (props.state === 'offline') {
    return '#dc2626'
  }
  return '#94a3b8'
})

const label = computed(() => props.text ?? props.state)
</script>

<template>
  <span class="status-light">
    <span class="status-light__dot" :class="{ 'is-checking': state === 'checking' }" :style="{ background: color }" />
    <span class="status-light__text">{{ label }}</span>
  </span>
</template>

<style scoped>
.status-light {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12.5px;
  color: #475569;
}

.status-light__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  display: inline-block;
}

.is-checking {
  animation: pulse 1.1s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 0.35;
  }
  50% {
    opacity: 1;
  }
}
</style>
