<script setup lang="ts">
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
</script>
<template><article class="page-shell policy-page"><p class="eyebrow">DEVELOPMENT PREVIEW · 2026-10</p><h1>{{ t('privacy') }}</h1><p>{{ t('privacyBody') }}</p><RouterLink to="/login" class="secondary-button">{{ t('back') }}</RouterLink></article></template>
