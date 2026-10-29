<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { usePortalAuth, useAdminAuth } from '../stores/auth'
import { clients, errorMessage } from '../api/client'
import { safeRedirect } from '../app/navigation'

const route = useRoute(), router = useRouter(), { t } = useI18n()
const portal = usePortalAuth(), adminAuth = useAdminAuth()
const registering = computed(() => route.path === '/register')
const admin = computed(() => route.path === '/admin/login')
const form = reactive({ identifier: '', username: '', email: '', password: '', confirmPassword: '', cultureRole: 'GUIDE', adultConfirmed: false, acceptedTerms: false })
const busy = ref(false), error = ref(''), notice = ref('')
watch(() => route.path, () => { error.value = ''; form.password = ''; form.confirmPassword = '' })
async function submit() {
  error.value = ''; notice.value = ''; busy.value = true
  try {
    if (registering.value) {
      if (form.password !== form.confirmPassword) { error.value = t('passwordMismatch'); return }
      if (!form.acceptedTerms) { error.value = t('consentRequired'); return }
      if (new TextEncoder().encode(form.password).length > 72) { error.value = t('passwordHint'); return }
      await clients.portal.post('/auth/register', { username: form.username, email: form.email, password: form.password, cultureRole: form.cultureRole, adultConfirmed: form.adultConfirmed, acceptedTerms: true, termsVersion: '2026-10' })
      notice.value = t('registered'); form.identifier = form.username; await router.push('/login')
    } else {
      const auth = admin.value ? adminAuth : portal
      await auth.login(form.identifier, form.password)
      await router.replace(safeRedirect(route.query.redirect, admin.value))
    }
  } catch (cause) { error.value = errorMessage(cause, t('loginError')) }
  finally { busy.value = false }
}
</script>

<template>
  <div class="auth-shell">
    <section class="season-panel" aria-labelledby="brand-story">
      <p class="eyebrow">A CULTURAL JOURNEY THROUGH TIME</p>
      <h1 id="brand-story">{{ t('welcome') }}</h1>
      <p class="season-intro">{{ t('welcomeBody') }}</p>
      <div class="season-art" aria-hidden="true">
        <svg viewBox="0 0 440 440" role="presentation">
          <defs><linearGradient id="ink" x1="0" y1="0" x2="1" y2="1"><stop stop-color="#347c66"/><stop offset="1" stop-color="#163e37"/></linearGradient></defs>
          <circle cx="220" cy="220" r="181" class="orbit-outer"/><circle cx="220" cy="220" r="153" class="orbit-inner"/>
          <path d="M220 70a150 150 0 0 1 150 150H220Z" fill="#b94d3c" opacity=".16"/>
          <path d="M370 220a150 150 0 0 1-150 150V220Z" fill="#b68c43" opacity=".23"/>
          <path d="M220 370A150 150 0 0 1 70 220h150Z" fill="#354d78" opacity=".13"/>
          <path d="M70 220A150 150 0 0 1 220 70v150Z" fill="#347c66" opacity=".13"/>
          <path d="M126 300c36-25 65-69 91-141m-31 74c-48-2-58-23-53-56 33 8 53 21 53 56Zm20-45c-4-39 5-65 36-79 13 31 5 59-36 79Zm-38 80c33-2 58-22 64-51-33 2-55 19-64 51Z" fill="url(#ink)"/>
          <path d="M260 159c30 25 51 69 50 128m-8-28c34-10 48-35 42-65-29 15-42 35-42 65Zm-7-36c-27-2-41-21-43-46 25 4 40 17 43 46Z" fill="#b68c43" opacity=".8"/>
          <circle cx="220" cy="39" r="4" fill="#347c66"/><circle cx="401" cy="220" r="4" fill="#b94d3c"/><circle cx="220" cy="401" r="4" fill="#b68c43"/><circle cx="39" cy="220" r="4" fill="#354d78"/>
        </svg>
        <span class="season-word north">{{ t('spring') }}</span><span class="season-word east">{{ t('summer') }}</span><span class="season-word south">{{ t('autumn') }}</span><span class="season-word west">{{ t('winter') }}</span>
      </div>
      <p class="season-signature">{{ t('tagline') }}</p>
    </section>
    <section class="auth-form-panel">
      <div class="form-heading"><p class="eyebrow">{{ admin ? 'SUISHI WORKSPACE' : 'BEGIN YOUR JOURNEY' }}</p>
        <h2>{{ registering ? t('registerTitle') : admin ? t('adminTitle') : t('loginTitle') }}</h2>
        <p>{{ registering ? t('registerHint') : admin ? t('adminHint') : t('loginHint') }}</p>
      </div>
      <p v-if="notice" class="notice success" role="status">{{ notice }}</p>
      <p v-if="error" class="notice error" role="alert">{{ error }}</p>
      <form @submit.prevent="submit" class="auth-form" :aria-busy="busy">
        <template v-if="registering">
          <label class="field">{{ t('username') }}<input v-model="form.username" name="username" required pattern="[A-Za-z0-9_]{3,64}" maxlength="64" autocomplete="username" :placeholder="t('usernameHint')"/></label>
          <label class="field">{{ t('email') }}<input v-model="form.email" name="email" required type="email" maxlength="254" autocomplete="email" placeholder="you@example.com"/></label>
        </template>
        <label v-else class="field">{{ t('identifier') }}<input v-model="form.identifier" name="identifier" required maxlength="254" autocomplete="username" :placeholder="t('identifier')"/></label>
        <label class="field">{{ t('password') }}<input v-model="form.password" name="password" required type="password" :minlength="registering ? 12 : undefined" :maxlength="registering ? 72 : 256" :autocomplete="registering ? 'new-password' : 'current-password'" :placeholder="registering ? t('passwordHint') : t('password')"/></label>
        <template v-if="registering">
          <label class="field">{{ t('confirmPassword') }}<input v-model="form.confirmPassword" name="confirmPassword" required type="password" minlength="12" maxlength="72" autocomplete="new-password"/></label>
          <fieldset class="role-selector"><legend>{{ t('cultureRole') }}</legend>
            <label :class="{ selected: form.cultureRole === 'GUIDE' }"><input v-model="form.cultureRole" type="radio" value="GUIDE" name="cultureRole"/><span><strong>{{ t('guide') }}</strong><small>{{ t('guideHint') }}</small></span></label>
            <label :class="{ selected: form.cultureRole === 'PARTNER' }"><input v-model="form.cultureRole" type="radio" value="PARTNER" name="cultureRole"/><span><strong>{{ t('partner') }}</strong><small>{{ t('partnerHint') }}</small></span></label>
          </fieldset>
          <label class="check-field"><input v-model="form.adultConfirmed" name="adultConfirmed" type="checkbox"/><span>{{ t('adult') }}</span></label>
          <label class="check-field"><input v-model="form.acceptedTerms" name="acceptedTerms" required type="checkbox"/><span>{{ t('agreement') }} <RouterLink to="/about/privacy" target="_blank">{{ t('privacy') }}</RouterLink></span></label>
        </template>
        <button class="primary-button" type="submit" :disabled="busy">{{ busy ? t('submitting') : registering ? t('register') : t('login') }}<span aria-hidden="true">→</span></button>
      </form>
      <p v-if="!admin" class="auth-switch">{{ registering ? t('haveAccount') : t('noAccount') }} <RouterLink :to="registering ? '/login' : '/register'">{{ registering ? t('login') : t('register') }}</RouterLink></p>
      <RouterLink :to="admin ? '/login' : '/admin/login'" class="admin-entry">{{ admin ? t('portalEntry') : t('adminEntry') }} <span aria-hidden="true">↗</span></RouterLink>
    </section>
  </div>
</template>
