<script setup lang="ts">
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
</script>
<template><section class="page-shell policy-page"><p class="eyebrow">404</p><h1>{{ t('notFound') }}</h1><p>{{ t('notFoundBody') }}</p><RouterLink to="/" class="primary-button">{{ t('backHome') }}</RouterLink></section></template>
