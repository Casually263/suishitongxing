<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { usePortalAuth } from '../stores/auth'
import { useJourney } from '../experience/useJourney'
import DualInteraction from '../components/DualInteraction.vue'
import ExperienceState from '../components/ExperienceState.vue'
import SeasonArtwork from '../components/SeasonArtwork.vue'
const { locale } = useI18n(), { state, loading, busy, error, load, act } = useJourney('1', true)
const w = (zh: string, en: string) => locale.value === 'en' ? en : zh
const auth = usePortalAuth()
const own = computed(() => state.value?.perspective || (auth.user?.cultureRole === 'PARTNER' ? 'B' : 'A'))
const dual = computed(() => state.value?.node?.type === 'dual')
</script>
<template>
  <div class="experience-shell collection-page" data-season="spring" :aria-busy="loading || busy">
    <section class="collection-intro"><div><p class="eyebrow">BETWEEN TWO PERSPECTIVES</p><h1>{{ w('各执一笺，共赴春天', 'Two perspectives. One spring.') }}</h1><p>{{ w('有些答案，需要两个人的目光。', 'Some stories become whole when seen through two pairs of eyes.') }}</p></div><span class="page-number">02 / 同行</span></section>
    <ExperienceState :loading="loading" :error="error" @retry="load()"/>
    <template v-if="state && !loading"><div class="collaboration-status"><span class="live-dot"/><strong>{{ !state.started ? w('还未启程', 'Not started yet') : state.status === 'COMPLETED' ? w('这一段同行已珍藏', 'This journey is complete') : dual ? w('正在拼合两半线索', 'Bringing two halves together') : w('各自读信，等待相遇', 'Read your letter. A meeting awaits.') }}</strong><span>{{ state.started ? state.partnerMode === 'HUMAN' ? w('真人协作', 'Human collaboration') : w('小满脚本伙伴', 'Scripted Xiaoman') : w('立春 · 一封春信', 'Lichun · A letter of spring') }}</span></div>
      <div class="perspective-grid"><article class="perspective-card own-perspective"><span class="perspective-token">{{ own }}</span><small>{{ w('我的视角', 'MY PERSPECTIVE') }}</small><h2>{{ w('我所见的一半', 'The half I can see') }}</h2><p>{{ state.started ? w('你的线索与选择只属于你。每次提交均会保存，刷新后可以继续。', 'Your clues and choices belong to you. Each submission is saved, so you can return after a reload.') : w('在立春篇章选择伙伴模式，开始自己的故事。', 'Choose a companion mode in the Lichun chapter to begin your story.') }}</p><span class="small-label">{{ state.dual?.submitted ? w('我的答案已保存', 'MY ANSWER IS SAVED') : w('独立视角 · 独立存档', 'YOUR PERSPECTIVE · YOUR SAVE') }}</span></article><article class="perspective-card locked-perspective"><span class="perspective-token">{{ own === 'A' ? 'B' : 'A' }}</span><small>{{ w('同行者的视角', 'YOUR COMPANION’S PERSPECTIVE') }}</small><h2>{{ w('留给对方的那一半', 'A half held by another') }}</h2><p>{{ w('我们保留一点未知，让交流有意义。双方提交后，才由系统揭晓共同判定。', 'A little mystery makes room for conversation. The server reveals a shared result after both answers arrive.') }}</p><span class="small-label">{{ state.partnerMode === 'SCRIPT' ? w('脚本伙伴已就位', 'SCRIPT COMPANION READY') : state.partnerJoined ? w('真人伙伴已加入', 'HUMAN PARTNER JOINED') : w('等待同行者加入', 'AWAITING A COMPANION') }}</span></article></div>
      <div v-if="state.inviteCode" class="notice story-invitation collaboration-invite"><strong>{{ w('把这枚邀请寄给文化伙伴', 'Send this invitation to a cultural partner') }}</strong><code>{{ state.inviteCode }}</code><p>{{ w('对方须使用自己的文化伙伴账号，在立春篇章接受邀请。邀请有效期48小时。', 'Your partner accepts in the Lichun chapter using their own cultural partner account. Invitations last 48 hours.') }}</p></div>
      <DualInteraction v-if="dual && state.node" :node="state.node" :state="state" :busy="busy" bilingual @act="act" @refresh="load()"/>
      <section v-else class="collaboration-next"><SeasonArtwork/><div><p class="eyebrow">A LETTER OF SPRING</p><h2>{{ state.status === 'COMPLETED' ? w('春信已有回音', 'Your spring letter has found a reply') : w('先走进故事，再与彼此相遇', 'Step into the story. Meet along the way.') }}</h2><p>{{ state.started ? w('当前还没有待提交的双视角题目。继续阅读立春篇章，协作状态会随剧情同步。', 'There is no two-perspective answer to submit right now. Continue Lichun; this space follows your story.') : w('文化向导可以发起真人邀请，文化伙伴可以接受邀请；也可选择小满脚本伙伴独自体验。', 'Guides can create human invitations and cultural partners can accept them. Or explore with scripted Xiaoman.') }}</p><RouterLink to="/stories/1" class="primary-button">{{ w('前往立春篇章', 'Open the Lichun chapter') }} ↗</RouterLink></div></section>
    </template>
  </div>
</template>
