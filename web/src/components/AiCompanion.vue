<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { aiApi, aiReason, type AiSource } from '../api/ai'

const props = defineProps<{ chapterId: string; nodeId: string; partnerMode?: string }>()
const { locale } = useI18n()
const en = computed(() => locale.value === 'en'), words = (zh: string, english: string) => en.value ? english : zh
interface Bubble { id: string; role: 'user' | 'assistant'; text: string; mode?: string; reason?: string | null; status?: string; sources?: AiSource[] }
const messages = ref<Bubble[]>([]), draft = ref(''), busy = ref(false), loading = ref(false), error = ref('')
const transcript = ref<HTMLElement>()
function scrollToLatest() { void nextTick(() => { if (transcript.value) transcript.value.scrollTop = transcript.value.scrollHeight }) }
let controller: AbortController | undefined, revision = 0
const mode = computed(() => [...messages.value].reverse().find(message => message.role === 'assistant')?.mode)
async function history() {
  const current = ++revision; loading.value = true
  try {
    const saved = await aiApi.history(props.chapterId)
    if (current !== revision) return
    messages.value = saved.flatMap(row => [{ id: row.id + '-u', role: 'user' as const, text: row.userText }, { id: row.id, role: 'assistant' as const, text: row.reply, mode: row.mode, reason: row.reason, status: row.status }])
    scrollToLatest()
  } catch { if (current === revision) error.value = words('对话记录暂时无法读取。', 'Chat history is temporarily unavailable.') }
  finally { if (current === revision) loading.value = false }
}
async function send(text = draft.value) {
  if (!text.trim() || busy.value) return
  error.value = ''; busy.value = true; draft.value = ''
  const requestId = crypto.randomUUID(), cancellation = new AbortController(); controller = cancellation
  messages.value.push({ id: requestId + '-u', role: 'user', text: text.trim() })
  const bubble: Bubble = { id: requestId, role: 'assistant', text: '', status: 'RUNNING' }
  messages.value.push(bubble)
  const update = () => { messages.value = [...messages.value]; scrollToLatest() }
  try {
    await aiApi.stream(props.chapterId, { nodeId: props.nodeId, message: text.trim(), language: en.value ? 'en' : 'zh', requestId }, cancellation.signal, event => {
      if (event.event === 'meta' || event.event === 'reset') {
        bubble.mode = String(event.data.mode); bubble.reason = String(event.data.reason || '')
        bubble.sources = (event.data.sources || []) as AiSource[]
        if (event.event === 'reset') bubble.text = ''
      }
      if (event.event === 'delta') bubble.text += String(event.data.text || '')
      if (event.event === 'done') { bubble.id = String(event.data.id); bubble.status = 'COMPLETED'; bubble.mode = String(event.data.mode); bubble.reason = String(event.data.reason || '') }
      update()
    })
  } catch (cause) {
    bubble.status = 'CANCELLED'
    if (!cancellation.signal.aborted) error.value = cause instanceof Error ? cause.message : words('对话未完成，请重试。', 'Chat interrupted. Please retry.')
    update()
  } finally { if (controller === cancellation) { busy.value = false; controller = undefined } }
}
function stop() { controller?.abort(); busy.value = false; controller = undefined }
watch(() => [props.nodeId, props.partnerMode, locale.value], () => { stop(); error.value = ''; void history() }, { immediate: true })
onUnmounted(() => { revision++; stop() })
</script>

<template>
  <section class="paper-card ai-companion" data-testid="ai-companion" :aria-label="words('小满故事伙伴', 'Xiaoman story companion')">
    <header class="ai-heading"><div><p class="small-label">XIAOMAN · YOUR SEASONAL GUIDE</p><h2>{{ words('和小满聊一聊', 'Talk with Xiaoman') }}</h2></div><span class="ai-identity" data-testid="ai-identity">{{ mode === 'MODEL' ? words('AI 模型', 'AI model') : mode === 'SCRIPT' ? words('脚本伙伴', 'Script companion') : words('AI / 脚本伙伴', 'AI / script companion') }}</span></header>
    <p class="ai-boundary">{{ words('小满是虚构向导。本轮消息会脱敏后发给已配置模型；未配置或不可用时，本地脚本接替。对话不会代替提交选择，也不会修改存档和奖励。', 'Xiaoman is fictional. Messages are redacted before reaching the configured model. A local script takes over when it is unavailable. Chat never submits choices or changes saves and rewards.') }}</p>
    <p v-if="partnerMode === 'HUMAN'" class="ai-boundary">{{ words('真人协作期间，小满只能转述你自己的线索。', 'During human collaboration, Xiaoman can restate only your own clue.') }}</p>
    <div v-if="error" class="notice error" role="alert">{{ error }}</div>
    <p v-if="loading" role="status">{{ words('正在读取对话记录…', 'Loading chat history…') }}</p>
    <div ref="transcript" class="ai-messages" aria-live="polite" aria-relevant="additions text" data-testid="ai-messages">
      <p v-if="!messages.length && !loading" class="ai-empty">{{ words('可以问：“请转述当前线索”，或者一起回顾已审校的节气知识。', 'Ask for the current clue, or explore reviewed seasonal knowledge together.') }}</p>
      <article v-for="message in messages" :key="message.id" class="ai-bubble" :class="message.role" :data-testid="message.role === 'assistant' ? 'ai-reply' : 'ai-question'" :data-status="message.status">
        <span class="ai-speaker">{{ message.role === 'user' ? words('你', 'You') : message.mode === 'MODEL' ? words('小满 · AI 模型', 'Xiaoman · AI model') : words('小满 · 脚本', 'Xiaoman · Script') }}</span>
        <p>{{ message.text || (message.status === 'RUNNING' && busy ? words('正在组织回复…', 'Preparing a reply…') : words('此条对话未完成，可重新提问。', 'This chat was interrupted. You can ask again.')) }}</p>
        <small v-if="message.reason">{{ aiReason(message.reason, en) }}</small>
        <small v-if="message.status === 'CANCELLED'">{{ words('已中断', 'Interrupted') }}</small>
        <div v-if="message.sources?.length" class="ai-sources"><a v-for="source in message.sources" :key="source.id" :href="source.url" target="_blank" rel="noopener noreferrer">[K{{ source.id }}] {{ source.title }} ↗</a></div>
      </article>
    </div>
    <form class="ai-chat-form" @submit.prevent="send()"><label for="ai-message">{{ words('给小满的一句话', 'Your message to Xiaoman') }}</label><textarea id="ai-message" v-model="draft" name="aiMessage" maxlength="1000" rows="3" :disabled="busy" required :placeholder="words('请不要填写姓名、联系方式或密钥', 'Please omit names, contact details and credentials')"/><div class="ai-chat-actions"><button type="submit" class="primary-button" :disabled="busy || !draft.trim()">{{ words('发送', 'Send') }}</button><button v-if="busy" type="button" class="secondary-button" @click="stop">{{ words('停止回复', 'Stop reply') }}</button><button type="button" class="text-button" :disabled="busy || loading" @click="send(words('请转述当前允许披露的线索。', 'Please restate the clue you are allowed to share.'))">{{ words('问问当前线索', 'Ask about the clue') }}</button><button type="button" class="text-button" :disabled="busy || loading" @click="history">{{ words('刷新记录', 'Reload history') }}</button></div></form>
  </section>
</template>
