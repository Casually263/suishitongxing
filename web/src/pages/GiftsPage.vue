<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { storyApi, type Bilingual, type StoryState } from '../api/story'
import { errorMessage } from '../api/client'
import ExperienceState from '../components/ExperienceState.vue'
import SeasonArtwork from '../components/SeasonArtwork.vue'
const { locale } = useI18n(), loading = ref(true), error = ref(''), filter = ref('all')
const entries = ref<{ chapterId: string; chapter: Bilingual; entry: NonNullable<StoryState['submissions']>[number] }[]>([])
const w = (zh: string, en: string) => locale.value === 'en' ? en : zh
const visible = computed(() => entries.value.filter(item => filter.value === 'all' || item.entry.kind === filter.value))
async function load() {
  loading.value = true; error.value = ''
  try {
    const chapters = await storyApi.chapters()
    const saves = await Promise.all(chapters.map(async chapter => ({ chapter, state: await storyApi.state(chapter.id) })))
    entries.value = saves.flatMap(({ chapter, state }) => (state.submissions || []).map(entry => ({ chapterId: chapter.id, chapter: chapter.title, entry })))
  } catch (cause) { error.value = errorMessage(cause, w('暂时无法展开回礼，请重试。', 'Could not open your letters. Please retry.')) }
  finally { loading.value = false }
}
onMounted(load)
</script>
<template>
  <div class="experience-shell collection-page" data-season="autumn" :aria-busy="loading"><section class="collection-intro"><div><p class="eyebrow">CULTURE, IN YOUR OWN WORDS</p><h1>{{ w('以日常，回赠一份心意', 'A little of your world, in return') }}</h1><p>{{ w('把习俗写成故事，把不同的语言并排放在一起。', 'Turn a custom into a story. Let two languages sit side by side.') }}</p></div><span class="page-number">03 / 回礼</span></section>
    <section class="gift-manifesto"><div class="letter-mark" aria-hidden="true"><span>礼</span><i>LETTERS & LITTLE RITUALS</i></div><div><h2>{{ w('我的文化手记', 'My cultural letters') }}</h2><p>{{ w('这里收着你在剧情中写下的回礼与习俗体验，中文与英文原文并排呈现。', 'Here are the gifts and everyday rituals you have written in the story, with your original Chinese and English side by side.') }}</p><span class="privacy-label">{{ w('仅自己可见 · 尚未公开分享', 'PRIVATE TO YOU · NOT PUBLISHED') }}</span></div><RouterLink to="/stories/1" class="secondary-button">{{ w('回到故事，记录心意', 'Return to the story to write') }} ↗</RouterLink></section>
    <div class="collection-toolbar"><div class="filter-tabs" role="group" :aria-label="w('筛选手记', 'Filter letters')"><button v-for="item in [{key:'all',zh:'全部手记',en:'All letters'},{key:'gift',zh:'文化回礼',en:'Cultural gifts'},{key:'checkin',zh:'习俗打卡',en:'Everyday rituals'}]" :key="item.key" :aria-pressed="filter === item.key" @click="filter = item.key">{{ w(item.zh, item.en) }}</button></div><span>{{ loading || error ? '—' : visible.length }} {{ w('份记忆', 'memories') }}</span></div>
    <ExperienceState :loading="loading" :error="error" @retry="load"/>
    <template v-if="!loading && !error"><div v-if="!visible.length" class="empty-collection"><SeasonArtwork season="autumn"/><div><p class="eyebrow">A PAGE WAITING FOR YOU</p><h2>{{ w('第一份心意，还在路上', 'Your first letter is still to come') }}</h2><p>{{ w('在立春故事的回礼或打卡节点写下体验，保存后就会出现在这里。', 'Write at a gift or check-in scene in Lichun. Once saved, your words will appear here.') }}</p><RouterLink to="/stories/1" class="primary-button">{{ w('继续立春故事', 'Continue Lichun') }} →</RouterLink></div></div>
    <article v-for="(item,index) in visible" :key="item.chapterId + item.entry.nodeId" class="cultural-letter" data-testid="cultural-letter"><header><div><span class="letter-number">{{ String(index + 1).padStart(2,'0') }}</span><div><small>{{ w(item.chapter.zh,item.chapter.en) }}</small><h2>{{ item.entry.kind === 'gift' ? w('一份文化回礼', 'A cultural gift') : w('一次习俗体验', 'An everyday ritual') }}</h2></div></div><span class="privacy-label">{{ w('私有手记', 'PRIVATE LETTER') }}</span></header><div class="cultural-columns"><section lang="zh-CN"><span class="language-caption">中文 <i>CHINESE</i></span><p v-if="item.entry.content.zh">{{ item.entry.content.zh }}</p><p v-else class="missing-language">{{ w('这一页尚未填写中文。', 'No Chinese text was added.') }}</p></section><section lang="en"><span class="language-caption">English <i>英文</i></span><p v-if="item.entry.content.en">{{ item.entry.content.en }}</p><p v-else class="missing-language">{{ w('这一页尚未填写英文。', 'No English text was added.') }}</p></section></div><footer><span>{{ w('保留你的原文，不自动翻译', 'Your original words, without automatic translation') }}</span><RouterLink :to="'/stories/' + item.chapterId">{{ w('回看故事', 'Revisit the story') }} ↗</RouterLink></footer></article></template>
    <p class="availability-note">{{ w('伙伴间的习俗对照、图片上传与审核后公开分享正在筹备；当前展示的是你自己的双语手记。', 'Partner-to-partner comparisons, uploads and moderated public sharing are in preparation. This space currently shows your own bilingual letters.') }}</p>
  </div>
</template>
