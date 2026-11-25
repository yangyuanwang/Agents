<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import PageHeader from '@/components/PageHeader.vue'
import PendingNotice from '@/components/PendingNotice.vue'
import { createUser, deleteUser, listUsers } from '@/api/system'
import { isApiError } from '@/api/http'
import { useTenantStore } from '@/stores/tenant'
import type { AppUser } from '@/types/domain'

type LoadState = 'loading' | 'ready' | 'pending' | 'error'

const tenant = useTenantStore()

const state = ref<LoadState>('loading')
const users = ref<AppUser[]>([])
const detail = ref('')

const form = reactive({ username: '', displayName: '', role: 'viewer', tenantId: '' })
const saving = ref(false)
const saveResult = ref<{ ok: boolean; text: string } | null>(null)

async function load() {
  state.value = 'loading'
  detail.value = ''
  try {
    users.value = await listUsers({ tenantId: tenant.tenantId || undefined })
    state.value = 'ready'
  } catch (error) {
    if (isApiError(error) && error.pending) {
      state.value = 'pending'
      detail.value = `${error.status ?? '-'} ${error.message}`
    } else {
      state.value = 'error'
      detail.value = isApiError(error) ? error.message : String(error)
    }
    users.value = []
  }
}

async function submit() {
  if (!form.username.trim()) {
    ElMessage.warning('请填写用户名')
    return
  }
  saving.value = true
  saveResult.value = null
  try {
    await createUser({ ...form, username: form.username.trim(), tenantId: form.tenantId || tenant.tenantId })
    saveResult.value = { ok: true, text: '创建成功，后端已返回用户记录。' }
    await load()
  } catch (error) {
    saveResult.value = {
      ok: false,
      text: isApiError(error)
        ? `POST /api/users → ${error.status ?? '网络失败'}：${error.message}`
        : String(error)
    }
  } finally {
    saving.value = false
  }
}

async function remove(row: AppUser) {
  try {
    await ElMessageBox.confirm(`删除用户 ${row.username}？`, '确认', { type: 'warning' })
  } catch {
    return
  }
  try {
    await deleteUser(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    ElMessage.error(
      isApiError(error) && error.pending
        ? 'DELETE /api/users/{id} 待开发，后端未提供该接口'
        : isApiError(error)
          ? error.message
          : String(error)
    )
  }
}

onMounted(load)
</script>

<template>
  <div class="app-page">
    <PageHeader
      title="用户管理"
      description="后端 spring-ai-qdrant-rag 目前没有引入 Spring Security，也没有用户表和登录接口，因此本页面所有操作都会真实发起请求并把 404 结果如实展示出来，不会用假账号填充列表。"
    >
      <template #actions>
        <el-button :loading="state === 'loading'" @click="load">重新请求</el-button>
      </template>
    </PageHeader>

    <PendingNotice endpoint-key="user.list" title="用户列表" />

    <el-alert
      v-if="state === 'pending'"
      :closable="false"
      type="warning"
      show-icon
      title="接口未就绪"
      :description="`GET /api/users → ${detail || '404'}，列表为空是真实结果。`"
      style="margin-top: 14px"
    />
    <el-alert
      v-else-if="state === 'error'"
      :closable="false"
      show-icon
      type="error"
      title="请求失败"
      :description="detail"
      style="margin-top: 14px"
    />

    <div class="app-panel">
      <div class="app-panel__title">
        <span>用户列表</span>
        <span class="app-muted">
          请求携带 tenantId = {{ tenant.tenantId || '(空)' }}，状态：
          {{ state === 'ready' ? '已拿到后端数据' : state === 'loading' ? '请求中' : '待开发' }}
        </span>
      </div>

      <el-table :data="users" size="small" border v-loading="state === 'loading'">
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="displayName" label="显示名" min-width="140" />
        <el-table-column prop="role" label="角色" width="120" />
        <el-table-column prop="tenantId" label="所属租户" min-width="140" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="app-muted">
            {{ state === 'ready' ? '后端返回空列表' : 'GET /api/users 待开发，暂无用户数据' }}
          </span>
        </template>
      </el-table>
    </div>

    <div class="app-panel">
      <div class="app-panel__title">
        <span>新建用户（接口预留）</span>
        <span class="app-muted">字段名按常见约定预留，后端接口落地时以真实 DTO 为准</span>
      </div>

      <el-form label-width="96px" style="max-width: 560px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" placeholder="登录名" />
        </el-form-item>
        <el-form-item label="显示名">
          <el-input v-model="form.displayName" placeholder="可选" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.role" style="width: 100%">
            <el-option label="admin" value="admin" />
            <el-option label="editor" value="editor" />
            <el-option label="viewer" value="viewer" />
          </el-select>
        </el-form-item>
        <el-form-item label="租户 ID">
          <el-input v-model="form.tenantId" :placeholder="tenant.tenantId || 'default'" class="app-mono" />
        </el-form-item>
        <el-form-item>
          <el-button :loading="saving" type="primary" @click="submit">提交请求</el-button>
        </el-form-item>
      </el-form>

      <el-alert
        v-if="saveResult"
        :type="saveResult.ok ? 'success' : 'warning'"
        :closable="false"
        show-icon
        :title="saveResult.ok ? '成功' : '接口待开发'"
        :description="saveResult.text"
      />

      <div style="margin-top: 14px; display: grid; gap: 10px">
        <PendingNotice endpoint-key="user.create" title="新建用户" />
        <PendingNotice endpoint-key="user.delete" title="删除用户" />
      </div>
    </div>
  </div>
</template>
