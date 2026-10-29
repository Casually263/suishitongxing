<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import axios from 'axios'
import { errorMessage } from '../api/client'
import { storyApi, type Bilingual, type Chapter, type KnowledgeCard, type StoryAction, type StoryState } from '../api/story'
import { usePortalAuth } from '../stores/auth'
import AiCompanion from '../components/AiCompanion.vue'
import SeasonArtwork from '../components/SeasonArtwork.vue'
import DualInteraction from '../components/DualInteraction.vue'

const route = useRoute(), { locale } = useI18n(), auth = usePortalAuth()
const id = computed(() => String(route.params.id))
const state = ref<StoryState>(), cards = ref<KnowledgeCard[]>([]), busy = ref(false), loading = ref(true), error = ref('')
const textZh = ref(''), textEn = ref(''), invite = ref(''), bilingual = ref(true), readingSize = ref(false), cardError = ref('')
const words = (zh: string, en: string) => locale.value === 'en' ? en : zh
const primary = (value?: Bilingual) => value ? locale.value === 'en' ? value.en || value.zh : value.zh || value.en : ''
const secondary = (value?: Bilingual) => value ? locale.value === 'en' ? value.zh : value.en : ''
const chapterInfo = ref<Chapter>()
const heading = computed(() => chapterInfo.value?.title || (id.value === '1' ? {zh:'立春 · 一封春信',en:'Lichun · A letter of spring'} : {zh:'节气互动篇章',en:'An interactive seasonal chapter'}))
async function loadChapter() { try { chapterInfo.value = (await storyApi.chapters()).find(c => c.id === id.value) } catch { /* Save and knowledge errors are reported independently. */ } }
const node = computed(() => state.value?.node)
const latest = computed(() => state.value?.history?.filter(event => event.feedback?.zh).at(-1)?.feedback)
const nodeType = computed(() => ({ narrative: words('叙事', 'Narrative'), choice: words('选择', 'Choice'), dual: words('双视角', 'Two perspectives'), gift: words('文化回礼', 'Cultural gift'), checkin: words('习俗打卡', 'Check-in'), ending: words('结局', 'Ending') })[node.value?.type || 'narrative'])
let timer: ReturnType<typeof setInterval> | undefined, disposed = false
let pending: StoryAction | undefined
async function refresh(silent = false) {
  if (!silent) { loading.value = true; error.value = '' }
  try { const next = await storyApi.state(id.value); if (!disposed) { state.value = next; if (silent) error.value = '' } }
  catch (cause) { if (!disposed) error.value = errorMessage(cause, words('读取存档失败，请重试。', 'Could not load your save. Please retry.')) }
  finally { if (!disposed) loading.value = false }
}
async function begin(mode: 'SCRIPT' | 'HUMAN') {
  busy.value = true; error.value = ''
  try { state.value = await storyApi.start(id.value, mode) }
  catch (cause) { error.value = errorMessage(cause, words('无法开始剧情。', 'Could not start the story.')) }
  finally { busy.value = false }
}
async function join() {
  busy.value = true; error.value = ''
  try { state.value = await storyApi.join(id.value, invite.value.trim()) }
  catch (cause) { error.value = errorMessage(cause, words('无法接受邀请。', 'Could not accept this invitation.')) }
  finally { busy.value = false }
}
async function act(action: string, answer?: string) {
  if (!node.value || state.value?.version === undefined || busy.value) return
  const payload = { nodeId: node.value.id, expectedVersion: state.value.version, action, ...(answer ? { answer } : {}), ...(['gift', 'checkin'].includes(node.value.type) ? { textZh: textZh.value, textEn: textEn.value } : {}) }
  const previousPayload = pending ? { ...pending, requestId: undefined } : undefined
  if (JSON.stringify({ ...payload, requestId: undefined }) !== JSON.stringify(previousPayload)) pending = { ...payload, requestId: crypto.randomUUID() }
  busy.value = true; error.value = ''
  try { state.value = await storyApi.choose(id.value, pending!); pending = undefined; textZh.value = ''; textEn.value = '' }
  catch (cause) {
    error.value = errorMessage(cause, words('提交未确认，请重试；重复提交不会重复加分。', 'Submission unconfirmed. Retry safely; points will not be duplicated.'))
    if (axios.isAxiosError(cause) && cause.response) { pending = undefined; if (cause.response.status === 409) await refresh(true) }
  } finally { busy.value = false }
}
async function loadCards() {
  cardError.value = ''
  try { cards.value = await storyApi.cards(id.value) } catch (cause) { cardError.value = errorMessage(cause, words('知识卡片暂时不可用。', 'Knowledge cards are unavailable.')) }
 }
onMounted(async () => {
  await Promise.all([loadCards(), refresh(), loadChapter()])
  if (disposed) return
  timer = setInterval(() => { if (!busy.value && node.value?.type === 'dual' && state.value?.partnerMode === 'HUMAN') void refresh(true) }, 4000)
})
onUnmounted(() => { disposed = true; if (timer) clearInterval(timer) })
</script>

<template>
  <div class="page-shell story-page experience-reader" data-season="spring" :class="{ 'large-reading': readingSize }" :aria-busy="busy || loading">
    <nav class="reader-breadcrumb" :aria-label="words('当前位置', 'Breadcrumb')"><RouterLink to="/">{{ words('岁时轮转', 'Seasonal wheel') }}</RouterLink><span>/</span><span>{{ primary(heading) }}</span><RouterLink to="/collaboration">{{ words('同行空间', 'Companion space') }} ↗</RouterLink></nav>
    <section class="page-intro story-intro"><div><p class="eyebrow">{{ chapterInfo?.code || 'SEASONAL STORY' }} · SUISHI STORIES</p><h1>{{ primary(heading) }}</h1><p>{{ words('双语互动篇章 · 常年练习开放 · 每步自动存档', 'A bilingual story · Year-round practice · Every step is saved') }}</p></div><div class="reading-tools"><button class="reading-size" :aria-pressed="readingSize" @click="readingSize = !readingSize" :aria-label="words('切换大字号阅读', 'Toggle larger reading text')">A<span>A</span></button><label class="story-toggle"><input v-model="bilingual" type="checkbox"/> {{ words('中英对照', 'Bilingual view') }}</label></div></section>
    <div v-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" @click="refresh()">{{ words('刷新存档', 'Reload save') }}</button></div>
    <div v-if="cardError" class="notice error" role="alert">{{ cardError }} <button class="text-button" @click="loadCards">{{ words('重新读取知识卡', 'Reload knowledge cards') }}</button></div>
    <details class="paper-card knowledge-library" :open="!state?.started"><summary>{{ words('出发前 · 知识卡与出处', 'Before you begin · Knowledge cards and sources') }}</summary><div class="knowledge-grid"><article v-for="card in cards" :key="card.id"><h3>{{ primary(card.content.title) }}</h3><p>{{ primary(card.content.body) }}</p><p v-if="bilingual" class="parallel-text" :lang="locale === 'en' ? 'zh-CN' : 'en'">{{ secondary(card.content.body) }}</p><a :href="card.sourceUrl" target="_blank" rel="noopener noreferrer">{{ card.sourceTitle }} ↗</a></article></div></details>
    <div v-if="loading" class="loading-panel" role="status">{{ words('正在读取存档…', 'Loading your save…') }}</div>
    <section v-else-if="state && !state.started" class="paper-card story-start"><SeasonArtwork :label="words('山间村落与初生春芽', 'A village among mountains and new spring leaves')"/><span class="small-label">CHAPTER 01</span><h2>{{ words('让两半线索，写成一封完整的信', 'Let two halves of a clue become one letter') }}</h2><p>{{ words('可以选择小满脚本伙伴独自体验；也可以由文化向导发出邀请，另一位文化伙伴接受后共同解谜。每位用户保留独立分支与存档。', 'Play with Xiaoman, a scripted companion, or let a cultural guide invite a cultural partner to solve the puzzle together. Each person keeps their own choices and save.') }}</p><div class="story-actions"><button class="primary-button" :disabled="busy" @click="begin('SCRIPT')">{{ words('与小满脚本伙伴开始', 'Start with scripted Xiaoman') }}</button><button v-if="auth.user?.cultureRole === 'GUIDE' && auth.user.adultConfirmed" class="secondary-button" :disabled="busy" @click="begin('HUMAN')">{{ words('发起真人双视角邀请', 'Create a human invitation') }}</button></div><form v-if="auth.user?.cultureRole === 'PARTNER' && auth.user.adultConfirmed" class="story-join" @submit.prevent="join"><label for="story-invite">{{ words('接受文化向导的邀请', 'Accept a guide’s invitation') }}</label><input id="story-invite" v-model="invite" name="inviteCode" required pattern="[a-f0-9]{32}" maxlength="32" :placeholder="words('粘贴32位邀请代码', 'Paste a 32-character invitation code')"/><button class="secondary-button" :disabled="busy" type="submit">{{ words('接受并开始', 'Accept and begin') }}</button></form><p class="muted">{{ words('模式在创建存档时确定。真人协作超时后，可以延长窗口、切换脚本伙伴或跳过。', 'Choose a mode when creating your save. If human collaboration times out, extend the window, switch to a script companion or skip.') }}</p></section>
    <template v-else-if="node && state">
      <aside v-if="state.inviteCode" class="notice story-invitation"><strong>{{ words('给文化伙伴的邀请代码（48小时有效）', 'Invitation for a cultural partner (valid for 48 hours)') }}</strong><code>{{ state.inviteCode }}</code><span>{{ words('让对方使用自己的文化伙伴账号，在本篇章开始页接受。', 'Ask your partner to sign in with their own cultural partner account and accept on this chapter’s start page.') }}</span></aside>
      <div class="story-layout"><article class="paper-card story-reader" data-testid="story-reader"><div class="story-node-meta"><span>{{ nodeType }} · {{ state.perspective === 'A' ? words('视角 A', 'Perspective A') : words('视角 B', 'Perspective B') }}</span><span>{{ words('存档版本', 'Save revision') }} {{ state.version }}</span></div>
        <div class="reader-landscape"><SeasonArtwork :label="words('原创春山与村落插画', 'Original illustration of spring mountains and a village')"/><span>{{ chapterInfo?.code || 'SUISHI STORY' }}</span></div>
        <h2 data-testid="story-node-title">{{ primary(node.title) }}</h2><p class="story-prose">{{ primary(node.body) }}</p><p v-if="bilingual" class="parallel-text story-prose">{{ secondary(node.body) }}</p>
        <aside v-if="latest" class="story-feedback" role="status"><p>{{ primary(latest) }}</p><p v-if="bilingual" class="parallel-text">{{ secondary(latest) }}</p><span v-if="latest.harmonyDelta !== undefined">{{ words('本次默契变化', 'Harmony change') }} +{{ latest.harmonyDelta }} · {{ latest.partnerMode === 'HUMAN' ? words('真人协作', 'Human collaboration') : words('脚本协作', 'Script collaboration') }}</span></aside>
        <div v-if="state.status === 'COMPLETED'" class="story-completed" data-testid="story-completed"><div class="chapter-stamp">{{ chapterInfo?.code === 'LICHUN' ? '立' : '岁' }}<br/>{{ chapterInfo?.code === 'LICHUN' ? '春' : '时' }}</div><h3>{{ chapterInfo?.code === 'LICHUN' ? words('立春篇章已完成，印章已入库', 'Lichun completed. Your stamp is recorded.') : words('篇章已完成，印章已入库', 'Chapter completed. Your stamp is recorded.') }}</h3><p>{{ words('刷新或下次登录后，这份经历仍然保留。', 'Your journey remains here after a reload or your next sign-in.') }}</p><RouterLink to="/passport" class="secondary-button">{{ words('在岁时册中查看', 'View in my journal') }} ↗</RouterLink></div>
        <template v-else>
          <button v-if="node.type === 'narrative'" class="primary-button" :disabled="busy" @click="act('continue')">{{ words('继续故事', 'Continue the story') }} →</button>
          <div v-if="node.type === 'choice'" class="story-options"><button v-for="option in node.options" :key="option.id" class="story-option" :data-testid="`option-${option.id}`" :disabled="busy" @click="act(option.id)"><span>{{ primary(option.label) }}</span><small v-if="bilingual">{{ secondary(option.label) }}</small></button></div>
          <DualInteraction v-if="node.type === 'dual'" :node="node" :state="state" :busy="busy" :bilingual="bilingual" @act="act" @refresh="refresh()"/>
          <form v-if="node.type === 'gift' || node.type === 'checkin'" class="story-entry" @submit.prevent="act('submit')"><label for="story-zh">中文</label><textarea id="story-zh" :disabled="busy" v-model="textZh" name="textZh" rows="4" maxlength="2000"/><label for="story-en">English</label><textarea id="story-en" :disabled="busy" v-model="textEn" name="textEn" rows="4" maxlength="2000"/><p class="muted">{{ words('至少填写一种语言，每段5–2000个字符。内容仅自己可见；图片上传和公开审核尚未开放。', 'Use at least one language, 5–2000 characters per entry. Visible only to you; uploads and public review are not available yet.') }}</p><button type="submit" class="primary-button" :disabled="busy || !textZh.trim() && !textEn.trim()">{{ words('私有保存并继续', 'Save privately and continue') }}</button></form>
          <button v-if="node.type === 'ending'" class="primary-button" :disabled="busy" @click="act('finish')">{{ chapterInfo?.code === 'LICHUN' ? words('完成篇章 · 领取立春印章', 'Finish · Receive the Lichun stamp') : words('完成篇章 · 领取节气印章', 'Finish · Receive a chapter stamp') }}</button>
        </template>
      </article><aside class="story-sidebar"><section class="paper-card journey-sidebar-card"><p class="small-label">YOUR JOURNEY</p><h3>{{ words('春信行囊', 'Your letter’s journey') }}</h3><dl><div><dt>{{ words('知识', 'Knowledge') }}</dt><dd>{{ state.variables?.knowledge }}</dd></div><div><dt>{{ words('默契', 'Harmony') }}</dt><dd>{{ state.variables?.harmony }}</dd></div><div><dt>{{ words('关照', 'Care') }}</dt><dd>{{ state.variables?.care }}</dd></div><div><dt>{{ words('伙伴', 'Companion') }}</dt><dd>{{ state.partnerMode === 'HUMAN' ? words('真人伙伴', 'Human partner') : words('小满 · 脚本身份', 'Xiaoman · Scripted') }}</dd></div></dl><p class="muted">{{ words('故事版本', 'Story version') }} {{ state.storyVersion }} · <span role="status">{{ busy ? words('正在保存这一步…', 'Saving this step…') : words('已自动保存', 'Automatically saved') }}</span></p></section><details class="paper-card"><summary>{{ words('已走过的故事', 'Journey history') }} ({{ state.history?.length || 0 }})</summary><ol class="story-history"><li v-for="(event, index) in state.history" :key="index"><time :datetime="event.at">{{ new Date(event.at).toLocaleTimeString(locale === 'en' ? 'en-GB' : 'zh-CN', { hour: '2-digit', minute: '2-digit' }) }}</time><span>{{ words('第', 'Step ') }}{{ index + 1 }}{{ words('次足迹', '') }} · {{ words('已保存', 'Saved') }}</span></li></ol></details></aside></div>
      <AiCompanion :chapter-id="id" :node-id="node.id" :partner-mode="state.partnerMode"/>
      <details v-if="state.submissions?.length" class="paper-card saved-entries"><summary>{{ words('我的私有打卡与回礼', 'My private check-ins and gifts') }}</summary><article v-for="entry in state.submissions" :key="entry.nodeId"><h3>{{ entry.kind === 'gift' ? words('文化回礼', 'Cultural gift') : words('习俗打卡', 'Check-in') }}</h3><div class="entry-columns"><p>{{ entry.content.zh || '—' }}</p><p>{{ entry.content.en || '—' }}</p></div><small>{{ words('仅自己可见 · 未公开发布', 'Private · Not published') }}</small></article></details>
    </template>
  </div>
</template>
