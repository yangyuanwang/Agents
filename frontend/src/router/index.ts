import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'

import DefaultLayout from '@/layouts/DefaultLayout.vue'

/**
 * meta.status：available = 页面已接入真实后端接口；pending = 页面结构完成但接口待开发。
 * 侧边栏据此打标签，避免把待开发功能当成已完成能力展示。
 */
const routes: RouteRecordRaw[] = [
  {
    path: '/',
    component: DefaultLayout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '首页 Dashboard', icon: 'Odometer', group: '概览', status: 'available' }
      },
      {
        path: 'chat',
        name: 'chat',
        component: () => import('@/views/chat/ChatView.vue'),
        meta: { title: 'AI 对话', icon: 'ChatDotRound', group: '智能问答', status: 'partial' }
      },
      {
        path: 'knowledge',
        name: 'knowledge',
        component: () => import('@/views/knowledge/KnowledgeView.vue'),
        meta: { title: '知识库管理', icon: 'Collection', group: '知识管理', status: 'pending' }
      },
      {
        path: 'document',
        name: 'document',
        component: () => import('@/views/document/DocumentView.vue'),
        meta: { title: '文档管理', icon: 'Document', group: '知识管理', status: 'partial' }
      },
      {
        path: 'document/chunks',
        name: 'chunks',
        component: () => import('@/views/document/ChunkListView.vue'),
        meta: { title: '切片管理', icon: 'Files', group: '知识管理', status: 'available' }
      },
      {
        path: 'evaluation',
        name: 'evaluation',
        component: () => import('@/views/evaluation/EvaluationView.vue'),
        meta: { title: 'RAG 评估', icon: 'DataAnalysis', group: '质量', status: 'partial' }
      },
      {
        path: 'system/users',
        name: 'users',
        component: () => import('@/views/system/UserView.vue'),
        meta: { title: '用户管理', icon: 'User', group: '系统', status: 'pending' }
      },
      {
        path: 'system/tenants',
        name: 'tenants',
        component: () => import('@/views/system/TenantView.vue'),
        meta: { title: '租户管理', icon: 'Coordinate', group: '系统', status: 'pending' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    name: 'not-found',
    component: () => import('@/views/error/NotFoundView.vue'),
    meta: { title: '页面不存在', hidden: true }
  }
]

const router = createRouter({
  // 用 hash 模式，产物直接丢进任意静态目录或 Nginx 根路径都能跑，
  // 不需要额外配 try_files；要切 history 模式时改这一行并加 SPA fallback。
  history: createWebHashHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

router.afterEach((to) => {
  const base = import.meta.env.VITE_APP_TITLE || 'RAG 知识库管理平台'
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · ${base}` : base
})

export default router
