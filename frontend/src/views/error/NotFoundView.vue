<script setup lang="ts">
import { useRoute, useRouter } from 'vue-router'

const route = useRoute()
const router = useRouter()
</script>

<template>
  <div class="not-found">
    <div class="not-found__card">
      <div class="not-found__code">404</div>
      <h1 class="not-found__title">页面不存在</h1>
      <p class="not-found__desc">
        没有匹配到路由
        <code class="app-mono">{{ route.fullPath }}</code>
        ，可能是地址输错了，也可能是对应模块还没接入后端接口。
      </p>
      <div class="not-found__actions">
        <el-button type="primary" @click="router.push('/dashboard')">返回 Dashboard</el-button>
        <el-button @click="router.back()">回到上一页</el-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.not-found {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--app-bg, #f5f7fa);
  padding: 24px;
}

.not-found__card {
  background: #fff;
  border: 1px solid var(--app-border, #e2e8f0);
  border-radius: 12px;
  box-shadow: var(--app-shadow, 0 1px 2px rgba(15, 23, 42, 0.06));
  padding: 40px 48px;
  text-align: center;
  max-width: 560px;
}

.not-found__code {
  font-size: 56px;
  font-weight: 700;
  letter-spacing: 2px;
  color: #94a3b8;
}

.not-found__title {
  font-size: 20px;
  margin: 8px 0 12px;
}

.not-found__desc {
  color: #475569;
  font-size: 14px;
  line-height: 1.7;
  margin: 0 0 20px;
}

.not-found__actions {
  display: flex;
  justify-content: center;
  gap: 10px;
}
</style>
