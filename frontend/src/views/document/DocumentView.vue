<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, type UploadFile, type UploadUserFile } from 'element-plus'

import PageHeader from '@/components/PageHeader.vue'
import PendingNotice from '@/components/PendingNotice.vue'
import { isApiError, isPendingError } from '@/api/http'
import { deleteDocument, listDocuments, uploadDocument } from '@/api/document'
import { addVector } from '@/api/vector'
import { useTenantStore } from '@/stores/tenant'
import { formatBytes, newUuid } from '@/utils/format'
import type { RagDocument } from '@/types/domain'

const tenant = useTenantStore()

const state = ref<'checking' | 'pending' | 'ready' | 'error'>('checking')
const rows = ref<RagDocument[]>([])
const lastError = ref('')

const fileList = ref<UploadUserFile[]>([])
const uploading = ref(false)

/** 已接入能力：手工把单个片段写入向量库，走 POST /api/vector/add。 */
const writer = reactive({
  id: newUuid(),
  content: '',
  category: 'general',
  saving: false,
  result: ''
})

const accepted = '.pdf,.doc,.docx,.txt,.md'

const selectedSize = computed(() =>
  fileList.value.reduce((sum, item) => sum + (item.size ?? 0), 0)
)

async function load() {
  state.value = 'checking'
  lastError.value = ''
  try {
    rows.value = await listDocuments()
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

function onFileChange(file: UploadFile) {
  const raw = file.raw
  if (!raw) {
    return
  }
  if (raw.size > 20 * 1024 * 1024) {
    ElMessage.warning(`单个文件超过 20MB：${file.name}`)
    fileList.value = fileList.value.filter((item) => item.uid !== file.uid)
  }
}

/** 真实发往预留的 multipart 接口；后端实现前会返回 404。 */
async function sendUpload() {
  const raw = fileList.value[0]?.raw
  if (!raw) {
    ElMessage.warning('请先选择文件')
    return
  }
  uploading.value = true
  try {
    const saved = await uploadDocument({
      file: raw,
      knowledgeBaseId: 'default',
      tenantId: tenant.tenantId,
      category: writer.category || 'general'
    })
    ElMessage.success(`上传成功：${saved.fileName}`)
    fileList.value = []
    await load()
  } catch (error) {
    if (isPendingError(error)) {
      ElMessage.warning('文档上传接口待开发：后端还没有 multipart 入口与解析器')
      state.value = 'pending'
    } else {
      ElMessage.error(isApiError(error) ? error.message : String(error))
    }
  } finally {
    uploading.value = false
  }
}

async function saveChunk() {
  if (!writer.content.trim()) {
    ElMessage.warning('片段内容不能为空，后端四个字段都是 @NotBlank')
    return
  }
  writer.saving = true
  writer.result = ''
  try {
    const response = await addVector({
      id: writer.id.trim(),
      content: writer.content.trim(),
      tenantId: tenant.tenantId,
      category: writer.category.trim() || 'general'
    })
    writer.result = `已写入：id=${response.id}，status=${response.status}`
    ElMessage.success('片段已向量化并写入 Qdrant')
    writer.id = newUuid()
    writer.content = ''
  } catch (error) {
    ElMessage.error(isApiError(error) ? `${error.message}${error.detail ? `：${error.detail}` : ''}` : String(error))
  } finally {
    writer.saving = false
  }
}

async function remove(row: RagDocument) {
  try {
    await deleteDocument(row.id)
    ElMessage.success('已删除')
    await load()
  } catch (error) {
    if (isPendingError(error)) {
      ElMessage.warning('文档删除接口待开发')
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
      title="文档管理"
      description="上传 PDF / Word / TXT 并跟踪解析、分块、向量化状态。后端目前没有文档接口，也没有解析器与分块器；页面把上传请求真实发往预留地址，同时提供已经可用的单片段写入能力。"
    >
      <template #actions>
        <el-button @click="load">
          <el-icon><Refresh /></el-icon>
          <span>刷新列表</span>
        </el-button>
      </template>
    </PageHeader>

    <div class="app-grid app-grid--2">
      <div class="app-panel">
        <div class="app-panel__title">
          <span>文件上传</span>
          <el-tag size="small" type="warning" effect="plain">接口待开发</el-tag>
        </div>

        <el-upload
          v-model:file-list="fileList"
          drag
          :accept="accepted"
          :auto-upload="false"
          :limit="5"
          multiple
          :on-change="onFileChange"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">把文件拖到这里，或<em>点击选择</em></div>
          <template #tip>
            <div class="app-muted" style="font-size: 12.5px">
              支持 {{ accepted.split(',').join(' / ') }}；已选 {{ fileList.length }} 个，共
              {{ formatBytes(selectedSize) }}。
              请求会发往 <span class="app-mono">POST /api/documents/upload</span>，
              表单字段 file / knowledgeBaseId / tenantId / category。
            </div>
          </template>
        </el-upload>

        <div style="margin-top: 12px">
          <el-button type="primary" :loading="uploading" :disabled="fileList.length === 0" @click="sendUpload">
            发送到后端
          </el-button>
        </div>

        <div style="margin-top: 14px">
          <PendingNotice endpoint-key="document.upload" title="文档上传" />
        </div>
      </div>

      <div class="app-panel">
        <div class="app-panel__title">
          <span>写入单个向量片段</span>
          <el-tag size="small" type="success" effect="plain">已接入</el-tag>
        </div>

        <el-form label-position="top">
          <el-form-item label="片段 id（必须是 UUID，重复 id 会覆盖同一条向量）">
            <el-input v-model="writer.id" class="app-mono" />
          </el-form-item>
          <el-form-item label="片段内容（会被 Embedding 模型转成向量）">
            <el-input
              v-model="writer.content"
              type="textarea"
              :rows="5"
              placeholder="例如：危险品库内二甲苯最大储量为 20 吨，按 GB 15603 分区存放。"
            />
          </el-form-item>
          <el-form-item label="category（写入 Qdrant payload，可用于检索结果展示）">
            <el-input v-model="writer.category" placeholder="general" />
          </el-form-item>
          <el-form-item label="tenantId（当前右上角选择的租户标识）">
            <el-input :model-value="tenant.tenantId" disabled class="app-mono" />
          </el-form-item>
        </el-form>

        <el-button type="primary" :loading="writer.saving" @click="saveChunk">写入向量库</el-button>

        <el-alert
          v-if="writer.result"
          :title="writer.result"
          type="success"
          show-icon
          :closable="true"
          style="margin-top: 12px"
        />
        <div class="app-muted" style="margin-top: 10px; font-size: 12.5px">
          写入后可到「切片管理」用同样内容检索验证，链路是
          <span class="app-mono">POST /api/vector/add → Embedding → Qdrant</span>。
        </div>
      </div>
    </div>

    <div class="app-panel">
      <div class="app-panel__title">
        <span>文档列表与处理状态</span>
        <span class="app-muted">
          {{
            state === 'pending'
              ? 'GET /api/documents 返回 404，列表为空是真实结果'
              : state === 'checking'
                ? '正在探测接口…'
                : state === 'error'
                  ? `接口异常：${lastError}`
                  : `已读取 ${rows.length} 条`
          }}
        </span>
      </div>

      <el-table :data="rows" border v-loading="state === 'checking'">
        <el-table-column prop="fileName" label="文件名" min-width="200" />
        <el-table-column prop="fileType" label="类型" width="90" />
        <el-table-column label="大小" width="100">
          <template #default="{ row }">{{ formatBytes(row.sizeBytes) }}</template>
        </el-table-column>
        <el-table-column label="处理状态" width="130">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'completed' ? 'success' : 'warning'">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="切片数" width="100" />
        <el-table-column prop="errorMessage" label="错误信息" min-width="180" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button text size="small" type="danger" @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span class="app-muted">文档接口待开发，暂无数据</span>
        </template>
      </el-table>

      <div style="margin-top: 14px">
        <PendingNotice endpoint-key="document.list" title="文档列表与解析 / 分块 / 向量化状态" />
      </div>
    </div>
  </div>
</template>
