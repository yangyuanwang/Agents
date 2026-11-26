<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox, type UploadFile, type UploadUserFile } from 'element-plus'

import PageHeader from '@/components/PageHeader.vue'
import { isApiError } from '@/api/http'
import {
  createDocument,
  createDocumentVersion,
  deleteDocument,
  getDocument,
  getDocumentVersion,
  listDocumentVersions,
  listDocuments,
  setActiveDocumentVersion,
  updateDocument,
  uploadFile,
  type DocumentRecord,
  type DocumentVersion,
  type FileObjectUploadResult
} from '@/api/document'
import { listKnowledgeBases } from '@/api/knowledge'
import { addVector } from '@/api/vector'
import { isUuid, useTenantStore } from '@/stores/tenant'
import { formatBytes, newUuid } from '@/utils/format'
import type { KnowledgeBase } from '@/types/domain'

const tenant = useTenantStore()
const knowledgeBases = ref<KnowledgeBase[]>([])
const knowledgeBaseId = ref('')
const kbLoading = ref(false)
const rows = ref<DocumentRecord[]>([])
const total = ref(0)
const page = ref(1)
const size = 20
const keywordInput = ref('')
const keyword = ref('')
const loading = ref(false)
const lastError = ref('')
const fileList = ref<UploadUserFile[]>([])
const uploadProgress = ref(0)
const uploading = ref(false)
const uploadedFile = ref<FileObjectUploadResult | null>(null)
const documentDialog = reactive({ visible: false, saving: false, mode: 'create' as 'create' | 'edit', id: '', displayName: '' })
const versionDialog = reactive({ visible: false, loading: false, saving: false, document: null as DocumentRecord | null, items: [] as DocumentVersion[] })
const detailDialog = reactive({ visible: false, loading: false, item: null as DocumentRecord | null })
const versionDetail = ref<DocumentVersion | null>(null)
const writer = reactive({ id: newUuid(), content: '', category: 'general', saving: false, result: '' })

const selectedFile = computed(() => fileList.value[0]?.raw)
const hasKnowledgeBases = computed(() => knowledgeBases.value.length > 0)

async function loadKnowledgeBases() {
  knowledgeBaseId.value = ''
  rows.value = []
  total.value = 0
  knowledgeBases.value = []
  if (!isUuid(tenant.tenantId)) {
    lastError.value = '请先在顶部输入数据库中已存在的租户 UUID。当前开发身份未设置。'
    return
  }
  kbLoading.value = true
  try {
    // Current backend contract requires a developer-selected tenantId. It is a query selector only,
    // not trusted identity or authorization; production tenant context must come from authentication.
    const result = await listKnowledgeBases({ tenantId: tenant.tenantId })
    knowledgeBases.value = result.items
    if (!knowledgeBases.value.some((item) => item.id === knowledgeBaseId.value)) {
      knowledgeBaseId.value = knowledgeBases.value[0]?.id ?? ''
    }
  } catch (error) {
    knowledgeBases.value = []
    knowledgeBaseId.value = ''
    lastError.value = errorMessage(error)
  } finally {
    kbLoading.value = false
  }
}

async function loadDocuments() {
  rows.value = []
  total.value = 0
  if (!knowledgeBaseId.value) return
  loading.value = true
  lastError.value = ''
  try {
    const result = await listDocuments({ knowledgeBaseId: knowledgeBaseId.value, page: page.value, size, keyword: keyword.value || undefined })
    rows.value = result.items
    total.value = result.total
  } catch (error) {
    lastError.value = errorMessage(error)
    ElMessage.error(lastError.value)
  } finally {
    loading.value = false
  }
}

function errorMessage(error: unknown) {
  return isApiError(error) ? error.message : error instanceof Error ? error.message : String(error)
}

function search() {
  page.value = 1
  keyword.value = keywordInput.value.trim()
  void loadDocuments()
}

watch(knowledgeBaseId, () => {
  // Drop the prior knowledge base's rows immediately to prevent cross-base display.
  rows.value = []
  total.value = 0
  page.value = 1
  uploadedFile.value = null
  fileList.value = []
  void loadDocuments()
})

watch(() => tenant.tenantId, () => void loadKnowledgeBases())

function onFileChange(file: UploadFile) {
  uploadedFile.value = null
  if (file.raw && file.raw.size === 0) {
    fileList.value = fileList.value.filter((item) => item.uid !== file.uid)
    ElMessage.warning('不能上传空文件')
  }
}

async function sendUpload() {
  if (!knowledgeBaseId.value || !selectedFile.value || uploading.value) return
  uploading.value = true
  uploadProgress.value = 0
  uploadedFile.value = null
  try {
    uploadedFile.value = await uploadFile(knowledgeBaseId.value, selectedFile.value, (value) => (uploadProgress.value = value))
    ElMessage.success(`文件已保存（${formatBytes(uploadedFile.value.byteSize)}）；尚未创建文档版本`)
  } catch (error) {
    ElMessage.error(`文件上传失败：${errorMessage(error)}`)
  } finally {
    uploading.value = false
  }
}

function openCreate() {
  documentDialog.mode = 'create'
  documentDialog.id = ''
  documentDialog.displayName = selectedFile.value?.name.replace(/\.[^.]+$/, '') ?? ''
  documentDialog.visible = true
}

function openEdit(row: DocumentRecord) {
  documentDialog.mode = 'edit'
  documentDialog.id = row.id
  documentDialog.displayName = row.displayName
  documentDialog.visible = true
}

async function saveDocument() {
  const name = documentDialog.displayName.trim()
  if (!name || !knowledgeBaseId.value) return
  documentDialog.saving = true
  try {
    let saved: DocumentRecord
    if (documentDialog.mode === 'edit') {
      saved = await updateDocument(documentDialog.id, knowledgeBaseId.value, { displayName: name })
    } else {
      saved = await createDocument({ knowledgeBaseId: knowledgeBaseId.value, displayName: name })
    }
    documentDialog.visible = false
    ElMessage.success(documentDialog.mode === 'create' ? '文档已创建' : '文档信息已更新')
    await loadDocuments()
    if (documentDialog.mode === 'create' && uploadedFile.value) {
      try {
        await createDocumentVersion(saved.id, {
          fileObjectId: uploadedFile.value.id,
          originalFilename: selectedFile.value?.name ?? documentDialog.displayName
        })
        uploadedFile.value = null
        fileList.value = []
        ElMessage.success('已创建待处理版本（UPLOADED）')
        await loadDocuments()
      } catch (error) {
        ElMessage.error(`文档已创建，但版本创建失败：${errorMessage(error)}`)
      }
    }
  } catch (error) {
    ElMessage.error(`保存文档失败：${errorMessage(error)}`)
  } finally {
    documentDialog.saving = false
  }
}

async function remove(row: DocumentRecord) {
  try {
    await ElMessageBox.confirm(`确定删除“${row.displayName}”吗？`, '删除文档', { type: 'warning' })
    await deleteDocument(row.id, knowledgeBaseId.value)
    ElMessage.success('文档已删除')
    await loadDocuments()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(`删除文档失败：${errorMessage(error)}`)
  }
}

async function showDetail(row: DocumentRecord) {
  detailDialog.visible = true
  detailDialog.loading = true
  detailDialog.item = null
  try {
    detailDialog.item = await getDocument(row.id, knowledgeBaseId.value)
  } catch (error) {
    detailDialog.visible = false
    ElMessage.error(`读取文档详情失败：${errorMessage(error)}`)
  } finally {
    detailDialog.loading = false
  }
}

async function showVersions(row: DocumentRecord) {
  versionDialog.document = row
  versionDialog.items = []
  versionDetail.value = null
  versionDialog.visible = true
  versionDialog.loading = true
  try {
    versionDialog.items = await listDocumentVersions(row.id)
  } catch (error) {
    ElMessage.error(`读取版本列表失败：${errorMessage(error)}`)
  } finally {
    versionDialog.loading = false
  }
}

async function addUploadedVersion() {
  const doc = versionDialog.document
  if (!doc || !uploadedFile.value || versionDialog.saving) return
  versionDialog.saving = true
  try {
    await createDocumentVersion(doc.id, { fileObjectId: uploadedFile.value.id, originalFilename: selectedFile.value?.name ?? '' })
    uploadedFile.value = null
    fileList.value = []
    ElMessage.success('新版本已创建，状态为 UPLOADED（待处理）')
    await refreshVersions(doc.id)
    await loadDocuments()
  } catch (error) {
    ElMessage.error(`创建版本失败：${errorMessage(error)}`)
  } finally {
    versionDialog.saving = false
  }
}

async function refreshVersions(documentId: string) {
  versionDialog.items = await listDocumentVersions(documentId)
  const refreshed = await getDocument(documentId, knowledgeBaseId.value)
  versionDialog.document = refreshed
  if (detailDialog.visible && detailDialog.item?.id === documentId) detailDialog.item = refreshed
}

async function activateVersion(version: DocumentVersion) {
  const doc = versionDialog.document
  if (!doc || version.status !== 'COMPLETED') return
  try {
    await setActiveDocumentVersion(doc.id, version.id)
    ElMessage.success(`已切换活动版本 V${version.versionNo}`)
    await refreshVersions(doc.id)
    await loadDocuments()
  } catch (error) {
    ElMessage.error(`切换活动版本失败：${errorMessage(error)}`)
  }
}

async function inspectVersion(version: DocumentVersion) {
  if (!versionDialog.document) return
  try {
    versionDetail.value = await getDocumentVersion(versionDialog.document.id, version.id)
  } catch (error) {
    ElMessage.error(`读取版本详情失败：${errorMessage(error)}`)
  }
}

function statusLabel(status: string) {
  return status === 'UPLOADED' ? '待处理' : status
}
function statusType(status: string): 'success' | 'warning' | 'info' | 'danger' {
  if (status === 'COMPLETED' || status === 'ACTIVE') return 'success'
  if (status === 'FAILED') return 'danger'
  return status === 'UPLOADED' ? 'warning' : 'info'
}

async function saveChunk() {
  if (!writer.content.trim()) {
    ElMessage.warning('片段内容不能为空')
    return
  }
  writer.saving = true
  writer.result = ''
  try {
    const result = await addVector({ id: writer.id.trim(), content: writer.content.trim(), tenantId: tenant.tenantId, category: writer.category.trim() || 'general' })
    writer.result = `已写入：id=${result.id}，status=${result.status}`
    ElMessage.success('片段已写入 Qdrant')
    writer.id = newUuid()
    writer.content = ''
  } catch (error) {
    ElMessage.error(`写入失败：${errorMessage(error)}`)
  } finally {
    writer.saving = false
  }
}

onMounted(() => void loadKnowledgeBases())
</script>

<template>
  <div class="app-page">
    <PageHeader title="文档管理" description="按知识库管理文档及其版本；文件上传只保存文件对象，版本需单独创建，处理与检索能力按服务端状态展示。">
      <template #actions>
        <el-button :disabled="!knowledgeBaseId" @click="loadDocuments"><el-icon><Refresh /></el-icon><span>刷新列表</span></el-button>
      </template>
    </PageHeader>

    <div class="app-panel">
      <div class="app-panel__title"><span>知识库</span><span class="app-muted">从当前开发租户的真实数据加载</span></div>
      <el-select v-model="knowledgeBaseId" :loading="kbLoading" placeholder="请选择知识库" clearable style="width: min(100%, 420px)">
        <el-option v-for="item in knowledgeBases" :key="item.id" :label="item.name" :value="item.id" />
      </el-select>
      <el-alert v-if="!kbLoading && !isUuid(tenant.tenantId)" title="请在顶部设置数据库中实际存在的租户 UUID；租户身份仅为开发期上下文，不构成认证授权。" type="info" :closable="false" style="margin-top: 12px" />
      <el-alert v-else-if="!kbLoading && !hasKnowledgeBases" title="当前租户下没有可用知识库，请先创建知识库。" type="info" :closable="false" style="margin-top: 12px" />
      <div class="app-muted" style="margin-top: 8px; font-size: 12px">tenantId 仅用于当前开发期列表查询，不是可信身份或生产级授权依据。</div>
    </div>

    <div class="app-grid app-grid--2">
      <div class="app-panel">
        <div class="app-panel__title"><span>文件上传</span><el-tag v-if="uploadedFile" size="small" type="success" effect="plain">文件已保存</el-tag></div>
        <el-upload v-model:file-list="fileList" drag :auto-upload="false" :limit="1" :disabled="!knowledgeBaseId || uploading" :on-change="onFileChange" :on-remove="() => (uploadedFile = null)">
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">把文件拖到这里，或<em>点击选择</em></div>
          <template #tip><div class="app-muted" style="font-size: 12.5px">文件类型及大小由服务端配置校验。接口：POST /api/files/upload，字段 knowledgeBaseId、file。</div></template>
        </el-upload>
        <el-progress v-if="uploading || uploadProgress > 0" :percentage="uploadProgress" style="margin-top: 12px" />
        <div style="margin-top: 12px"><el-button type="primary" :loading="uploading" :disabled="!knowledgeBaseId || !selectedFile || uploading" @click="sendUpload">上传文件</el-button></div>
        <el-alert v-if="uploadedFile" type="success" :closable="false" show-icon style="margin-top: 12px">
          <template #title>FileObject 已保存，ID：{{ uploadedFile.id }}</template>
          这不代表 DocumentVersion 已创建。新建文档时可继续创建首个版本；也可在已有文档的版本管理中添加。
        </el-alert>
      </div>

      <div class="app-panel">
        <div class="app-panel__title"><span>写入单个向量片段</span><el-tag size="small" type="success" effect="plain">已接入</el-tag></div>
        <el-form label-position="top">
          <el-form-item label="片段 id（必须是 UUID，重复 id 会覆盖同一条向量）"><el-input v-model="writer.id" class="app-mono" /></el-form-item>
          <el-form-item label="片段内容（会被 Embedding 模型转成向量）"><el-input v-model="writer.content" type="textarea" :rows="4" /></el-form-item>
          <el-form-item label="category"><el-input v-model="writer.category" placeholder="general" /></el-form-item>
        </el-form>
        <el-button type="primary" :loading="writer.saving" @click="saveChunk">写入向量库</el-button>
        <el-alert v-if="writer.result" :title="writer.result" type="success" show-icon :closable="true" style="margin-top:12px" />
        <div class="app-muted" style="margin-top:8px;font-size:12px">该已有功能仍走 vector.add；tenantId 来自开发期租户选择，不构成认证授权。</div>
      </div>
    </div>

    <div class="app-panel">
      <div class="app-panel__title"><span>文档列表</span><span class="app-muted">{{ lastError || `共 ${total} 条` }}</span></div>
      <div style="display:flex; gap:10px; margin-bottom:14px; max-width:600px">
        <el-input v-model="keywordInput" placeholder="按文档名称搜索" clearable @keyup.enter="search" />
        <el-button :disabled="!knowledgeBaseId" @click="search">搜索</el-button>
        <el-button type="primary" :disabled="!knowledgeBaseId" @click="openCreate">新建文档</el-button>
      </div>
      <el-table :data="rows" border v-loading="loading">
        <el-table-column prop="displayName" label="文档名称" min-width="200" />
        <el-table-column label="当前版本" width="110"><template #default="{ row }">{{ row.activeVersion ? `V${row.activeVersion.versionNo}` : '—' }}</template></el-table-column>
        <el-table-column prop="versionCount" label="版本数" width="90" />
        <el-table-column label="状态" width="120"><template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" min-width="170" />
        <el-table-column label="操作" min-width="260" fixed="right"><template #default="{ row }">
          <el-button text size="small" @click="showDetail(row)">详情</el-button><el-button text size="small" @click="openEdit(row)">编辑</el-button><el-button text size="small" @click="showVersions(row)">版本</el-button><el-button text size="small" type="danger" @click="remove(row)">删除</el-button>
        </template></el-table-column>
        <template #empty><span class="app-muted">{{ knowledgeBaseId ? '该知识库暂无文档' : '请先选择知识库' }}</span></template>
      </el-table>
      <div style="display:flex; justify-content:flex-end; margin-top:14px"><el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="total, prev, pager, next" @current-change="loadDocuments" /></div>
    </div>

    <el-dialog v-model="documentDialog.visible" :title="documentDialog.mode === 'create' ? '新建文档' : '编辑文档'" width="480px">
      <el-form label-position="top"><el-form-item label="文档名称" required><el-input v-model="documentDialog.displayName" maxlength="255" /></el-form-item></el-form>
      <template #footer><el-button @click="documentDialog.visible = false">取消</el-button><el-button type="primary" :loading="documentDialog.saving" @click="saveDocument">保存</el-button></template>
    </el-dialog>

    <el-dialog v-model="detailDialog.visible" title="文档详情" width="640px">
      <div v-loading="detailDialog.loading"><el-descriptions v-if="detailDialog.item" :column="2" border>
        <el-descriptions-item label="名称">{{ detailDialog.item.displayName }}</el-descriptions-item><el-descriptions-item label="状态">{{ statusLabel(detailDialog.item.status) }}</el-descriptions-item><el-descriptions-item label="文档 ID">{{ detailDialog.item.id }}</el-descriptions-item><el-descriptions-item label="版本数">{{ detailDialog.item.versionCount }}</el-descriptions-item><el-descriptions-item label="当前版本">{{ detailDialog.item.activeVersion ? `V${detailDialog.item.activeVersion.versionNo}` : '—' }}</el-descriptions-item><el-descriptions-item label="更新时间">{{ detailDialog.item.updatedAt }}</el-descriptions-item>
      </el-descriptions></div>
    </el-dialog>

    <el-dialog v-model="versionDialog.visible" :title="`版本管理${versionDialog.document ? ` · ${versionDialog.document.displayName}` : ''}`" width="850px">
      <el-table :data="versionDialog.items" border v-loading="versionDialog.loading">
        <el-table-column prop="versionNo" label="版本" width="80"><template #default="{ row }">V{{ row.versionNo }}</template></el-table-column><el-table-column prop="originalFilename" label="文件名" min-width="180" /><el-table-column prop="byteSize" label="大小" width="110"><template #default="{ row }">{{ formatBytes(row.byteSize) }}</template></el-table-column><el-table-column label="状态" width="120"><template #default="{ row }"><el-tag size="small" :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag></template></el-table-column><el-table-column label="活动版本" width="100"><template #default="{ row }">{{ versionDialog.document?.activeVersionId === row.id ? '当前' : '' }}</template></el-table-column><el-table-column label="操作" width="170"><template #default="{ row }"><el-button text size="small" @click="inspectVersion(row)">详情</el-button><el-button text size="small" :disabled="row.status !== 'COMPLETED' || versionDialog.document?.activeVersionId === row.id" @click="activateVersion(row)">设为活动</el-button></template></el-table-column>
        <template #empty><span class="app-muted">暂无版本</span></template>
      </el-table>
      <el-alert v-if="versionDetail" type="info" :closable="false" style="margin-top:12px" :title="`V${versionDetail.versionNo} · ${statusLabel(versionDetail.status)} · ${versionDetail.originalFilename}`">文件 ID：{{ versionDetail.fileObjectId }}；SHA256：{{ versionDetail.sha256 }}</el-alert>
      <div v-if="uploadedFile" class="app-muted" style="margin-top:12px">已有上传文件可加入此文档作为新版本（服务端生成版本号）。</div>
      <template #footer><el-button v-if="uploadedFile" type="primary" :loading="versionDialog.saving" @click="addUploadedVersion">将已上传文件创建为新版本</el-button><el-button @click="versionDialog.visible = false">关闭</el-button></template>
    </el-dialog>
  </div>
</template>
