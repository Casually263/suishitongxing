import { clients, portalStreamFetch } from './client'
import { readSse, type StreamEvent } from './sse'
import type { Result } from './types'

export interface ChatHistory { id: string; nodeId: string; userText: string; reply: string; language: string; mode: 'MODEL' | 'SCRIPT'; status: string; reason: string | null; attempts: number; latencyMs: number; usageComplete: boolean }
export interface AiSource { id: string; title: string; url: string }
export const aiApi = {
  async history(chapterId: string) { return (await clients.portal.get<Result<ChatHistory[]>>(`/stories/${chapterId}/ai/history`)).data.data },
  async stream(chapterId: string, body: { nodeId: string; message: string; language: string; requestId: string }, signal: AbortSignal, onEvent: (event: StreamEvent) => void) {
    const response = await portalStreamFetch(`/stories/${chapterId}/ai/chat`, { method: 'POST', headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' }, body: JSON.stringify(body), signal })
    if (!response.ok) { const error = await response.json().catch(() => null); throw new Error(error?.msg || `Chat request failed (${response.status})`) }
    if (!response.body || !response.headers.get('Content-Type')?.includes('text/event-stream')) throw new Error('Invalid chat response')
    await readSse(response.body, onEvent)
  },
}
export function aiReason(reason: string | null | undefined, english: boolean) {
  const names: Record<string, [string, string]> = {
    NO_KEY: ['未配置模型密钥，本地脚本回复', 'No model key: local scripted reply'],
    DISABLED: ['模型已关闭，本地脚本回复', 'Model disabled: local scripted reply'],
    CONFIG_MISSING: ['模型配置未完成，使用脚本', 'Model configuration incomplete: script mode'],
    NO_REVIEWED_KNOWLEDGE: ['知识卡待审校，使用脚本', 'Knowledge review pending: script mode'],
    QUOTA_EXHAUSTED: ['模型额度已用完，使用脚本', 'Model quota reached: script mode'],
    UPSTREAM_TIMEOUT: ['模型响应超时，已切换脚本', 'Model timed out: switched to script'],
    UPSTREAM_ERROR: ['模型暂时不可用，已切换脚本', 'Model unavailable: switched to script'],
    UNSAFE_OUTPUT: ['回复未通过检查，已切换脚本', 'Reply did not pass checks: script mode'],
    INPUT_BLOCKED: ['该请求由本地边界提示回应', 'Answered locally within the guide’s boundaries'],
    STALE_CONTEXT: ['剧情或知识状态已变化，使用当前脚本', 'Story or knowledge changed: current script used'],
  }
  return reason ? names[reason]?.[english ? 1 : 0] || (english ? 'Scripted fallback' : '脚本降级') : ''
}
