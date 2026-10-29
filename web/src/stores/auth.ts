import { defineStore } from 'pinia'
import { ref } from 'vue'
import { clients, setRefresher, setToken } from '../api/client'
import type { Channel, Result, Session, User } from '../api/types'

function createAuthStore(channel: Channel) {
  return defineStore(`auth-${channel}`, () => {
    const user = ref<User | null>(null)
    const ready = ref(false)
    const api = clients[channel]
    let bootPromise: Promise<void> | undefined
    function accept(session: Session) { setToken(channel, session.accessToken); user.value = session.user; ready.value = true }
    function clear() { setToken(channel, ''); user.value = null }
    async function csrf() { return (await api.get<Result<{ csrfToken: string }>>('/auth/csrf')).data.data.csrfToken }
    async function refresh() {
      const renew = async () => {
        try { const token = await csrf(); accept((await api.post<Result<Session>>('/auth/refresh', {}, { headers: { 'X-CSRF-Token': token } })).data.data) }
        catch (error) { clear(); throw error }
      }
      // Serialize cookie rotation across tabs where Web Locks is available.
      if (navigator.locks) await navigator.locks.request(`suishi-refresh-${channel}`, renew)
      else await renew()
    }
    setRefresher(channel, refresh)
    async function boot() {
      if (ready.value) return
      bootPromise ||= refresh().catch(() => {}).finally(() => { ready.value = true; bootPromise = undefined })
      await bootPromise
    }
    async function login(identifier: string, password: string) { accept((await api.post<Result<Session>>('/auth/login', { identifier, password })).data.data) }
    async function logout() {
      const token = await csrf()
      await api.post('/auth/logout', {}, { headers: { 'X-CSRF-Token': token } })
      clear()
    }
    return { user, ready, boot, login, logout, clear, refresh }
  })
}
export const usePortalAuth = createAuthStore('portal')
export const useAdminAuth = createAuthStore('admin')
