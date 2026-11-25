<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'

import PageHeader from '@/components/PageHeader.vue'
import PendingNotice from '@/components/PendingNotice.vue'
import { isApiError, isPendingError } from '@/api/http'
import {
  createKnowledgeBase,
  deleteKnowledgeBase,
  listKnowledgeBases,
  updateKnowledgeBase
} from '@/api/knowledge'
import type { KnowledgeBase } from '@/types/domain'

type PageState = 'checking' | 'pending' | 'ready' | 'error'

const state = ref<PageState>('checking')
const rows = ref<KnowledgeBase[]>([])
const lastError = ref('')
const submitting = ref(false)

const dialog = reactive({
  visible: false,
  mode: 'create' as 'create' | 'edit',
  id: '',
  name: '',
  description: ''
})

const headline = computed(() => {
  if (state.value === 'pending') {
    return '后端没有知识库接口，列表为空是真实结果，不是加载失败。'
  }
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
  try {
    rows.value = await listKnowledgeBases()
    state.value = 'ready'
  } catch (error) {
    rows.value = []
    if (isPendingError(error)) {
      state.value = 'pending'
    } else {
      state.value = 'error'
      lastError.value = isApiError(error) ? error.message : String(error)
    }
  }
}

function openCreate() {
  dialog.mode = 'create'
  dialog.id = ''
  dialog.name = ''
  dialog.description = ''
  dialog.visible = true
}

function openEdit(row: KnowledgeBase) {
  dialog.mode = 'edit'
  dialog.id = row.id
  dialog.name = row.name
  dialog.description = row.description
  dialog.visible = true
}

/** 提交会真实打到预留地址；后端补上接口后这里不需要再改。 */
async function submit() {
  if (!dialog.name.trim()) {
    ElMessage.warning('知识库名称不能为空')
    return
  }
  submitting.value = true
  const payload = { name: dialog.name.trim(), description: dialog.description.trim() }

  try {
    const saved = dialog.mode === 'create'
      ? await createKnowledgeBase(payload)
      : await updateKnowledgeBase(dialog.id, payload)
    ElMessage.success(`已保存：${saved.name}`)
    dialog.visible = false
    await load()
  } catch (error) {
    if (isPendingError(error)) {
      ElMessage.warning('知识库写入接口待开发，请求已按契约发往后端但返回 404')
      state.value = 'pending'
    } else {
      ElMessage.error(isApiError(error) ? error.message : String(error))
    }
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
    if (isPendingError(error)) {
      ElMessage.warning('删除接口待开发')
      state.value = 'pending'
    } else {
      ElMessage.error(isApiError(error) ? error.message : String(error))
    }
  }
}

onMounted(load)
</script>

<template>
  <div class="app-page">
    <PageHeader
      title="知识库管理"
      description="创建、编辑、删除知识库。后端目前把向量集合固定在配置项 spring.ai.vectorstore.qdrant.collection-name，还没有知识库维度的建模与接口，因此本页所有写操作都会真实发往预留地址并返回 404。"
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

    <PendingNotice endpoint-key="knowledge.list" title="知识库列表" />
    <PendingNotice endpoint-key="knowledge.create" title="创建 / 编辑知识库" />
    <PendingNotice endpoint-key="knowledge.delete" title="删除知识库" />

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
            {{ state === 'pending' ? '知识库接口待开发，暂无数据' : '暂无知识库' }}
          </span>
        </template>
      </el-table>
    </div>

    <el-dialog v-model="dialog.visible" :title="dialog.mode === 'create' ? '新建知识库' : '编辑知识库'" width="480px">
      <el-form label-position="top">
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
