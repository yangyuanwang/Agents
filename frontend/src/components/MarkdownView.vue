<script setup lang="ts">
import DOMPurify from 'dompurify'
import MarkdownIt from 'markdown-it'
import { computed } from 'vue'

const props = defineProps<{ content: string }>()

// html:false 让 Markdown 里的原始 HTML 变成纯文本，再过一道 DOMPurify，
// 避免检索到的文档内容带脚本时直接执行。
const markdown = new MarkdownIt({ html: false, breaks: true, linkify: true })

const html = computed(() => DOMPurify.sanitize(markdown.render(props.content ?? '')))
</script>

<template>
  <div class="markdown-body" v-html="html" />
</template>
