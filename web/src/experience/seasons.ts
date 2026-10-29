import { computed, ref } from 'vue'

export type Season = 'spring' | 'summer' | 'autumn' | 'winter'
export const seasons: { key: Season; featuredIndex: number; zh: string; en: string; noteZh: string; noteEn: string }[] = [
  { key: 'spring', featuredIndex: 0, zh: '春生', en: 'Spring', noteZh: '草木萌动，故事初生', noteEn: 'New growth, new beginnings' },
  { key: 'summer', featuredIndex: 9, zh: '夏长', en: 'Summer', noteZh: '日光渐盛，万物并秀', noteEn: 'Long light, a world in bloom' },
  { key: 'autumn', featuredIndex: 12, zh: '秋收', en: 'Autumn', noteZh: '金风送信，收藏相遇', noteEn: 'Gather moments in the golden light' },
  { key: 'winter', featuredIndex: 21, zh: '冬藏', en: 'Winter', noteZh: '围炉寄语，静候重逢', noteEn: 'Keep a little warmth for our next meeting' },
]
// Solar-term taxonomy only. Annual dates and opening rules must come from the server.
export const solarTerms = [
  ['LICHUN', '立春', 'Start of Spring'], ['YUSHUI', '雨水', 'Rain Water'], ['JINGZHE', '惊蛰', 'Awakening of Insects'],
  ['CHUNFEN', '春分', 'Spring Equinox'], ['QINGMING', '清明', 'Pure Brightness'], ['GUYU', '谷雨', 'Grain Rain'],
  ['LIXIA', '立夏', 'Start of Summer'], ['XIAOMAN', '小满', 'Grain Buds'], ['MANGZHONG', '芒种', 'Grain in Ear'],
  ['XIAZHI', '夏至', 'Summer Solstice'], ['XIAOSHU', '小暑', 'Minor Heat'], ['DASHU', '大暑', 'Major Heat'],
  ['LIQIU', '立秋', 'Start of Autumn'], ['CHUSHU', '处暑', 'End of Heat'], ['BAILU', '白露', 'White Dew'],
  ['QIUFEN', '秋分', 'Autumn Equinox'], ['HANLU', '寒露', 'Cold Dew'], ['SHUANGJIANG', '霜降', 'Frost’s Descent'],
  ['LIDONG', '立冬', 'Start of Winter'], ['XIAOXUE', '小雪', 'Minor Snow'], ['DAXUE', '大雪', 'Major Snow'],
  ['DONGZHI', '冬至', 'Winter Solstice'], ['XIAOHAN', '小寒', 'Minor Cold'], ['DAHAN', '大寒', 'Major Cold'],
].map(([code, zh, en], index) => ({ code: code!, zh: zh!, en: en!, index, season: seasons[Math.floor(index / 6)]!.key }))

const selected = ref(0)
export function useSeason() {
  const term = computed(() => solarTerms[selected.value]!)
  const season = computed(() => term.value.season)
  function select(index: number) { selected.value = (index + 24) % 24 }
  return { selected, term, season, select }
}
