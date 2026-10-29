<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { storyApi, type StoryReward } from '../api/story'
import { errorMessage } from '../api/client'
import { solarTerms } from '../experience/seasons'
import ExperienceState from '../components/ExperienceState.vue'
const { locale } = useI18n(), rewards = ref<StoryReward[]>([]), loading = ref(true), error = ref(''), year = ref('all'), selected = ref(0)
const w = (zh: string, en: string) => locale.value === 'en' ? en : zh
const years = computed(() => [...new Set(rewards.value.map(item => String(new Date(item.awardedAt).getFullYear())))].sort().reverse())
const shown = computed(() => rewards.value.filter(item => year.value === 'all' || String(new Date(item.awardedAt).getFullYear()) === year.value))
const stamps = computed(() => new Map(shown.value.map(item => [item.stampCode, item])))
const selectedTerm = computed(() => solarTerms[selected.value]!)
const reward = computed(() => stamps.value.get(selectedTerm.value.code))
const ending = computed(() => ({ spring_returns: w('春回大地', 'Spring returns'), across_windows: w('隔窗同春', 'Spring across windows'), first_bud: w('第一枚春芽', 'The first bud') })[reward.value?.endingKey || ''])
function date(value: string) { return new Date(value).toLocaleDateString(locale.value === 'en' ? 'en-GB' : 'zh-CN', { year: 'numeric', month: 'long', day: 'numeric' }) }
function print() { window.print() }
async function load() {
  loading.value = true; error.value = ''
  try { rewards.value = await storyApi.rewards() }
  catch (cause) { error.value = errorMessage(cause, w('无法读取印章记录，请重试。', 'Could not load your stamp records. Please retry.')) }
  finally { loading.value = false }
}
onMounted(load)
</script>
<template>
  <div class="experience-shell collection-page passport-page" data-season="winter" :aria-busy="loading"><section class="collection-intro"><div><p class="eyebrow">A JOURNAL OF SEASONS & ENCOUNTERS</p><h1>{{ w('把同行，收藏成册', 'Keep a season. Keep a memory.') }}</h1><p>{{ w('走过的故事，都在这里留下印记。', 'Every story you complete leaves a mark here.') }}</p></div><button class="secondary-button print-journal" :disabled="loading || !!error" @click="print">{{ w('打印岁时册', 'Print journal') }} ↗</button></section>
    <ExperienceState :loading="loading" :error="error" @retry="load"/>
    <div v-if="!loading && !error" class="passport-book"><aside class="passport-cover"><div><p class="eyebrow">SUISHI · PERSONAL COLLECTION</p><h2>岁<br/>时<br/>册</h2><span>THE SEASONAL JOURNAL</span></div><svg viewBox="0 0 200 200" aria-hidden="true"><circle cx="100" cy="100" r="78"/><circle cx="100" cy="100" r="64"/><path v-for="i in 24" :key="i" d="M100 18v12" :transform="'rotate(' + i*15 + ' 100 100)'"/><path d="M100 142V70m0 32Q64 101 62 73q33-1 38 29m0 20q36-1 38-29-33-1-38 29"/></svg><div class="passport-count"><strong data-testid="stamp-count">{{ stamps.size.toString().padStart(2,'0') }}</strong><span>/ 24<br/>{{ w('枚节气印章', 'SOLAR TERM STAMPS') }}</span></div><progress :value="stamps.size" max="24" :aria-label="w('印章收藏进度', 'Stamp collection progress')"/><p>{{ w('不必赶路，四季自有回响。', 'Take your time. Every season has a story.') }}</p></aside>
    <section class="passport-pages"><div class="passport-page-heading"><div><p class="eyebrow">COLLECT THE MOMENTS</p><h2>{{ w('二十四节气印章', 'The 24 solar-term stamps') }}</h2></div><label>{{ w('收藏年份', 'Collection year') }}<select v-model="year" name="stampYear"><option value="all">{{ w('全部年份', 'All years') }}</option><option v-for="value in years" :key="value" :value="value">{{ value }}</option></select></label></div><p class="passport-explanation">{{ w('完成篇章后自动盖章。点选一枚印章，翻看它的故事。', 'Stamps are awarded when you finish a chapter. Select one to see its story.') }}</p><div class="stamp-wall"><button v-for="term in solarTerms" :key="term.code" class="stamp-cell" :class="{ earned: stamps.has(term.code), active: selected === term.index }" :data-season="term.season" :data-stamp="term.code" :aria-pressed="selected === term.index" :aria-label="(locale === 'en' ? term.en : term.zh) + ' · ' + (stamps.has(term.code) ? w('已收藏', 'Collected') : w('未收藏', 'Not collected'))" @click="selected = term.index"><span class="stamp-symbol">{{ term.zh }}<i v-if="stamps.has(term.code)">岁时同行</i></span><small>{{ String(term.index+1).padStart(2,'0') }}</small><span>{{ locale === 'en' ? term.en : stamps.has(term.code) ? '已收藏' : '待相遇' }}</span></button></div>
    <div class="stamp-detail" aria-live="polite"><span class="mini-seal">{{ selectedTerm.zh }}</span><div><h3>{{ reward ? w('这一刻，已经珍藏', 'A moment, safely kept') : w('给未来，留一枚印记', 'A place for a future memory') }}</h3><p v-if="reward">{{ date(reward.awardedAt) }} · {{ ending || reward.endingKey }} · {{ reward.partnerMode === 'HUMAN' ? w('真人同行', 'Human companion') : w('小满脚本同行', 'Scripted Xiaoman') }}</p><p v-else>{{ w('当前筛选下还没有这枚印章。已开放篇章完成后，收藏会自动更新。', 'This stamp has not been collected in the selected period. Finish an available chapter to add it here.') }}</p></div><RouterLink v-if="reward || selectedTerm.code === 'LICHUN'" :to="'/stories/' + (reward?.chapterId || '1')" class="text-button">{{ w('翻开篇章', 'Open chapter') }} ↗</RouterLink></div><div class="book-page-number">{{ w('每一枚印章，都来自真实完成记录', 'Every stamp comes from a completed story') }}<span>01 — 24</span></div></section></div>
    <p class="availability-note">{{ w('此页按奖励记录的获得年份归档；应时特殊印记、成就徽章与分享海报尚未开放。打印时会隐藏导航与操作按钮。', 'Records are grouped by the year earned. Seasonal-window marks, achievement badges and share posters are not available yet. Navigation and controls are hidden when printing.') }}</p>
  </div>
</template>
