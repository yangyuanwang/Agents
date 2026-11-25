import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { newId } from '@/utils/format'
import type { ChatMessage, Conversation } from '@/types/domain'

const STORAGE_KEY = 'rag-admin.conversations'

function load(): Conversation[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) {
      return []
    }
    const parsed = JSON.parse(raw) as Conversation[]
    // 后端会话接口没实现之前，所有历史都只可能来自本地暂存。
    return parsed.map((item) => ({ ...item, localOnly: true }))
  } catch {
    return []
  }
}

export const useChatStore = defineStore('chat', () => {
  const conversations = ref<Conversation[]>(load())
  const activeId = ref<string>(conversations.value[0]?.id ?? '')

  const active = computed<Conversation | undefined>(
    () => conversations.value.find((item) => item.id === activeId.value)
  )

  const sorted = computed<Conversation[]>(() =>
    [...conversations.value].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
  )

  function persist() {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(conversations.value.slice(0, 50)))
  }

  function createConversation(tenantId: string): Conversation {
    const now = new Date().toISOString()
    const conversation: Conversation = {
      id: newId('local'),
      title: '新会话',
      tenantId,
      createdAt: now,
      updatedAt: now,
      messages: [],
      localOnly: true
    }
    conversations.value.unshift(conversation)
    activeId.value = conversation.id
    persist()
    return conversation
  }

  function ensureActive(tenantId: string): Conversation {
    const current = active.value
    if (current) {
      return current
    }
    return createConversation(tenantId)
  }

  function addMessage(conversationId: string, message: ChatMessage) {
    const conversation = conversations.value.find((item) => item.id === conversationId)
    if (!conversation) {
      return
    }
    conversation.messages.push(message)
    conversation.updatedAt = new Date().toISOString()
    if (conversation.title === '新会话' && message.role === 'user') {
      conversation.title = message.content.slice(0, 24) || '新会话'
    }
    persist()
  }

  function patchMessage(conversationId: string, messageId: string, patch: Partial<ChatMessage>) {
    const conversation = conversations.value.find((item) => item.id === conversationId)
    const message = conversation?.messages.find((item) => item.id === messageId)
    if (!message) {
      return
    }
    Object.assign(message, patch)
    if (conversation) {
      conversation.updatedAt = new Date().toISOString()
    }
    persist()
  }

  function removeConversation(id: string) {
    conversations.value = conversations.value.filter((item) => item.id !== id)
    if (activeId.value === id) {
      activeId.value = conversations.value[0]?.id ?? ''
    }
    persist()
  }

  function clearAll() {
    conversations.value = []
    activeId.value = ''
    localStorage.removeItem(STORAGE_KEY)
  }

  return {
    conversations,
    sorted,
    activeId,
    active,
    createConversation,
    ensureActive,
    addMessage,
    patchMessage,
    removeConversation,
    clearAll,
    persist
  }
})
