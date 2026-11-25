<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

import MarkdownView from '@/components/MarkdownView.vue'
import PendingNotice from '@/components/PendingNotice.vue'
import SearchResultList from '@/components/SearchResultList.vue'
import { isApiError, isPendingError } from '@/api/http'
import { streamChat } from '@/api/chat'
import { searchVectors } from '@/api/vector'
import { useChatStore } from '@/stores/chat'
import { useTenantStore } from '@/stores/tenant'
import { formatClock, newId, truncate } from '@/utils/format'
import type { ChatMessage, Conversation } from '@/types/domain'
import type { VectorSearchResult } from '@/types/vector'

type Mode = 'retrieve' | 'generate'

const chat = useChatStore()
const tenant = useTenantStore()

const mode = ref<Mode>('retrieve')
const draft = ref('')
const config = reactive({ topK: 4 })

const streaming = ref(false)
const liveMessageId = ref('')
const liveText = ref('')
const disconnected = ref('')
const generatorPending = ref(false)

let controller: AbortController | null = null
const scroller = ref<HTMLElement | null>(null)

const active = computed<Conversation | undefined>(() => chat.active)
const messages = computed<ChatMessage[]>(() => active.value?.messages ?? [])

const modeTip = computed(() =>
  mode.value === 'retrieve'
    ? '仅检索模式：直接调用已实现的 POST /api/vector/search，回答正文就是 Qdrant 命中的真实片段，引用来源可展开核对。'
    : '生成模式：走 POST /api/chat/stream 的 SSE 通道。后端还没有对话接口，发送后会明确提示待开发，不会伪造模型回答。'
)

function canSend(): boolean {
  return draft.value.trim().length > 0 && !streaming.value
}

async function scrollToBottom() {
  await nextTick()
  const el = scroller.value
  if (el) {
    el.scrollTop = el.scrollHeight
  }
}

function pushUser(text: string): ChatMessage {
  const message: ChatMessage = {
    id: newId('msg'),
    role: 'user',
    content: text,
    createdAt: new Date().toISOString()
  }
  return message
}

/** 已实现链路：真实检索 + 真实片段作为回答。 */
async function runRetrieve(conversationId: string, question: string) {
  const placeholder: ChatMessage = {
    id: newId('msg'),
    role: 'assistant',
    content: '',
    createdAt: new Date().toISOString(),
    streaming: true
  }
  chat.addMessage(conversationId, placeholder)

  try {
    const results = await searchVectors({ query: question, tenantId: tenant.tenantId, topK: config.topK })
    const lines: string[] = []
    lines.push(`从 Qdrant 命中 **${results.length}** 个片段（topK=${config.topK}，tenantId=\`${tenant.tenantId}\`）：`)
    lines.push('')
    if (results.length === 0) {
      lines.push('> 没有命中任何片段。可能是该租户下还没有写入向量，或查询词语与库内内容差距较大。')
    }
    results.forEach((item: VectorSearchResult, index: number) => {
      lines.push(`${index + 1}. \`score=${item.score.toFixed(4)}\` ${truncate(item.content, 160)}`)
    })

    chat.patchMessage(conversationId, placeholder.id, {
      content: lines.join('\n'),
      references: results,
      streaming: false
    })
  } catch (error) {
    const message = isApiError(error) ? error.message : String(error)
    chat.patchMessage(conversationId, placeholder.id, {
      streaming: false,
      error: message,
      content: `检索失败：${message}`
    })
  } finally {
    await scrollToBottom()
  }
}

/** 待实现链路：SSE 客户端已就绪，等后端补 ChatController。 */
async function runGenerate(conversationId: string, question: string) {
  const placeholder: ChatMessage = {
    id: newId('msg'),
    role: 'assistant',
    content: '',
    createdAt: new Date().toISOString(),
    streaming: true
  }
  chat.addMessage(conversationId, placeholder)

  liveMessageId.value = placeholder.id
  liveText.value = ''
  disconnected.value = ''
  generatorPending.value = false
  controller = new AbortController()
  streaming.value = true

  let finished = false

  try {
    await streamChat(
      {
        conversationId,
        message: question,
        tenantId: tenant.tenantId,
        topK: config.topK
      },
      {
        onOpen: () => {
          // 收到响应头说明通道建立成功，开始按 token 增量渲染。
        },
        onEvent: (event) => {
          if (event.event === 'done' || event.data === '[DONE]') {
            finished = true
            return
          }
          if (event.event === 'error') {
            throw new Error(`后端返回错误事件：${event.data}`)
          }
          // 兼容三种常见载荷：纯文本增量、{delta}、{content}。
          let piece = event.data
          try {
            const parsed = JSON.parse(event.data) as { delta?: string; content?: string; text?: string }
            piece = parsed.delta ?? parsed.content ?? parsed.text ?? event.data
          } catch {
            piece = event.data
          }
          liveText.value += piece
          void scrollToBottom()
        }
      },
      controller.signal
    )

    if (!finished && liveText.value) {
      disconnected.value = '事件流在没有 done 标记的情况下结束，以上回答可能不完整。'
    }
  } catch (error) {
    if (isPendingError(error)) {
      generatorPending.value = true
      ElMessage.warning('对话生成接口尚未实现，已切到可用的仅检索模式说明')
    } else if (error instanceof DOMException && error.name === 'AbortError') {
      disconnected.value = '已手动停止生成。'
    } else {
      const message = isApiError(error) ? error.message : String(error)
      disconnected.value = message
    }
  } finally {
    streaming.value = false
    controller = null
    chat.patchMessage(conversationId, placeholder.id, {
      content: liveText.value || '（没有收到任何内容）',
      streaming: false,
      error: disconnected.value || undefined
    })
    liveMessageId.value = ''
    await scrollToBottom()
  }
}

async function send() {
  if (!canSend()) {
    return
  }
  const text = draft.value.trim()
  const conversation = chat.ensureActive(tenant.tenantId)

  draft.value = ''
  chat.addMessage(conversation.id, pushUser(text))
  await scrollToBottom()

  if (mode.value === 'generate') {
    await runGenerate(conversation.id, text)
    return
  }
  await runRetrieve(conversation.id, text)
}

function stop() {
  controller?.abort()
}

async function newConversation() {
  chat.createConversation(tenant.tenantId)
  draft.value = ''
  await scrollToBottom()
}

async function switchConversation(id: string) {
  chat.activeId = id
  await scrollToBottom()
}

async function removeConversation(target: Conversation) {
  try {
    await ElMessageBox.confirm(
      `删除会话「${target.title}」？该记录只存在浏览器本地，后端会话接口待开发。`,
      '确认删除',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  chat.removeConversation(target.id)
}

function contentOf(message: ChatMessage): string {
  if (message.id === liveMessageId.value) {
    return liveText.value
  }
  return message.content
}

watch(mode, (value) => {
  if (value === 'generate') {
    generatorPending.value = false
    disconnected.value = ''
  }
})

onBeforeUnmount(() => {
  controller?.abort()
})
</script>

<template>
  <div class="chat-page">
    <aside class="chat-side">
      <el-button type="primary" class="chat-side__new" @click="newConversation">
        <el-icon><Plus /></el-icon>
        <span>新建会话</span>
      </el-button>

      <div class="chat-side__label">
        会话记录
        <el-tooltip content="后端 GET /api/conversations 尚未实现，列表来自浏览器 localStorage，只在本机可见。">
          <el-tag size="small" type="warning" effect="plain">本地暂存</el-tag>
        </el-tooltip>
      </div>

      <el-scrollbar class="chat-side__list">
        <el-empty v-if="chat.sorted.length === 0" description="还没有会话" :image-size="60" />
        <div
          v-for="item in chat.sorted"
          :key="item.id"
          class="chat-item"
          :class="{ 'is-active': item.id === chat.activeId }"
          @click="switchConversation(item.id)"
        >
          <div class="chat-item__title">{{ item.title }}</div>
          <div class="chat-item__meta">
            <span>{{ formatClock(item.updatedAt) }}</span>
            <span>{{ item.messages.length }} 条</span>
            <el-button text size="small" @click.stop="removeConversation(item)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>
      </el-scrollbar>
    </aside>

    <section class="chat-main">
      <header class="chat-main__head">
        <el-radio-group v-model="mode" size="small">
          <el-radio-button value="retrieve">仅检索（已接入）</el-radio-button>
          <el-radio-button value="generate">生成回答（待开发）</el-radio-button>
        </el-radio-group>
        <span class="chat-main__topk">
          topK
          <el-input-number v-model="config.topK" :min="1" :max="20" size="small" />
        </span>
        <span class="app-muted chat-main__tenant">tenantId <b class="app-mono">{{ tenant.tenantId }}</b></span>
      </header>

      <div class="chat-main__tip">{{ modeTip }}</div>

      <div ref="scroller" class="chat-main__body">
        <el-empty v-if="messages.length === 0" description="开始提问，检索结果来自真实 Qdrant 数据" />

        <div v-for="message in messages" :key="message.id" class="bubble" :class="`is-${message.role}`">
          <div class="bubble__avatar">{{ message.role === 'user' ? '我' : 'AI' }}</div>
          <div class="bubble__content">
            <div v-if="message.role === 'assistant'" class="bubble__render">
              <MarkdownView v-if="contentOf(message)" :content="contentOf(message)" />
              <span v-else-if="message.streaming" class="app-muted">正在响应…</span>
            </div>
            <div v-else class="bubble__text">{{ message.content }}</div>

            <div v-if="message.error" class="bubble__error">
              <el-icon><WarningFilled /></el-icon>
              <span>{{ message.error }}</span>
            </div>

            <el-collapse v-if="message.references && message.references.length" class="bubble__refs">
              <el-collapse-item :title="`引用来源 ${message.references.length} 条（POST /api/vector/search 真实返回）`">
                <SearchResultList :results="message.references" :keyword="message.content" />
              </el-collapse-item>
            </el-collapse>

            <div v-if="message.id === liveMessageId && streaming" class="bubble__cursor">生成中…</div>
          </div>
        </div>
      </div>

      <div v-if="generatorPending" class="chat-main__alert">
        <PendingNotice endpoint-key="chat.stream" title="对话生成接口待开发" />
      </div>

      <footer class="chat-main__input">
        <el-input
          v-model="draft"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="提问后按 Enter 发送，Shift + Enter 换行"
          @keydown.enter.exact.prevent="send"
        />
        <div class="chat-main__actions">
          <span class="app-muted">
            {{ mode === 'generate' ? 'SSE 通道已就绪，等待后端 /api/chat/stream' : '当前走已实现的向量检索接口' }}
          </span>
          <el-button v-if="streaming" @click="stop">
            <el-icon><VideoPause /></el-icon>
            <span>停止</span>
          </el-button>
          <el-button type="primary" :disabled="!canSend()" :loading="streaming" @click="send">
            <el-icon><Promotion /></el-icon>
            <span>发送</span>
          </el-button>
        </div>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  gap: 14px;
  height: calc(100vh - 96px);
  min-height: 520px;
}

.chat-side {
  width: 240px;
  flex: 0 0 auto;
  background: var(--app-panel);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
  display: flex;
  flex-direction: column;
  padding: 12px;
  gap: 10px;
}

.chat-side__new {
  width: 100%;
}

.chat-side__label {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  font-size: 12.5px;
  color: var(--app-text-muted);
}

.chat-side__list {
  flex: 1;
  min-height: 0;
}

.chat-item {
  padding: 9px 10px;
  border-radius: 8px;
  cursor: pointer;
  border: 1px solid transparent;
  margin-bottom: 6px;
}

.chat-item:hover {
  background: #f8fafc;
}

.chat-item.is-active {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.chat-item__title {
  font-size: 13px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.chat-item__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11.5px;
  color: #94a3b8;
  margin-top: 2px;
}

.chat-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: var(--app-panel);
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius);
  box-shadow: var(--app-shadow);
}

.chat-main__head {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--app-border);
  flex-wrap: wrap;
}

.chat-main__topk {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 12.5px;
  color: var(--app-text-muted);
}

.chat-main__tenant {
  font-size: 12.5px;
  margin-left: auto;
}

.chat-main__tip {
  padding: 8px 16px;
  font-size: 12.5px;
  color: #475569;
  background: #f8fafc;
  border-bottom: 1px solid var(--app-border);
  line-height: 1.6;
}

.chat-main__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 18px 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.chat-main__alert {
  border-radius: 0;
}

.bubble {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.bubble.is-user {
  flex-direction: row-reverse;
}

.bubble__avatar {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  flex: 0 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  background: #e2e8f0;
  color: #334155;
}

.bubble.is-user .bubble__avatar {
  background: var(--app-brand);
  color: #fff;
}

.bubble__content {
  max-width: min(760px, 82%);
  background: #f8fafc;
  border: 1px solid var(--app-border);
  border-radius: 10px;
  padding: 10px 14px;
}

.bubble.is-user .bubble__content {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.bubble__text {
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.7;
}

.bubble__error {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  color: #b45309;
  font-size: 12.5px;
}

.bubble__refs {
  margin-top: 10px;
}

.bubble__cursor {
  margin-top: 6px;
  font-size: 12px;
  color: #94a3b8;
}

.chat-main__input {
  border-top: 1px solid var(--app-border);
  padding: 12px 16px 14px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.chat-main__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
}

.chat-main__actions .app-muted {
  margin-right: auto;
  font-size: 12px;
}

@media (max-width: 900px) {
  .chat-page {
    flex-direction: column;
    height: auto;
  }

  .chat-side {
    width: auto;
  }

  .chat-main {
    min-height: 520px;
  }
}
</style>
