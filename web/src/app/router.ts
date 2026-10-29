import { createRouter, createWebHistory } from 'vue-router'
import { useAdminAuth, usePortalAuth } from '../stores/auth'
export { safeRedirect } from './navigation'

export const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to, from, savedPosition) { if (savedPosition) return savedPosition; if (to.path !== from.path) return { top: 0 } },
  routes: [
    { path: '/login', component: () => import('../pages/AuthPage.vue') },
    { path: '/register', component: () => import('../pages/AuthPage.vue') },
    { path: '/about/privacy', component: () => import('../pages/PrivacyPage.vue') },
    { path: '/', component: () => import('../pages/HomePage.vue'), meta: { authenticated: true } },
    { path: '/profile', component: () => import('../pages/ProfilePage.vue'), meta: { authenticated: true } },
    { path: '/stories/:id', component: () => import('../pages/StoryPage.vue'), meta: { authenticated: true } },
    { path: '/collaboration', component: () => import('../pages/CollaborationPage.vue'), meta: { authenticated: true } },
    { path: '/gifts', component: () => import('../pages/GiftsPage.vue'), meta: { authenticated: true } },
    { path: '/passport', component: () => import('../pages/PassportPage.vue'), meta: { authenticated: true } },
    { path: '/admin/login', component: () => import('../pages/AuthPage.vue') },
    { path: '/admin', component: () => import('../pages/AdminPage.vue'), meta: { authenticated: true, admin: true } },
    { path: '/admin/users', component: () => import('../pages/AdminPage.vue'), meta: { authenticated: true, admin: true, permission: 'user:read' } },
    { path: '/admin/stories', component: () => import('../pages/StoryEditorPage.vue'), meta: { authenticated: true, admin: true, permission: 'story:read' } },
    { path: '/admin/ai', component: () => import('../pages/AiAdminPage.vue'), meta: { authenticated: true, admin: true, permission: 'ai:read' } },
    { path: '/:pathMatch(.*)*', component: () => import('../pages/NotFoundPage.vue') },
  ],
})
router.beforeEach(async to => {
  if (!to.meta.authenticated) return true
  const auth = to.meta.admin ? useAdminAuth() : usePortalAuth()
  await auth.boot()
  if (!auth.user) return { path: to.meta.admin ? '/admin/login' : '/login', query: { redirect: to.fullPath } }
  if (to.meta.admin && !auth.user.permissions.includes('admin:access')) return '/admin/login'
  if (typeof to.meta.permission === 'string' && !auth.user.permissions.includes(to.meta.permission)) return '/admin'
  return true
})
