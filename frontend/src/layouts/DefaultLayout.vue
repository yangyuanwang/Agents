<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter, type RouteRecordRaw } from 'vue-router'
import { ElMessage } from 'element-plus'

import StatusLight from '@/components/StatusLight.vue'
import { useAppStore } from '@/stores/app'
import { useSystemStore } from '@/stores/system'
import { useTenantStore } from '@/stores/tenant'

interface MenuEntry {
  path: string
  title: string
  icon: string
  group: string
  status: string
}

const route = useRoute()
const router = useRouter()
const app = useAppStore()
const system = useSystemStore()
const tenant = useTenantStore()

const compact = ref(false)
const tenantDraft = ref(tenant.tenantId)

const menuEntries = computed<MenuEntry[]>(() => {
  const root = router.options.routes.find((item) => item.path === '/') as RouteRecordRaw | undefined
  const children = root?.children ?? []
  return children
    .filter((child) => Boolean(child.meta) && child.meta?.hidden !== true)
    .map((child) => ({
      path: `/${String(child.path).replace(/^\//, '')}`,
      title: String(child.meta?.title ?? child.path),
      icon: String(child.meta?.icon ?? 'Menu'),
      group: String(child.meta?.group ?? '其他'),
      status: String(child.meta?.status ?? 'available')
    }))
})

const groups = computed(() => {
  const map = new Map<string, MenuEntry[]>()
  for (const entry of menuEntries.value) {
    const list = map.get(entry.group)
    if (list) {
      list.push(entry)
    } else {
      map.set(entry.group, [entry])
    }
  }
  return [...map.entries()].map(([name, items]) => ({ name, items }))
})

const currentTitle = computed(() => String(route.meta.title ?? ''))

function statusTag(status: string): { label: string; type: 'success' | 'warning' | 'primary' } {
  if (status === 'available') {
    return { label: '已接入', type: 'success' }
  }
  if (status === 'pending') {
    return { label: '待开发', type: 'warning' }
  }
  return { label: '部分接入', type: 'primary' }
}

function applyTenant() {
  const value = tenantDraft.value.trim()
  if (!value) {
    ElMessage.warning('租户标识不能为空，检索时它是必填过滤条件')
    tenantDraft.value = tenant.tenantId
    return
  }
  tenant.setTenantId(value)
  ElMessage.success(`检索过滤条件已切换为 tenantId = ${value}`)
}

function onResize() {
  compact.value = window.innerWidth < 960
  if (compact.value) {
    app.sidebarCollapsed = true
  }
}

onMounted(() => {
  onResize()
  window.addEventListener('resize', onResize)
  if (system.state === 'checking') {
    void system.refresh()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
})
</script>

<template>
  <div class="layout">
    <aside class="layout__aside" :class="{ 'is-collapsed': app.sidebarCollapsed }">
      <div class="layout__brand">
        <span class="layout__logo">RAG</span>
        <span v-if="!app.sidebarCollapsed" class="layout__brand-text">知识库管理平台</span>
      </div>

      <el-scrollbar class="layout__nav">
        <el-menu
          :default-active="route.path"
          :collapse="app.sidebarCollapsed"
          :collapse-transition="false"
          router
          class="layout__menu"
        >
          <template v-for="group in groups" :key="group.name">
            <div v-if="!app.sidebarCollapsed" class="layout__group">{{ group.name }}</div>
            <el-menu-item v-for="entry in group.items" :key="entry.path" :index="entry.path">
              <el-icon><component :is="entry.icon" /></el-icon>
              <template #title>
                <span class="layout__item-text">{{ entry.title }}</span>
                <el-tag :type="statusTag(entry.status).type" size="small" effect="plain">
                  {{ statusTag(entry.status).label }}
                </el-tag>
              </template>
            </el-menu-item>
          </template>
        </el-menu>
      </el-scrollbar>
    </aside>

    <div class="layout__body">
      <header class="layout__header">
        <div class="layout__header-left">
          <el-button text @click="app.toggleSidebar">
            <el-icon :size="18"><Fold v-if="!app.sidebarCollapsed" /><Expand v-else /></el-icon>
          </el-button>
          <span class="layout__crumb">{{ currentTitle }}</span>
        </div>

        <div class="layout__header-right">
          <div class="layout__tenant">
            <span class="layout__tenant-label">tenantId</span>
            <el-input
              v-model="tenantDraft"
              size="small"
              class="layout__tenant-input"
              placeholder="租户标识"
              @keyup.enter="applyTenant"
              @blur="applyTenant"
            />
          </div>
          <el-button size="small" text :loading="system.state === 'checking'" @click="system.refresh()">
            <el-icon><Refresh /></el-icon>
          </el-button>
          <StatusLight :state="system.state" :text="system.statusText" />
        </div>
      </header>

      <main class="layout__main">
        <RouterView v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped>
.layout {
  display: flex;
  height: 100%;
  overflow: hidden;
}

.layout__aside {
  width: 224px;
  flex: 0 0 auto;
  background: var(--app-sidebar-bg);
  color: var(--app-sidebar-text);
  display: flex;
  flex-direction: column;
  transition: width 0.2s ease;
}

.layout__aside.is-collapsed {
  width: 64px;
}

.layout__brand {
  height: 56px;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
}

.layout__logo {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  background: var(--app-brand);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 12px;
  flex: 0 0 auto;
}

.layout__brand-text {
  font-size: 13.5px;
  font-weight: 600;
  color: #f1f5f9;
  white-space: nowrap;
}

.layout__nav {
  flex: 1;
  min-height: 0;
}

.layout__group {
  padding: 14px 16px 6px;
  font-size: 11.5px;
  letter-spacing: 0.06em;
  color: #64748b;
  text-transform: uppercase;
}

.layout__menu {
  border-right: none;
  background: transparent;
  --el-menu-bg-color: transparent;
  --el-menu-text-color: #cbd5e1;
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.06);
  --el-menu-active-color: #ffffff;
}

.layout__menu:not(.el-menu--collapse) {
  width: 224px;
}

.layout__item-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.layout__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.layout__header {
  height: 56px;
  flex: 0 0 auto;
  background: #fff;
  border-bottom: 1px solid var(--app-border);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 18px;
}

.layout__header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
}

.layout__crumb {
  font-weight: 600;
  font-size: 15px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.layout__header-right {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: nowrap;
}

.layout__tenant {
  display: flex;
  align-items: center;
  gap: 6px;
}

.layout__tenant-label {
  font-size: 12px;
  color: var(--app-text-muted);
  font-family: 'JetBrains Mono', Consolas, monospace;
}

.layout__tenant-input {
  width: 150px;
}

.layout__main {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 20px 22px 28px;
}

:deep(.el-menu-item .el-tag) {
  margin-left: 8px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.15s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@media (max-width: 768px) {
  .layout__tenant-input {
    width: 96px;
  }

  .layout__main {
    padding: 14px;
  }
}
</style>
