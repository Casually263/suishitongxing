import { onMounted, onUnmounted, ref } from 'vue'
import axios from 'axios'
import { useI18n } from 'vue-i18n'
import { storyApi, type StoryAction, type StoryState } from '../api/story'
import { errorMessage } from '../api/client'

// A dedicated collaboration view reads the same save and submits the same versioned commands.
export function useJourney(id: string, poll = false) {
  const state = ref<StoryState>(), loading = ref(true), busy = ref(false), error = ref('')
  const { locale } = useI18n()
  let disposed = false, timer: ReturnType<typeof setInterval> | undefined, pending: StoryAction | undefined
  async function load(silent = false) {
    if (!silent) { loading.value = true; error.value = '' }
    try { const next = await storyApi.state(id); if (!disposed) { state.value = next; error.value = '' } }
    catch (cause) { if (!disposed) error.value = errorMessage(cause, locale.value === 'en' ? 'Could not load your journey. Please retry.' : '无法读取旅程，请重试。') }
    finally { if (!disposed) loading.value = false }
  }
  async function act(action: string, answer?: string) {
    if (!state.value?.node || state.value.version === undefined || busy.value) return
    const payload = { nodeId: state.value.node.id, expectedVersion: state.value.version, action, ...(answer ? { answer } : {}) }
    if (!pending || JSON.stringify({ ...pending, requestId: undefined }) !== JSON.stringify(payload)) pending = { ...payload, requestId: crypto.randomUUID() }
    busy.value = true; error.value = ''
    try { state.value = await storyApi.choose(id, pending); pending = undefined }
    catch (cause) {
      error.value = errorMessage(cause, locale.value === 'en' ? 'Submission unconfirmed. You can safely retry.' : '提交未确认，可以安全重试。')
      if (axios.isAxiosError(cause) && cause.response) { pending = undefined; if (cause.response.status === 409) await load(true) }
    } finally { busy.value = false }
  }
  onMounted(() => {
    void load()
    if (poll) timer = setInterval(() => { if (!busy.value && state.value?.node?.type === 'dual' && state.value.partnerMode === 'HUMAN') void load(true) }, 4000)
  })
  onUnmounted(() => { disposed = true; if (timer) clearInterval(timer) })
  return { state, loading, busy, error, load, act }
}
