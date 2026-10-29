<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElMessageBox, ElPagination } from 'element-plus'
import { clients, errorMessage } from '../api/client'
import { useAdminAuth } from '../stores/auth'
import type { Page, Result, User } from '../api/types'

const route = useRoute(), { t, locale } = useI18n(), auth = useAdminAuth()
const overview = ref<{ registeredUsers: number; generatedAt: string }>(), users = ref<Page<User>>(), page = ref(1)
const loading = ref(true), error = ref(''), changing = ref('')
async function load() {
  loading.value = true; error.value = ''
  try {
    overview.value = (await clients.admin.get<Result<{ registeredUsers: number; generatedAt: string }>>('/overview')).data.data
    if (route.path === '/admin/users') users.value = (await clients.admin.get<Result<Page<User>>>('/users', { params: { page: page.value, size: 10 } })).data.data
  } catch (cause) { error.value = errorMessage(cause, t('loadError')) }
  finally { loading.value = false }
}
async function changeStatus(user: User) {
  try {
    if (user.status === 'ACTIVE') await ElMessageBox.confirm(t('confirmDisable'), t('disable'), { type: 'warning', confirmButtonText: t('disable'), cancelButtonText: t('backHome') })
    changing.value = user.id
    await clients.admin.put(`/users/${user.id}/status`, { status: user.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE', expectedAuthVersion: user.authVersion })
    ElMessage.success(t('updated')); await load()
  } catch (cause) { if (cause !== 'cancel' && cause !== 'close') ElMessage.error(errorMessage(cause, t('adminError'))) }
  finally { changing.value = '' }
}
watch(() => route.path, () => { page.value = 1; void load() }, { immediate: true })
</script>

<template>
  <div class="page-shell admin-page"><section class="page-intro"><p class="eyebrow">SUISHI WORKSPACE</p><h1>{{ route.path === '/admin/users' ? t('users') : t('admin') }}</h1><p>{{ t('adminIntro') }}</p></section>
    <div v-if="error" class="notice error" role="alert">{{ error }} <button class="text-button" @click="load">{{ t('retry') }}</button></div>
    <div v-else-if="loading" class="loading-panel" role="status">{{ t('submitting') }}</div>
    <template v-else>
      <section v-if="overview" class="admin-metric paper-card"><div><p class="eyebrow">{{ t('realUsers') }}</p><strong class="metric-number" data-testid="registered-users">{{ overview.registeredUsers }}</strong><p>{{ t('actualData') }}</p></div><time :datetime="overview.generatedAt">{{ new Date(overview.generatedAt).toLocaleString() }}</time></section>
      <section v-if="route.path === '/admin/users' && users" class="paper-card users-section"><h2>{{ t('userList') }}</h2><div class="table-scroll"><table><thead><tr><th>{{ t('username') }}</th><th>{{ t('identity') }}</th><th>{{ t('roles') }}</th><th>{{ t('status') }}</th><th>{{ t('created') }}</th><th>{{ t('actions') }}</th></tr></thead><tbody><tr v-for="user in users.items" :key="user.id"><td><strong>{{ user.username }}</strong></td><td>{{ user.cultureRole === 'GUIDE' ? t('guide') : t('partner') }}</td><td>{{ user.roles.join(', ') }}</td><td><span class="status-pill" :class="{ inactive: user.status === 'DISABLED' }">{{ user.status === 'ACTIVE' ? t('active') : t('disabled') }}</span></td><td>{{ user.createdAt?.slice(0, 10) }}</td><td><button v-if="auth.user?.permissions.includes('user:write')" class="text-button" :class="{ 'danger-text': user.status === 'ACTIVE' }" :disabled="user.id === auth.user?.id || changing === user.id" @click="changeStatus(user)">{{ user.status === 'ACTIVE' ? t('disable') : t('enable') }}</button></td></tr></tbody></table></div><ElPagination v-model:current-page="page" :page-size="10" :total="users.total" layout="prev, pager, next, total" @current-change="load"/></section>
      <section v-else class="paper-card workspace-links"><h2>{{ t('userList') }}</h2><RouterLink v-if="auth.user?.permissions.includes('user:read')" to="/admin/users" class="primary-button">{{ t('users') }} <span aria-hidden="true">→</span></RouterLink></section>
    </template>
  </div>
</template>
