<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { clients, errorMessage } from '../api/client'
import type { Result } from '../api/types'
import { useAdminAuth } from '../stores/auth'
import { aiReason } from '../api/ai'

interface Configuration { enabled: boolean; baseUrl: string; model: string; temperature: number; timeoutMs: number; retries: number; dailyLimit: number; maxRounds: number; minuteLimit: number; maxTokens: number; includeUsage: boolean; version: number }
interface ConfigView { config: Configuration; keyConfigured: boolean; allowedOrigins: string[] }
interface Card { id: string; content: { title: { zh: string; en: string }; body: { zh: string; en: string } }; sourceTitle: string; sourceUrl: string; contentHash: string; reviewStatus: string; decision?: string; note?: string; reviewedAt?: string }
interface Metadata { id: string; userId: string; mode: string; status: string; fallbackReason?: string; attempts: number; latencyMs: number; promptTokens?: number; completionTokens?: number; usageComplete: boolean }
interface Usage { today: { requests: number; attempts: number; modelReplies: number; scriptReplies: number; averageLatencyMs: number; knownPromptTokens?: number; knownCompletionTokens?: number; unknownUsageRequests: number }; latest: Metadata[] }
const { locale } = useI18n(), auth = useAdminAuth()
const words = (zh: string, en: string) => locale.value === 'en' ? en : zh
const view = ref<ConfigView>(), config = ref<Configuration>(), usage = ref<Usage>(), cards = ref<Card[]>([])
const loading = ref(false), busy = ref(false), error = ref(''), success = ref(''), checked = ref<Record<string, boolean>>({}), notes = ref<Record<string, string>>({})
async function load() {
  loading.value = true; error.value = ''
  try {
    const [settings, stats] = await Promise.all([clients.admin.get<Result<ConfigView>>('/ai/config'), clients.admin.get<Result<Usage>>('/ai/usage')])
    view.value = settings.data.data; config.value = { ...view.value.config }; usage.value = stats.data.data
    if (auth.user?.permissions.includes('knowledge:review')) cards.value = (await clients.admin.get<Result<Card[]>>('/ai/knowledge')).data.data
  } catch (cause) { error.value = errorMessage(cause, words('无法读取AI管理数据。', 'Could not load AI settings.')) }
  finally { loading.value = false }
}
async function save() {
  if (!config.value) return
  busy.value = true; error.value = ''; success.value = ''
  const c = config.value
  try {
    await clients.admin.put('/ai/config', { enabled: c.enabled, baseUrl: c.baseUrl, model: c.model, temperature: c.temperature, timeoutMs: c.timeoutMs, retries: c.retries, dailyLimit: c.dailyLimit, maxRounds: c.maxRounds, minuteLimit: c.minuteLimit, maxTokens: c.maxTokens, includeUsage: c.includeUsage, expectedVersion: c.version })
    success.value = words('模型配置已保存。', 'Model settings saved.'); await load()
  } catch (cause) { error.value = errorMessage(cause, words('保存失败，请刷新后重试。', 'Save failed. Reload and retry.')) }
  finally { busy.value = false }
}
async function review(card: Card, decision: 'APPROVED' | 'REVOKED') {
  busy.value = true; error.value = ''; success.value = ''
  try {
    await clients.admin.put(`/ai/knowledge/${card.id}/review`, { expectedHash: card.contentHash, decision, culturalChecked: !!checked.value[card.id], bilingualChecked: !!checked.value[card.id], note: notes.value[card.id] || '' })
    checked.value[card.id] = false; success.value = words('审校记录已保存。', 'Review recorded.'); await load()
  } catch (cause) { error.value = errorMessage(cause, words('审校保存失败。', 'Could not record this review.')) }
  finally { busy.value = false }
}
onMounted(load)
</script>

<template>
  <div class="page-shell ai-admin-page"><section class="page-intro"><p class="eyebrow">XIAOMAN · OPERATIONS</p><h1>{{ words('AI 故事伙伴', 'AI story companion') }}</h1><p>{{ words('模型只负责对话，剧情判定与奖励继续由服务端规则处理。', 'The model handles conversation; story rules retain control of judgment and rewards.') }}</p></section>
    <div v-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" @click="load">{{ words('重新读取', 'Reload') }}</button></div><div v-if="success" class="notice" role="status">{{ success }}</div>
    <p v-if="loading">{{ words('正在读取真实配置与统计…', 'Loading configuration and database statistics…') }}</p>
    <div v-if="view && config" class="ai-admin-grid">
      <section class="paper-card"><h2>{{ words('模型设置', 'Model settings') }}</h2><p class="ai-config-note" data-testid="ai-key-status">{{ view.keyConfigured ? words('服务端已配置模型密钥（不回显）', 'A server-side model key is configured (never displayed)') : words('服务端未配置模型密钥：使用脚本伙伴', 'No server-side model key: scripted companion is active') }}</p><p class="ai-config-note">{{ words('密钥仅从服务端 AI_API_KEY 环境变量读取，本页面不接收密钥。', 'The key is read only from the server AI_API_KEY environment variable. This form never accepts keys.') }}</p><p class="ai-config-note">{{ words('服务端允许的来源', 'Server-approved origins') }}：{{ view.allowedOrigins.join(', ') }}</p>
        <form class="ai-admin-form" @submit.prevent="save"><label class="ai-inline-checkbox"><input v-model="config.enabled" type="checkbox"/>{{ words('启用模型（关闭后全站脚本降级）', 'Enable model (disable for site-wide script fallback)') }}</label><label>{{ words('兼容服务 Base URL（包括 /v1）', 'Compatible Base URL (including /v1)') }}<input v-model="config.baseUrl" name="baseUrl" maxlength="500" placeholder="https://provider.example/v1"/></label><label>{{ words('模型名', 'Model name') }}<input v-model="config.model" name="model" maxlength="128" :placeholder="words('填写服务商提供的模型ID', 'Enter the provider’s model ID')"/></label><div class="ai-config-fields"><label>{{ words('温度', 'Temperature') }}<input v-model.number="config.temperature" type="number" min="0" max="1" step="0.05" required/></label><label>{{ words('单次超时（毫秒）', 'Attempt timeout (ms)') }}<input v-model.number="config.timeoutMs" type="number" min="500" max="15000" required/></label><label>{{ words('失败重试（0或1）', 'Retries (0 or 1)') }}<input v-model.number="config.retries" type="number" min="0" max="1" required/></label><label>{{ words('每用户每日模型调用上限', 'Daily model attempts per user') }}<input v-model.number="config.dailyLimit" type="number" min="1" max="200" required/></label><label>{{ words('每篇章最大模型对话轮数', 'Model rounds per chapter') }}<input v-model.number="config.maxRounds" type="number" min="1" max="100" required/></label><label>{{ words('每用户每分钟请求数', 'Requests per user per minute') }}<input v-model.number="config.minuteLimit" type="number" min="1" max="30" required/></label><label>{{ words('最大输出 token', 'Max output tokens') }}<input v-model.number="config.maxTokens" type="number" min="64" max="512" required/></label></div><label class="ai-inline-checkbox"><input v-model="config.includeUsage" type="checkbox"/>{{ words('请求流末尾用量（不支持的兼容服务可关闭）', 'Request final usage chunk (disable if unsupported)') }}</label><button v-if="auth.user?.permissions.includes('ai:write')" class="primary-button" type="submit" :disabled="busy">{{ words('保存模型配置', 'Save model settings') }}</button></form>
      </section>
      <section v-if="usage" class="paper-card"><h2>{{ words('今日调用 · UTC', 'Today’s usage · UTC') }}</h2><p class="ai-config-note">{{ words('真实数据库统计，包含开发测试账号；脚本回复不消耗模型额度。重试按实际模型请求计数。', 'Database statistics include development accounts. Script replies use no model quota; retries count as model attempts.') }}</p><div class="ai-metrics"><article><strong data-testid="ai-request-count">{{ usage.today.requests }}</strong><span>{{ words('对话请求', 'Chat requests') }}</span></article><article><strong>{{ usage.today.attempts }}</strong><span>{{ words('模型实际请求次数', 'Model attempts') }}</span></article><article><strong>{{ usage.today.modelReplies }}</strong><span>{{ words('模型完成回复', 'Completed model replies') }}</span></article><article><strong>{{ usage.today.scriptReplies }}</strong><span>{{ words('脚本完成回复', 'Completed script replies') }}</span></article></div><p class="ai-config-note">{{ words('平均耗时', 'Average latency') }}：{{ Math.round(usage.today.averageLatencyMs) }} ms</p><p class="ai-config-note">{{ words('已返回的 token 数（输入 / 输出）', 'Reported tokens (input / output)') }}：{{ usage.today.knownPromptTokens ?? '—' }} / {{ usage.today.knownCompletionTokens ?? '—' }}</p><p class="ai-config-note">{{ words('有模型请求但用量不完整的对话', 'Model requests with incomplete usage') }}：{{ usage.today.unknownUsageRequests }}。{{ words('未知用量不按零计费，也不据此估算价格。', 'Unknown usage is not treated as zero; no cost estimate is claimed.') }}</p><button class="text-button" :disabled="busy" @click="load">{{ words('刷新统计', 'Refresh statistics') }}</button></section>
    </div>
    <section v-if="cards.length" class="paper-card ai-admin-section"><h2>{{ words('知识卡片审校', 'Knowledge review') }}</h2><p class="ai-config-note">{{ words('仅“已核验出处”不能进入模型。逐项核对文化事实、中英文和出处后再批准；任何正文或出处修改都会使授权失效。这里记录管理员审校，不代表专业学者认证。', 'Source-checking alone is insufficient. Verify cultural facts, both languages and provenance before approving. Content or source changes invalidate approval. This records an administrator review, not a professional academic certification.') }}</p><article v-for="card in cards" :key="card.id" class="review-card"><h3>{{ card.content.title.zh }} · {{ card.content.title.en }}</h3><span class="status-pill">{{ card.decision === 'APPROVED' && card.reviewStatus === 'APPROVED' ? words('已审校，可用于AI', 'Reviewed: available to AI') : words('未获当前内容审校授权', 'Current content is not approved') }}</span><div class="entry-columns"><p>{{ card.content.body.zh }}</p><p>{{ card.content.body.en }}</p></div><a :href="card.sourceUrl" target="_blank" rel="noopener noreferrer">{{ card.sourceTitle }} ↗</a><p v-if="card.reviewedAt" class="ai-config-note">{{ words('上次审校', 'Last review') }}：{{ card.reviewedAt }} · {{ card.note }}</p><label class="ai-inline-checkbox"><input v-model="checked[card.id]" type="checkbox"/>{{ words('我已核对文化事实、中英译文与出处', 'I have checked the facts, both languages and the source') }}</label><textarea v-model="notes[card.id]" rows="2" minlength="5" maxlength="500" :placeholder="words('填写至少5个字符的审校说明', 'Review note, at least 5 characters')"/><div class="story-actions"><button class="secondary-button" :disabled="busy || !checked[card.id] || (notes[card.id]?.trim().length || 0) < 5" @click="review(card, 'APPROVED')">{{ words('批准当前内容', 'Approve current content') }}</button><button class="text-button" :disabled="busy || (notes[card.id]?.trim().length || 0) < 5" @click="review(card, 'REVOKED')">{{ words('撤销AI知识授权', 'Revoke AI knowledge approval') }}</button></div></article></section>
    <section v-if="usage" class="paper-card ai-admin-section"><h2>{{ words('最近调用（仅元数据）', 'Recent calls (metadata only)') }}</h2><div class="table-scroll"><table><thead><tr><th>{{ words('用户ID', 'User ID') }}</th><th>{{ words('来源', 'Mode') }}</th><th>{{ words('状态', 'Status') }}</th><th>{{ words('降级原因', 'Fallback reason') }}</th><th>{{ words('模型请求', 'Attempts') }}</th><th>{{ words('耗时', 'Latency') }}</th></tr></thead><tbody><tr v-for="row in usage.latest" :key="row.id"><td>{{ row.userId }}</td><td>{{ row.mode }}</td><td>{{ row.status }}</td><td>{{ aiReason(row.fallbackReason, locale === 'en') || '—' }}</td><td>{{ row.attempts }}</td><td>{{ row.latencyMs }} ms</td></tr></tbody></table></div></section>
  </div>
</template>
