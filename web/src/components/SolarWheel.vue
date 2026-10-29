<script setup lang="ts">
import { useI18n } from 'vue-i18n'
import { solarTerms, useSeason } from '../experience/seasons'
import SeasonArtwork from './SeasonArtwork.vue'
defineProps<{ available: string[] }>()
const { locale } = useI18n(), { selected, term, season, select } = useSeason()
function position(index: number) {
  const angle = (index * 15 - 90) * Math.PI / 180
  return { left: `${50 + Math.cos(angle) * 44}%`, top: `${50 + Math.sin(angle) * 44}%` }
}
function keydown(event: KeyboardEvent, index: number) {
  const delta = ['ArrowRight', 'ArrowDown'].includes(event.key) ? 1 : ['ArrowLeft', 'ArrowUp'].includes(event.key) ? -1 : 0
  if (!delta && !['Home', 'End'].includes(event.key)) return
  event.preventDefault()
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? 23 : (index + delta + 24) % 24
  select(next)
  ;(event.currentTarget as HTMLElement).parentElement?.querySelector<HTMLButtonElement>(`[data-term-index="${next}"]`)?.focus()
}
</script>

<template>
  <div class="solar-wheel" :data-season="season" role="group" :aria-label="locale === 'en' ? 'Explore the 24 solar terms' : '探索二十四节气'" data-testid="solar-wheel">
    <svg class="wheel-rings" viewBox="0 0 600 600" aria-hidden="true"><circle cx="300" cy="300" r="211"/><circle cx="300" cy="300" r="203"/><circle cx="300" cy="300" r="169"/><path v-for="i in 96" :key="i" d="M300 79V85" :transform="`rotate(${i * 3.75} 300 300)`"/></svg>
    <div class="wheel-center"><SeasonArtwork :season="season"/><div class="wheel-center-label"><small>THE SEASON OF US</small><strong>{{ term.zh }}</strong><span>{{ term.en }}</span><i>{{ String(selected + 1).padStart(2, '0') }} / 24</i></div></div>
    <button v-for="item in solarTerms" :key="item.code" type="button" class="wheel-term" :class="{ selected: selected === item.index, available: available.includes(item.code) }" :style="position(item.index)" :aria-label="`${item.zh} · ${item.en}`" :aria-pressed="selected === item.index" :data-term-index="item.index" :title="locale === 'en' ? item.en : item.zh" @click="select(item.index)" @keydown="keydown($event, item.index)">{{ item.zh }}<span v-if="available.includes(item.code)" class="term-dot" aria-hidden="true"/></button>
  </div>
</template>
