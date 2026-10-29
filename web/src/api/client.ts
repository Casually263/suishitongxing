import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from 'axios'
import type { Channel } from './types'

const tokens: Record<Channel, string> = { portal: '', admin: '' }
const refreshers: Partial<Record<Channel, () => Promise<void>>> = {}
const pending: Partial<Record<Channel, Promise<void>>> = {}
export function setToken(channel: Channel, token: string) { tokens[channel] = token }
export function setRefresher(channel: Channel, renew: () => Promise<void>) { refreshers[channel] = renew }
function createClient(channel: Channel): AxiosInstance {
  const client = axios.create({ baseURL: `/api/${channel}`, withCredentials: true, timeout: 12000 })
  client.interceptors.request.use(config => {
    if (tokens[channel] && !/\/auth\/(login|register|refresh|csrf)$/.test(config.url || '')) config.headers.Authorization = `Bearer ${tokens[channel]}`
    return config
  })
  client.interceptors.response.use(response => response, async error => {
    const config = error.config as (InternalAxiosRequestConfig & { renewed?: boolean }) | undefined
    if (error.response?.status === 401 && config && !config.renewed && !config.url?.includes('/auth/') && refreshers[channel]) {
      config.renewed = true
      try {
        pending[channel] ||= refreshers[channel]!().finally(() => { pending[channel] = undefined })
        await pending[channel]
        return await client.request(config)
      } catch { tokens[channel] = '' }
    }
    return Promise.reject(error)
  })
  return client
}
export const clients = { portal: createClient('portal'), admin: createClient('admin') }
export const common = axios.create({ baseURL: '/api/common', timeout: 12000 })
// Fetch is required for streaming bodies; share the same memory-only JWT and refresh single-flight.
export async function portalStreamFetch(path: string, init: RequestInit): Promise<Response> {
  const send = () => {
    const headers = new Headers(init.headers)
    if (tokens.portal) headers.set('Authorization', `Bearer ${tokens.portal}`)
    return fetch(`/api/portal${path}`, { ...init, credentials: 'same-origin', headers })
  }
  let response = await send()
  if (response.status === 401 && refreshers.portal) {
    pending.portal ||= refreshers.portal().finally(() => { pending.portal = undefined })
    await pending.portal
    response = await send()
  }
  return response
}
export function errorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError(error)) return error.response?.data?.msg || fallback
  return fallback
}
