<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElConfigProvider, ElMessage } from 'element-plus'
import zhCn from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import { usePortalAuth, useAdminAuth } from './stores/auth'
import { errorMessage } from './api/client'
import { useSeason } from './experience/seasons'

const route = useRoute(), router = useRouter(), { t, locale } = useI18n()
const portal = usePortalAuth(), admin = useAdminAuth()
const isAdmin = computed(() => route.path.startsWith('/admin'))
const auth = computed(() => isAdmin.value ? admin : portal)
const busy = ref(false)
const { season } = useSeason()
const portalTheme = computed(() => route.path === '/gifts' ? 'autumn' : route.path === '/passport' ? 'winter' : route.path === '/collaboration' || route.path.startsWith('/stories/') ? 'spring' : season.value)
function switchLanguage() { locale.value = locale.value === 'zh' ? 'en' : 'zh'; localStorage.setItem('suishi.locale', locale.value) }
async function logout() {
  busy.value = true
  try { await auth.value.logout(); await router.replace(isAdmin.value ? '/admin/login' : '/login') }
  catch (error) { ElMessage.error(errorMessage(error, t('adminError'))) }
  finally { busy.value = false }
}
watch(locale, value => { document.documentElement.lang = value === 'zh' ? 'zh-CN' : 'en'; document.title = `${t('brand')} · ${t('subtitle')}` }, { immediate: true })
watch(() => auth.value.user, user => { if (!user && route.meta.authenticated) void router.replace(isAdmin.value ? '/admin/login' : '/login') })
</script>

<template>
  <ElConfigProvider :locale="locale === 'zh' ? zhCn : en">
  <div :class="{ 'portal-experience': !isAdmin }" :data-season="!isAdmin ? portalTheme : undefined">
  <a class="skip-link" href="#main-content">{{ locale === 'en' ? 'Skip to content' : '跳至主要内容' }}</a>
  <header class="site-header">
    <RouterLink :to="isAdmin ? '/admin' : '/'" class="brand" aria-label="岁时同行 Suishi">
      <img src="/favicon.svg" alt="" width="40" height="40"/><span><strong>{{ t('brand') }}</strong><small>{{ t('subtitle') }}</small></span>
    </RouterLink>
    <nav class="header-actions" aria-label="Main navigation">
      <template v-if="auth.user && route.meta.authenticated">
        <RouterLink :to="isAdmin ? '/admin' : '/'" class="nav-link">{{ isAdmin ? t('admin') : t('home') }}</RouterLink>

        <RouterLink v-if="!isAdmin" to="/stories/1" class="nav-link">{{ locale === 'en' ? 'Stories' : '入境叙事' }}</RouterLink>
        <RouterLink v-if="!isAdmin" to="/collaboration" class="nav-link">{{ locale === 'en' ? 'Companions' : '双笺同行' }}</RouterLink>
        <RouterLink v-if="!isAdmin" to="/gifts" class="nav-link">{{ locale === 'en' ? 'Cultural letters' : '文化回礼' }}</RouterLink>
        <RouterLink v-if="!isAdmin" to="/passport" class="nav-link">{{ locale === 'en' ? 'My journal' : '数字岁时册' }}</RouterLink>
        <RouterLink v-if="!isAdmin" to="/profile" class="nav-link profile-nav">{{ t('profile') }}</RouterLink>
        <RouterLink v-if="isAdmin && auth.user.permissions.includes('user:read')" to="/admin/users" class="nav-link">{{ t('users') }}</RouterLink>
        <RouterLink v-if="isAdmin && auth.user.permissions.includes('ai:read')" to="/admin/ai" class="nav-link">{{ locale === 'en' ? 'AI companion' : 'AI 伙伴' }}</RouterLink>
        <RouterLink v-if="isAdmin && auth.user.permissions.includes('story:read')" to="/admin/stories" class="nav-link">{{ locale === 'en' ? 'Story studio' : '剧情编辑器' }}</RouterLink>
        <button class="text-button" :disabled="busy" @click="logout">{{ t('logout') }}</button>
      </template>
      <button class="language-button" @click="switchLanguage" aria-label="Switch language">{{ t('locale') }}<span aria-hidden="true">↗</span></button>
    </nav>
  </header>
  <main id="main-content" tabindex="-1"><RouterView/></main>
  <footer class="site-footer"><span>{{ t('brand') }}</span><span>{{ t('tagline') }}</span><RouterLink to="/about/privacy">{{ t('privacy') }}</RouterLink></footer>
  </div>
  </ElConfigProvider>
</template>
