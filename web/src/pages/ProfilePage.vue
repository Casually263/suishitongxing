<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage, ElOption, ElSelect, ElSlider, ElSwitch } from 'element-plus'
import { clients, common, errorMessage } from '../api/client'
import type { Meta, Profile, Result } from '../api/types'

const { t, locale } = useI18n()
const form = reactive<Profile>({ displayName: '', country: null, nativeLang: 'zh', learnLang: 'en', langLevel: { zh: 0, en: 0 }, timezone: 'Asia/Shanghai', availSlots: [], tagIds: [], privacy: { showCountry: false, showAvatar: false }, notificationPreferences: { termReminders: false, partnerReminders: false }, version: 0 })
const meta = ref<Meta>(), error = ref(''), loading = ref(true), busy = ref(false)
const timezones = [...new Set(['UTC', 'Asia/Shanghai', ...Intl.supportedValuesOf('timeZone')])]
async function load() {
  loading.value = true; error.value = ''
  try { const [p, m] = await Promise.all([clients.portal.get<Result<Profile>>('/profile'), common.get<Result<Meta>>('/meta')]); Object.assign(form, p.data.data); meta.value = m.data.data }
  catch (cause) { error.value = errorMessage(cause, t('loadError')) }
  finally { loading.value = false }
}
async function save() {
  busy.value = true; error.value = ''
  const { version, ...fields } = form
  try { const response = await clients.portal.put<Result<Profile>>('/profile', { ...fields, expectedVersion: version }); Object.assign(form, response.data.data); ElMessage.success(t('saved')) }
  catch (cause) { error.value = errorMessage(cause, t('saveError')) }
  finally { busy.value = false }
}
onMounted(load)
</script>

<template>
  <div class="page-shell profile-page"><section class="page-intro"><p class="eyebrow">YOUR CULTURAL IDENTITY</p><h1>{{ t('profile') }}</h1><p>{{ t('profileBody') }}</p></section>
    <div v-if="error" class="notice error" role="alert">{{ error }} <button type="button" class="text-button" @click="load">{{ t('retry') }}</button></div>
    <div v-if="loading" class="loading-panel" role="status">{{ t('submitting') }}</div>
    <form v-else class="paper-card profile-form" @submit.prevent="save" :aria-busy="busy">
      <div class="form-grid">
        <label class="field">{{ t('displayName') }}<input v-model="form.displayName" name="displayName" required maxlength="64"/></label>
        <label class="field">{{ t('country') }}<input v-model="form.country" name="country" maxlength="50"/></label>
        <label class="field">{{ t('nativeLang') }}<select v-model="form.nativeLang" name="nativeLang"><option value="zh">{{ t('chinese') }}</option><option value="en">{{ t('english') }}</option></select></label>
        <label class="field">{{ t('learnLang') }}<select v-model="form.learnLang" name="learnLang"><option value="zh">{{ t('chinese') }}</option><option value="en">{{ t('english') }}</option></select></label>
        <div class="field full-width"><label for="timezone">{{ t('timezone') }}</label><ElSelect id="timezone" v-model="form.timezone" filterable :aria-label="t('timezone')"><ElOption v-for="zone in timezones" :key="zone" :label="zone" :value="zone"/></ElSelect></div>
        <div class="field full-width"><label for="interests">{{ t('interests') }}</label><ElSelect id="interests" v-model="form.tagIds" multiple :multiple-limit="10" :aria-label="t('interests')"><ElOption v-for="tag in meta?.interestTags" :key="tag.id" :value="tag.id" :label="locale === 'en' ? tag.name.en : tag.name.zh"/></ElSelect></div>
      </div>
      <fieldset class="profile-section"><legend>{{ t('languageLevels') }}</legend><div v-for="language in ['zh', 'en']" :key="language" class="level-row"><label :for="`level-${language}`">{{ language === 'zh' ? t('chinese') : t('english') }}</label><ElSlider :id="`level-${language}`" v-model="form.langLevel[language]!" :min="0" :max="5" :step="1" show-stops :aria-label="language === 'zh' ? t('chinese') : t('english')"/><strong>{{ form.langLevel[language] }}</strong></div></fieldset>
      <fieldset class="profile-section"><legend>{{ t('slots') }}</legend><div v-for="(slot, index) in form.availSlots" :key="index" class="slot-row"><select v-model.number="slot.dayOfWeek" :aria-label="t('day')"><option v-for="day in 7" :key="day" :value="day">{{ t('day') }} {{ day }}</option></select><input v-model="slot.start" type="time" required :aria-label="t('start')"/><span aria-hidden="true">—</span><input v-model="slot.end" type="time" required :aria-label="t('end')"/><button type="button" class="text-button danger-text" @click="form.availSlots.splice(index, 1)">{{ t('remove') }}</button></div><button type="button" class="secondary-button" :disabled="form.availSlots.length >= 28" @click="form.availSlots.push({ dayOfWeek: 1, start: '18:00', end: '20:00' })">＋ {{ t('addSlot') }}</button></fieldset>
      <fieldset class="profile-section"><legend>{{ t('privacyTitle') }}</legend><div class="switch-row"><label for="show-country">{{ t('showCountry') }}</label><ElSwitch id="show-country" v-model="form.privacy.showCountry"/></div><div class="switch-row"><label for="show-avatar">{{ t('showAvatar') }}</label><ElSwitch id="show-avatar" v-model="form.privacy.showAvatar"/></div></fieldset>
      <fieldset class="profile-section"><legend>{{ t('notifications') }}</legend><div class="switch-row"><label for="term-reminders">{{ t('termReminders') }}</label><ElSwitch id="term-reminders" v-model="form.notificationPreferences.termReminders"/></div><div class="switch-row"><label for="partner-reminders">{{ t('partnerReminders') }}</label><ElSwitch id="partner-reminders" v-model="form.notificationPreferences.partnerReminders"/></div></fieldset>
      <p class="muted">{{ t('versionNote') }}</p><button type="submit" class="primary-button" :disabled="busy">{{ busy ? t('submitting') : t('submit') }} <span aria-hidden="true">→</span></button>
    </form>
  </div>
</template>
