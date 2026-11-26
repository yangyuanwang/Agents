<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

import PageHeader from '@/components/PageHeader.vue'
import { isApiError } from '@/api/http'
import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  listKnowledgeBases,
  updateKnowledgeBase
} from '@/api/knowledge'
import type { KnowledgeBase } from '@/types/domain'
import { isUuid, useTenantStore } from '@/stores/tenant'

type PageState = 'checking' | 'ready' | 'error'

const state = ref<PageState>('checking')
const tenant = useTenantStore()
const rows = ref<KnowledgeBase[]>([])
const lastError = ref('')
const submitting = ref(false)

const dialog = reactive({
  visible: false,
  mode: 'create' as 'create' | 'edit',
  id: '',
  name: '',
  description: '',
  createdBy: ''
})

const headline = computed(() => {
  if (state.value === 'ready') {
    return `已读取 ${rows.value.length} 个知识库。`
  }
  if (state.value === 'error') {
    return `接口返回异常：${lastError.value}`
  }
  return '正在探测 GET /api/knowledge-bases…'
})

async function load() {
  state.value = 'checking'
  lastError.value = ''
  if (!isUuid(tenant.tenantId)) {
    rows.value = []
    state.value = 'error'
    lastError.value = '请先在顶部输入数据库中已存在的租户 UUID。开发租户上下文尚未设置。'
    return
  }
  try {
    rows.value = (await listKnowledgeBases({ tenantId: tenant.tenantId })).items
    state.value = 'ready'
  } catch (error) {
    rows.value = []
    state.value = 'error'
    lastError.value = isApiError(error) ? error.message : String(error)
  }
}

function openCreate() {
  dialog.mode = 'create'
  dialog.id = ''
  dialog.name = ''
  dialog.description = ''
  dialog.createdBy = tenant.createdBy
  dialog.visible = true
}

function openEdit(row: KnowledgeBase) {
  dialog.mode = 'edit'
  dialog.id = row.id
  dialog.name = row.name
  dialog.description = row.description ?? ''
  dialog.visible = true
}

async function submit() {
  if (!dialog.name.trim()) {
    ElMessage.warning('知识库名称不能为空')
    return
  }
  if (dialog.mode === 'create') {
    if (!isUuid(tenant.tenantId)) {
      ElMessage.warning('请先设置真实租户 UUID')
      return
    }
    if (!isUuid(dialog.createdBy)) {
      ElMessage.warning('请填写数据库中已存在的开发操作者 UUID')
      return
    }
    tenant.setCreatedBy(dialog.createdBy)
  }
  submitting.value = true
  const payload = { name: dialog.name.trim(), description: dialog.description.trim() || null }

  try {
    const saved = dialog.mode === 'create'
      ? await createKnowledgeBase({ ...payload, tenantId: tenant.tenantId, createdBy: tenant.createdBy })
      : await updateKnowledgeBase(dialog.id, payload)
    ElMessage.success(`已保存：${saved.name}`)
    dialog.visible = false
    await load()
  } catch (error) {
    ElMessage.error(isApiError(error) ? error.message : String(error))
  } finally {
    submitting.value = false
  }
}

async function remove(row: KnowledgeBase) {
  try {
    await deleteKnowledgeBase(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    ElMessage.error(isApiError(error) ? error.message : String(error))
  }
}

watch(() => tenant.tenantId, () => void load())
onMounted(load)
</script>

<template>
  <div class="app-page">
    <PageHeader
      title="知识库管理"
      description="通过后端接口管理知识库。租户和创建者由当前开发身份上下文提供；这不是生产级认证或授权。"
    >
      <template #actions>
        <el-button @click="load">
          <el-icon><Refresh /></el-icon>
          <span>重新探测</span>
        </el-button>
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon>
          <span>新建知识库</span>
        </el-button>
      </template>
    </PageHeader>

    <div class="app-panel">
      <div class="app-panel__title">
        <span>接口探测结果</span>
        <span class="app-muted">{{ headline }}</span>
      </div>

      <el-table :data="rows" border v-loading="state === 'checking'">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="description" label="描述" min-width="220" />
        <el-table-column prop="documentCount" label="文档数" width="100" />
        <el-table-column prop="chunkCount" label="切片数" width="100" />
        <el-table-column prop="collectionName" label="向量集合" min-width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" @click="openEdit(row)">编辑</el-button>
            <el-button text size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="app-muted">
            {{ state === 'error' ? lastError : '暂无知识库' }}
          </span>
        </template>
      </el-table>
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.mode === 'create' ? '新建知识库' : '编辑知识库'" width="480px">
      <el-form label-position="top">
        <el-form-item v-if="dialog.mode === 'create'" label="开发操作者 UUID" required>
          <el-input v-model="dialog.createdBy" class="app-mono" placeholder="输入数据库中已存在的 User UUID" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="dialog.name" placeholder="例如：化工技改安评报告库" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="dialog.description" type="textarea" :rows="3" placeholder="用途、覆盖范围、更新频率" />
        </el-form-item>
      </el-form>
      <div class="app-muted" style="font-size: 12.5px">
        提交后请求发往
        <span class="app-mono">
          {{ dialog.mode === 'create' ? 'POST /api/knowledge-bases' : `PUT /api/knowledge-bases/${dialog.id}` }}
        </span>
      </div>
      <template #footer>
        <el-button @click="dialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="submit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
