<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { clients, errorMessage } from '../api/client'
import type { Profile, Result } from '../api/types'
import { storyApi, type Chapter, type StoryReward } from '../api/story'
import { usePortalAuth } from '../stores/auth'
import { solarTerms, seasons, useSeason } from '../experience/seasons'
import SolarWheel from '../components/SolarWheel.vue'
import SeasonArtwork from '../components/SeasonArtwork.vue'
import ExperienceState from '../components/ExperienceState.vue'
const { locale } = useI18n(), auth = usePortalAuth(), { term, season, selected, select } = useSeason()
const profile = ref<Profile>(), chapters = ref<Chapter[]>([]), rewards = ref<StoryReward[]>([])
const loading = ref(true), error = ref('')
const w = (zh: string, en: string) => locale.value === 'en' ? en : zh
const chapter = computed(() => chapters.value.find(item => item.code === term.value.code))
const resumed = computed(() => chapters.value.find(item => item.progressStatus === 'PLAYING'))
const readiness = computed(() => profile.value ? [!!profile.value.displayName, Object.values(profile.value.langLevel).some(level => level > 0), profile.value.tagIds.length > 0, profile.value.availSlots.length > 0, !!profile.value.timezone].filter(Boolean).length * 20 : 0)
async function load() {
  loading.value = true; error.value = ''
  const results = await Promise.allSettled([clients.portal.get<Result<Profile>>('/profile'), storyApi.chapters(), storyApi.rewards()])
  const [p, c, r] = results
  if (p.status === 'fulfilled') profile.value = p.value.data.data
  if (c.status === 'fulfilled') chapters.value = c.value
  if (r.status === 'fulfilled') rewards.value = r.value
  const failed = results.find(item => item.status === 'rejected')
  if (failed?.status === 'rejected') error.value = errorMessage(failed.reason, w('部分旅程信息暂时无法读取，请重试。', 'Some journey details could not be loaded. Please retry.'))
  loading.value = false
}
onMounted(load)
</script>
<template>
  <div class="experience-home" :data-season="season" :aria-busy="loading"><div class="experience-shell">
    <div class="home-greeting"><h1>{{ w('你好，', 'Hello, ') }}{{ profile?.displayName || auth.user?.username }}</h1><span>{{ w('循时而行，相遇有期', 'A season to discover. A story to share.') }}</span></div>
    <ExperienceState :loading="loading" :error="error" @retry="load"/>
    <section class="home-stage" aria-labelledby="season-heading">
      <div class="home-editorial"><p class="eyebrow"><span class="tiny-line"/> WALKING THROUGH THE SEASONS</p><h2 id="season-heading">{{ w('把日子，', 'Find a story') }}<br/><em>{{ w('过成相遇。', 'in every season.') }}</em></h2><p class="hero-description">{{ w('从一枚节气出发，读一段东方故事。把你的日常，寄给世界另一端的同行者。', 'Begin with a solar term. Step into an Eastern story, and share a little of your everyday world with someone far away.') }}</p>
      <div class="season-tabs" role="group" :aria-label="w('选择四季主题', 'Choose a season')"><button v-for="item in seasons" :key="item.key" :aria-pressed="season === item.key" :data-season="item.key" @click="select(item.featuredIndex)"><span class="season-swatch"/>{{ w(item.zh, item.en) }}</button></div>
      <div class="term-preview" aria-live="polite"><div class="term-preview-top"><span>{{ String(selected + 1).padStart(2, '0') }} / 24</span><span class="status-pill" :class="{ inactive: !chapter }">{{ loading ? w('读取篇章中', 'Loading chapters') : chapter ? w('篇章已开放', 'Chapter available') : w('篇章筹备中', 'Chapter in preparation') }}</span></div><h3>{{ term.zh }}<small>{{ term.en }}</small></h3><p>{{ chapter ? w(chapter.intro.zh, chapter.intro.en) : w('留一页空白，等待这个节气的故事。你可以先从立春，开启我们的第一段同行。', 'A blank page awaits this season’s story. Start with Lichun for our first journey together.') }}</p><RouterLink v-if="chapter" :to="'/stories/' + chapter.id" class="primary-button">{{ chapter.progressStatus === 'NOT_STARTED' ? w('展开这一章', 'Open this chapter') : chapter.progressStatus === 'COMPLETED' ? w('重温这段春光', 'Revisit this story') : w('继续我的故事', 'Continue my story') }}<span aria-hidden="true">↗</span></RouterLink><button v-else class="text-button" @click="select(0)">{{ w('去看看立春篇章', 'Explore Lichun instead') }} →</button></div></div>
      <div class="home-calendar"><div class="calendar-heading"><span>{{ w('岁时轮转', 'THE SEASONAL WHEEL') }}</span><span>二十四节气</span></div><SolarWheel :available="chapters.map(item => item.code)"/><label class="wheel-picker">{{ w('跳转节气', 'Select a solar term') }}<select name="solarTerm" :value="selected" @change="select(Number(($event.target as HTMLSelectElement).value))"><option v-for="item in solarTerms" :key="item.code" :value="item.index">{{ item.zh }} · {{ item.en }}</option></select></label><p class="calendar-caption"><span class="term-dot"/>{{ w('圆点标记已开放篇章 · 点击节气探索', 'Dots mark available chapters · Select a term to explore') }}</p><p class="calendar-date-note">{{ w('节气文化导览 · 年度日期与倒计时尚未开放', 'A cultural guide · Annual dates and countdowns are not yet available') }}</p></div>
    </section>
    <section class="journey-ribbon" :aria-label="w('我的旅程', 'My journey')"><div><span class="ribbon-index">01</span><div><small>{{ w('故事有续', 'A STORY TO RETURN TO') }}</small><strong>{{ loading ? '…' : resumed ? w(resumed.title.zh, resumed.title.en) : w('从一封春信开始', 'Begin with a letter of spring') }}</strong></div><RouterLink :to="'/stories/' + (resumed?.id || chapters[0]?.id || '1')" :aria-label="w('打开剧情', 'Open the story')">↗</RouterLink></div><div><span class="ribbon-index">02</span><div><small>{{ w('相遇有迹', 'MOMENTS TO KEEP') }}</small><strong>{{ loading || error ? '—' : new Set(rewards.map(item => item.stampCode)).size + ' / 24' }} <span>{{ w('节气印章', 'seasonal stamps') }}</span></strong></div><RouterLink to="/passport" :aria-label="w('打开岁时册', 'Open my journal')">↗</RouterLink></div><div><span class="ribbon-index">03</span><div><small>{{ w('同行有约', 'MAKE ROOM FOR CONNECTION') }}</small><strong>{{ w('个人档案', 'Your profile') }} <span>{{ loading || error ? '—' : readiness + '%' }}</span></strong></div><RouterLink to="/profile" :aria-label="w('编辑个人档案', 'Edit my profile')">↗</RouterLink></div></section>
    <section class="season-collection"><div class="section-heading"><div><p class="eyebrow">FOUR SEASONS, SHARED STORIES</p><h2>{{ w('四季之间，皆可同行', 'A little closer, in every season') }}</h2></div><p>{{ w('选择一季，看看岁时的不同颜色。', 'Choose a season. See the wheel in a new light.') }}</p></div><div class="season-cards"><button v-for="(item, index) in seasons" :key="item.key" class="season-card" :data-season="item.key" :aria-pressed="season === item.key" @click="select(item.featuredIndex)"><SeasonArtwork :season="item.key"/><div><span>0{{ index + 1 }} / {{ item.en.toUpperCase() }}</span><h3>{{ item.zh }}</h3><p>{{ w(item.noteZh, item.noteEn) }}</p><i aria-hidden="true">↗</i></div></button></div></section>
    <section class="closing-note"><span aria-hidden="true">廿四</span><p>{{ w('同一轮四季，不同的生活。', 'One cycle of seasons. Many ways of living.') }}<br/><strong>{{ w('让文化，在一次次相遇里生长。', 'Let culture grow through the moments we share.') }}</strong></p><RouterLink to="/gifts" class="text-button">{{ w('翻看我的文化回礼', 'Open my cultural letters') }} →</RouterLink></section>
  </div></div>
</template>
