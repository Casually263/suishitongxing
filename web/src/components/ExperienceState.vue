<script setup lang="ts">
import { useI18n } from 'vue-i18n'
defineProps<{ loading?: boolean; error?: string }>()
defineEmits<{ retry: [] }>()
const { locale } = useI18n()
</script>
<template>
  <div v-if="error" class="experience-error" role="alert"><span class="state-symbol" aria-hidden="true">!</span><div><strong>{{ locale === 'en' ? 'A pause in the journey' : '旅途暂时停了一步' }}</strong><p>{{ error }}</p></div><button class="secondary-button" @click="$emit('retry')">{{ locale === 'en' ? 'Try again' : '重新加载' }}</button></div>
  <div v-else-if="loading" class="experience-loading" role="status"><span class="loading-orbit" aria-hidden="true"/><span>{{ locale === 'en' ? 'Unfolding your seasonal journal…' : '正在展开你的岁时记忆…' }}</span></div>
</template>
