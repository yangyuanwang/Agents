<script setup lang="ts">
import type { ApiEndpoint } from '@/types/api'

defineProps<{ items: ApiEndpoint[] }>()
</script>

<template>
  <el-table :data="items" size="small" border stripe>
    <el-table-column prop="name" label="能力" min-width="130" />
    <el-table-column label="接口" min-width="260">
      <template #default="{ row }">
        <span class="app-mono">
          <el-tag size="small" effect="plain" :type="row.method === 'GET' ? 'info' : 'primary'">
            {{ row.method }}
          </el-tag>
          <span class="app-mono__path">{{ row.path }}</span>
        </span>
      </template>
    </el-table-column>
    <el-table-column label="状态" width="90">
      <template #default="{ row }">
        <el-tag size="small" :type="row.status === 'available' ? 'success' : 'warning'" effect="light">
          {{ row.status === 'available' ? '已接入' : '待开发' }}
        </el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="note" label="说明" min-width="300" />
    <el-table-column label="使用页面" min-width="170">
      <template #default="{ row }">
        <el-tag v-for="page in row.usedBy" :key="page" size="small" type="info" effect="plain" class="used-tag">
          {{ page }}
        </el-tag>
      </template>
    </el-table-column>
  </el-table>
</template>

<style scoped>
.app-mono {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.app-mono__path {
  font-family: 'JetBrains Mono', Consolas, monospace;
  font-size: 12px;
}

.used-tag {
  margin: 0 4px 4px 0;
}
</style>
