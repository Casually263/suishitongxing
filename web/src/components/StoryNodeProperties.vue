<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import type { EditorConfig, EditorNode, Variable } from '../api/editor'
import type { StoryOption } from '../api/story'
const props = defineProps<{ node: EditorNode; writable: boolean; start: boolean }>()
const emit = defineEmits<{ rename: [id: string]; start: []; remove: [] }>()
const { locale } = useI18n(), keyDraft = ref(''), configText = ref(''), configError = ref('')
const w = (zh: string, en: string) => locale.value === 'en' ? en : zh
const variables: { key: Variable; zh: string; en: string }[] = [{ key: 'knowledge', zh: '知识', en: 'Knowledge' }, { key: 'harmony', zh: '默契', en: 'Harmony' }, { key: 'care', zh: '关照', en: 'Care' }]
const groups = computed(() => props.node.type === 'choice' ? [{ key: 'options' as const, label: w('可选分支', 'Choices') }] : props.node.type === 'dual' ? [{ key: 'optionsA' as const, label: w('视角 A 选项', 'Perspective A options') }, { key: 'optionsB' as const, label: w('视角 B 选项', 'Perspective B options') }] : [])
watch(() => props.node.id, () => { keyDraft.value = props.node.id; configText.value = JSON.stringify(props.node.config, null, 2); configError.value = '' }, { immediate: true })
function addOption(key: 'options' | 'optionsA' | 'optionsB') {
  const option: StoryOption = { id: `option_${crypto.randomUUID().slice(0, 8)}`, label: { zh: '', en: '' } }
  ;(props.node.content[key] ||= []).push(option)
  if (key === 'options') (props.node.config.choices ||= {})[option.id] = { effects: {} }
}
function removeOption(key: 'options' | 'optionsA' | 'optionsB', option: StoryOption) {
  props.node.content[key] = props.node.content[key]?.filter(item => item !== option)
  if (key === 'options') delete props.node.config.choices?.[option.id]
}
function choice(id: string) {
  const rules = props.node.config.choices ||= {}
  if (!Object.hasOwn(rules,id)) Object.defineProperty(rules,id,{ value:{ effects:{} }, enumerable:true, configurable:true, writable:true })
  return rules[id]!
}
function openConfig() { configText.value = JSON.stringify(props.node.config, null, 2); configError.value = '' }
function applyConfig() {
  try {
    const parsed: unknown = JSON.parse(configText.value)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) throw new Error(w('参数必须是JSON对象', 'Parameters must be a JSON object'))
    const candidate = parsed as EditorConfig
    const object = (value: unknown) => !!value && typeof value === 'object' && !Array.isArray(value)
    if (candidate.effects !== undefined && !object(candidate.effects) || candidate.choices !== undefined && !object(candidate.choices)) throw new Error(w('effects与choices必须为对象', 'effects and choices must be objects'))
    for (const choice of Object.values(candidate.choices || {})) { if (!object(choice) || choice.effects !== undefined && !object(choice.effects)) throw new Error(w('选项规则结构不合法', 'Invalid choice rule shape')); if (choice.feedback && (typeof choice.feedback.zh !== 'string' || typeof choice.feedback.en !== 'string')) throw new Error(w('反馈必须包含zh/en文本', 'Feedback needs zh/en strings')) }
    if (props.node.type === 'dual' && (!candidate.explanation || typeof candidate.explanation.zh !== 'string' || typeof candidate.explanation.en !== 'string')) throw new Error(w('双视角解释必须包含zh/en文本', 'Dual explanations need zh/en strings'))
    props.node.config = candidate; configError.value = ''
  } catch (cause) { configError.value = cause instanceof Error ? cause.message : w('JSON格式错误', 'Invalid JSON') }
}
</script>
<template>
  <div class="node-properties" data-testid="node-properties"><header><p class="eyebrow">NODE PROPERTIES</p><h2>{{ w('节点属性', 'Node properties') }}</h2><span class="status-pill">{{ node.type }}</span></header>
    <fieldset :disabled="!writable"><label>{{ w('节点标识（图内唯一）', 'Unique node key') }}<div class="key-editor"><input v-model="keyDraft" name="nodeKey" maxlength="64"/><button type="button" class="text-button" @click="emit('rename', keyDraft)">{{ w('应用', 'Apply') }}</button></div></label><button v-if="!start" type="button" class="secondary-button" @click="emit('start')">{{ w('设为故事起点', 'Set as start node') }}</button><span v-else class="editor-hint">{{ w('这是当前故事起点', 'This is the story’s start node') }}</span>
    <div v-for="field in (['title','body'] as const)" :key="field" class="bilingual-editor"><h3>{{ field === 'title' ? w('节点标题', 'Node title') : w('叙事正文', 'Story text') }}</h3><label>中文<textarea v-model="node.content[field].zh" :name="field + 'Zh'" :rows="field === 'title' ? 2 : 5" :maxlength="field === 'title' ? 200 : 10000"/></label><label>English<textarea v-model="node.content[field].en" :name="field + 'En'" :rows="field === 'title' ? 2 : 5" :maxlength="field === 'title' ? 200 : 10000"/></label></div>
    <div v-if="['narrative','gift','checkin'].includes(node.type)" class="node-effects"><h3>{{ w('完成后的变量变化', 'Variable changes on completion') }}</h3><div class="editor-number-grid"><label v-for="variable in variables" :key="variable.key">{{ w(variable.zh, variable.en) }}<input type="number" :value="node.config.effects?.[variable.key] ?? 0" min="-100" max="100" @input="(node.config.effects ||= {})[variable.key] = Number(($event.target as HTMLInputElement).value)"/></label></div></div>
    <template v-if="node.type === 'dual'"><div v-for="side in (['A','B'] as const)" :key="side" class="bilingual-editor"><h3>{{ w('专属线索', 'Private clue') }} {{ side }}</h3><label>中文<textarea v-model="node.content['clue'+side as 'clueA'|'clueB']!.zh" :name="'clue'+side+'Zh'" rows="3" maxlength="4000"/></label><label>English<textarea v-model="node.content['clue'+side as 'clueA'|'clueB']!.en" :name="'clue'+side+'En'" rows="3" maxlength="4000"/></label></div></template>
    <section v-for="group in groups" :key="group.key" class="option-editor"><h3>{{ group.label }}</h3><article v-for="option in node.content[group.key]" :key="option.id"><div class="option-key"><code>{{ option.id }}</code><button class="danger-text text-button" type="button" @click="removeOption(group.key,option)">{{ w('移除', 'Remove') }}</button></div><label>中文<input v-model="option.label.zh" maxlength="200"/></label><label>English<input v-model="option.label.en" maxlength="200"/></label><template v-if="group.key === 'options'"><div class="editor-number-grid"><label v-for="variable in variables" :key="variable.key">{{ w(variable.zh, variable.en) }}<input type="number" :value="choice(option.id).effects?.[variable.key] ?? 0" min="-100" max="100" @input="(choice(option.id).effects ||= {})[variable.key] = Number(($event.target as HTMLInputElement).value)"/></label></div><button v-if="!choice(option.id).feedback" type="button" class="text-button" @click="choice(option.id).feedback = {zh:'',en:''}">{{ w('添加双语反馈', 'Add bilingual feedback') }}</button><template v-else><label>{{ w('中文反馈', 'Chinese feedback') }}<textarea v-model="choice(option.id).feedback!.zh" rows="2" maxlength="2000"/></label><label>{{ w('英文反馈', 'English feedback') }}<textarea v-model="choice(option.id).feedback!.en" rows="2" maxlength="2000"/></label><button class="text-button" type="button" @click="delete choice(option.id).feedback">{{ w('取消反馈', 'Remove feedback') }}</button></template></template></article><button class="secondary-button" type="button" @click="addOption(group.key)">{{ w('添加选项', 'Add option') }} +</button><p class="editor-hint">{{ w('标识对应画布连线动作；添加后请为它连接默认分支。', 'Keys identify outgoing connections. Connect a fallback for every option.') }}</p></section>
    <template v-if="node.type === 'dual'"><section class="dual-config"><h3>{{ w('服务端谜题判定', 'Server-side puzzle rules') }}</h3><p class="editor-hint">{{ w('正确答案仅供有权限的管理端使用，不下发给用户或AI。', 'Answers stay in the authorized editor and are never sent to players or AI.') }}</p><label v-for="side in (['A','B'] as const)" :key="side">{{ w('正确选项', 'Correct option') }} {{ side }}<select v-model="node.config['answer'+side as 'answerA'|'answerB']"><option value="">{{ w('请选择', 'Select') }}</option><option v-for="option in node.content['options'+side as 'optionsA'|'optionsB']" :key="option.id" :value="option.id">{{ option.id }} · {{ w(option.label.zh,option.label.en) }}</option></select></label><div class="editor-number-grid"><label>{{ w('协作窗口/h', 'Window / h') }}<input v-model.number="node.config.windowHours" type="number" min="1" max="168"/></label><label>{{ w('基础默契', 'Base harmony') }}<input v-model.number="node.config.harmonyBase" type="number" min="0" max="100"/></label><label>{{ w('答对额外默契', 'Success bonus') }}<input v-model.number="node.config.harmonyBonus" type="number" min="0" max="100"/></label><label>{{ w('答对知识', 'Knowledge bonus') }}<input v-model.number="node.config.knowledgeBonus" type="number" min="0" max="100"/></label></div><label>{{ w('揭晓解释 · 中文', 'Explanation · Chinese') }}<textarea v-model="node.config.explanation!.zh" rows="3" maxlength="4000"/></label><label>{{ w('揭晓解释 · English', 'Explanation · English') }}<textarea v-model="node.config.explanation!.en" rows="3" maxlength="4000"/></label></section></template>
    <p v-if="node.type === 'ending'" class="editor-hint">{{ w('结局无出边；用户完成时由后端结算篇章印章。节点标识会作为结局标识。', 'Endings have no outgoing edges. The server settles the chapter stamp; this node key identifies the ending.') }}</p>
    <details class="advanced-config" @toggle="($event.target as HTMLDetailsElement).open && openConfig()"><summary>{{ w('节点参数 JSON', 'Node parameters JSON') }}</summary><p class="editor-hint">{{ w('编辑支持的引擎参数；未知字段会在服务端校验时拒绝，不执行脚本。', 'Only supported engine parameters are accepted. No scripts are executed.') }}</p><textarea v-model="configText" name="nodeConfig" rows="10" spellcheck="false"/><p v-if="configError" class="editor-field-error" role="alert">{{ configError }}</p><button type="button" class="secondary-button" @click="applyConfig">{{ w('应用参数到节点', 'Apply parameters') }}</button></details><button class="editor-delete" type="button" @click="emit('remove')">{{ w('删除此节点及关联连线', 'Delete node and its connections') }}</button></fieldset>
  </div>
</template>
