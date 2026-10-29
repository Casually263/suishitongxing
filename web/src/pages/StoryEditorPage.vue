<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, shallowRef, watch } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElDialog, ElMessageBox } from 'element-plus'
import { VueFlow, useVueFlow, MarkerType, type Node, type Edge, type Connection, type NodeDragEvent } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import { editorApi, type Draft, type EditorChapter, type EditorGraph, type EditorNode, type NodeKind, type Preview, type Publication, type Validation } from '../api/editor'
import { errorMessage } from '../api/client'
import { useAdminAuth } from '../stores/auth'
import { actions, newNode, nodeKinds, removeNode, renameNode } from '../experience/editor-model'
import StoryCanvasNode, { type CanvasData } from '../components/StoryCanvasNode.vue'
import StoryNodeProperties from '../components/StoryNodeProperties.vue'
import '../styles/editor.css'

const route = useRoute(), router = useRouter(), { locale } = useI18n(), auth = useAdminAuth()
const w = (zh: string, en: string) => locale.value === 'en' ? en : zh
const canWrite = computed(() => !!auth.user?.permissions.includes('story:write')), canPublish = computed(() => !!auth.user?.permissions.includes('story:publish')), canPreview = computed(() => !!auth.user?.permissions.includes('story:preview'))
const chapters = ref<EditorChapter[]>([]), chapterId = ref(String(route.query.chapter || '1')), draft = ref<Draft>(), graph = ref<EditorGraph>(), baseline = ref('')
const nodes = shallowRef<Node<CanvasData>[]>([]), edges = shallowRef<Edge[]>([]), selectedNodeId = ref(''), selectedEdgeId = ref(''), search = ref('')
const loading = ref(true), busy = ref(false), error = ref(''), success = ref(''), validation = ref<Validation>(), versions = ref<Publication[]>([])
const historyOpen = ref(false), publishOpen = ref(false), createOpen = ref(false), previewOpen = ref(false)
const preview = ref<Preview>(), perspective = ref('A'), ownAnswer = ref(''), otherAnswer = ref(''), previewZh = ref(''), previewEn = ref(''), previewError = ref('')
const culturalChecked = ref(false), bilingualChecked = ref(false), reviewNote = ref('')
const createForm = ref({ code: '', title: { zh: '', en: '' }, intro: { zh: '', en: '' }, copyFromChapterId: '1' })
const chapter = computed(() => chapters.value.find(c => c.id === chapterId.value))
const dirty = computed(() => !!graph.value && JSON.stringify(graph.value) !== baseline.value)
const selectedNode = computed(() => graph.value?.nodes.find(n => n.id === selectedNodeId.value))
const selectedEdge = computed(() => graph.value?.edges.find(e => e.id === selectedEdgeId.value))
const searchResults = computed(() => graph.value?.nodes.filter(n => !search.value || `${n.id} ${n.content.title.zh} ${n.content.title.en}`.toLowerCase().includes(search.value.toLowerCase())) || [])
const { fitView, zoomIn, zoomOut, screenToFlowCoordinate, setViewport } = useVueFlow({ id: 'story-editor' })
watch(previewOpen, value => { if (!value && route.query.preview) void router.replace({ query: { chapter: chapterId.value } }) })
function snapshot(): EditorGraph { return JSON.parse(JSON.stringify(graph.value)) }
function nodeData(n: EditorNode): CanvasData { return { kind: n.type, zh: n.content.title.zh, en: n.content.title.en, start: graph.value?.startNode === n.id, actions: actions(n) } }
function canvasEdges(): Edge[] { return (graph.value?.edges || []).map(e => ({ id: e.id, source: e.source, target: e.target, sourceHandle: e.action, targetHandle: 'in', type: 'smoothstep', label: `${e.action} · ${e.priority}${e.condition.var ? ` · ${e.condition.var} ${e.condition.op} ${e.condition.value}` : ''}`, markerEnd: MarkerType.ArrowClosed, selectable: true })) }
function syncCanvas() { nodes.value = (graph.value?.nodes || []).map(n => ({ id: n.id, type: 'story', position: { ...n.position }, data: nodeData(n) })); edges.value = canvasEdges() }
watch(graph, () => {
  validation.value = undefined
  nodes.value = nodes.value.map(n => { const source = graph.value?.nodes.find(item => item.id === n.id); return source ? { ...n, data: nodeData(source) } : n })
  const next = canvasEdges(); if (JSON.stringify(next.map(e => [e.id,e.source,e.target,e.sourceHandle,e.label])) !== JSON.stringify(edges.value.map(e => [e.id,e.sourceHandle,e.label]))) edges.value = next
}, { deep: true })
function adopt(value: Draft) {
  draft.value = value; graph.value = structuredClone(value.graph)
  for (const n of graph.value.nodes) if (n.type === 'dual') { n.config = { windowHours: 48, harmonyBase: 10, harmonyBonus: 5, knowledgeBonus: 1, ...n.config }; n.config.explanation ||= {zh:'',en:''}; n.content.clueA ||= {zh:'',en:''}; n.content.clueB ||= {zh:'',en:''} }
  baseline.value = JSON.stringify(graph.value); selectedNodeId.value = graph.value.startNode; selectedEdgeId.value = ''; syncCanvas()
  void nextTick(() => setViewport({ x: 35, y: 55, zoom: .7 }))
}
function focusEditor() { void nextTick(() => requestAnimationFrame(() => document.querySelector<HTMLButtonElement>('.editor-toolbar button')?.focus({ preventScroll: true }))) }
async function load(ask = false) {
  if (ask && dirty.value && !await confirm(w('重新加载会丢弃尚未保存的本地修改。', 'Reloading discards unsaved local changes.'))) return
  loading.value = true; error.value = ''
  try {
    chapters.value = await editorApi.chapters()
    if (!chapters.value.some(c => c.id === chapterId.value)) chapterId.value = chapters.value[0]?.id || '1'
    const [value, history] = await Promise.all([editorApi.draft(chapterId.value), editorApi.versions(chapterId.value)])
    adopt(value); versions.value = history
  } catch (cause) { error.value = errorMessage(cause, w('无法读取剧情，请重试。', 'Could not load the story. Please retry.')) }
  finally { loading.value = false }
}
async function confirm(message: string) { try { await ElMessageBox.confirm(message, w('保护当前修改', 'Protect your changes'), { confirmButtonText: w('继续', 'Continue'), cancelButtonText: w('返回编辑', 'Keep editing'), type: 'warning' }); return true } catch { return false } }
async function switchChapter(event: Event) {
  const next = (event.target as HTMLSelectElement).value
  if (dirty.value && !await confirm(w('切换篇章会丢弃本地未保存修改。', 'Switching chapters discards unsaved local changes.'))) { (event.target as HTMLSelectElement).value = chapterId.value; return }
  chapterId.value = next; await router.replace({ query: { chapter: next } }); await load()
}
async function save() {
  if (!graph.value || !draft.value || !canWrite.value || busy.value) return
  busy.value = true; error.value = ''; success.value = ''
  try { const value = await editorApi.save(chapterId.value, draft.value.revision, snapshot()); draft.value = value; baseline.value = JSON.stringify(graph.value); success.value = w('草稿已保存，尚未发布。', 'Draft saved. It has not been published.') }
  catch (cause) { error.value = errorMessage(cause, w('保存失败，本地修改仍然保留。', 'Save failed. Your local changes remain here.')) }
  finally { busy.value = false }
}
async function validate() {
  if (!graph.value || busy.value) return false
  busy.value = true; error.value = ''; success.value = ''
  try { validation.value = await editorApi.validate(snapshot()); return validation.value.valid }
  catch (cause) { error.value = errorMessage(cause, w('校验请求失败。', 'Validation request failed.')); return false }
  finally { busy.value = false }
}
function add(kind: NodeKind, position?: { x: number; y: number }) {
  if (!graph.value || !canWrite.value || busy.value) return
  const n = newNode(kind, position || screenToFlowCoordinate({ x: innerWidth * .45, y: innerHeight * .5 }))
  graph.value.nodes.push(n); if (!graph.value.startNode) graph.value.startNode = n.id
  syncCanvas(); selectedNodeId.value = n.id; selectedEdgeId.value = ''
}
function dragStart(event: DragEvent, kind: NodeKind) { event.dataTransfer?.setData('application/suishi-node', kind); if (event.dataTransfer) event.dataTransfer.effectAllowed = 'copy' }
function drop(event: DragEvent) { event.preventDefault(); const kind = event.dataTransfer?.getData('application/suishi-node'); if (nodeKinds.some(k => k.type === kind)) add(kind as NodeKind, screenToFlowCoordinate({ x: event.clientX, y: event.clientY })) }
function connect(connection: Connection) {
  if (!graph.value || !canWrite.value || busy.value || !connection.source || !connection.target) return
  const source = graph.value.nodes.find(n => n.id === connection.source), action = connection.sourceHandle || (source && actions(source)[0])
  if (!source || !action || source.type === 'ending') return
  const outgoing = graph.value.edges.filter(e => e.source === source.id && e.action === action)
  const id = crypto.randomUUID()
  graph.value.edges.push({ id, source: source.id, target: connection.target, action, priority: outgoing.length ? Math.max(...outgoing.map(e => e.priority)) + 10 : 0, condition: outgoing.length ? { var: 'knowledge', op: 'gte', value: 1 } : {} })
  edges.value = canvasEdges(); selectedEdgeId.value = id; selectedNodeId.value = ''
}
function dragStop(event: NodeDragEvent) { for (const node of event.nodes) { const source = graph.value?.nodes.find(n => n.id === node.id); if (source) source.position = { ...node.position } } }
function rename(id: string) { if (!graph.value || !selectedNode.value) return; try { renameNode(graph.value, selectedNode.value.id, id); selectedNodeId.value = id; syncCanvas() } catch (cause) { error.value = cause instanceof Error ? cause.message : 'Invalid node key' } }
function remove() { if (!graph.value || !selectedNode.value) return; removeNode(graph.value, selectedNode.value.id); selectedNodeId.value = ''; syncCanvas() }
function removeEdge() { if (!graph.value) return; graph.value.edges = graph.value.edges.filter(e => e.id !== selectedEdgeId.value); selectedEdgeId.value = ''; edges.value = canvasEdges() }
async function openPublish() { if (dirty.value || !draft.value?.revision) { error.value = w('请先保存当前草稿，再发布。', 'Save the current draft before publishing.'); return } if (await validate()) { culturalChecked.value = false; bilingualChecked.value = false; reviewNote.value = ''; publishOpen.value = true } }
async function publish() {
  if (!draft.value || busy.value) return
  busy.value = true; error.value = ''
  try { const result = await editorApi.publish(chapterId.value,draft.value.revision,reviewNote.value,culturalChecked.value,bilingualChecked.value); versions.value = await editorApi.versions(chapterId.value); chapters.value = await editorApi.chapters(); publishOpen.value = false; success.value = w(`已发布版本 v${result.version}。新用户存档将读取此版本，旧存档保持原版本。`, `Published v${result.version}. New saves use this version; existing saves stay on their original version.`) }
  catch (cause) { error.value = errorMessage(cause,w('发布失败，请核对草稿版本和权限。','Publication failed. Check the draft revision and permissions.')) }
  finally { busy.value = false }
}
async function restore(version: Publication) {
  if (!draft.value || busy.value || !await confirm(w(`将 v${version.version} 复制为新草稿，不会回退用户存档。当前本地修改将被替换。`, `Copy v${version.version} into a new draft. This replaces local edits but does not roll back player saves.`))) return
  busy.value = true; error.value = ''
  try { adopt(await editorApi.restore(chapterId.value,draft.value.revision,version.id)); historyOpen.value = false; success.value = w('历史版本已复制为新草稿，发布前仍可修改。', 'Version copied into a new draft. You can edit it before publishing.') }
  catch (cause) { error.value = errorMessage(cause,w('恢复失败，本地修改未被覆盖。','Restore failed. Local changes were not overwritten.')) }
  finally { busy.value = false }
}
async function createChapter() {
  if (busy.value) return
  if (dirty.value && !await confirm(w('新建篇章会离开当前未保存草稿，是否继续？', 'Creating a chapter leaves the current unsaved draft. Continue?'))) return
  busy.value = true; error.value = ''
  try { const value = await editorApi.create(createForm.value); chapterId.value = value.chapterId; createOpen.value = false; await router.replace({ query: { chapter: chapterId.value } }); await load(); success.value = w('独立篇章已创建，发布前不会出现在用户端。', 'Independent chapter created. Players will see it only after publication.') }
  catch (cause) { error.value = errorMessage(cause,w('创建失败，请检查代码是否重复。','Creation failed. Check for a duplicate code.')) }
  finally { busy.value = false }
}
async function archive() {
  if (busy.value || !await confirm(w('归档后新用户无法开始此篇章，已有存档仍可继续。版本记录和草稿会保留。', 'Archiving prevents new starts. Existing saves can continue; drafts and versions remain.'))) return
  busy.value = true; error.value = ''
  try { await editorApi.archive(chapterId.value); chapters.value = await editorApi.chapters(); success.value = w('篇章已归档。', 'Chapter archived.') }
  catch (cause) { error.value = errorMessage(cause,w('归档失败。','Could not archive the chapter.')) }
  finally { busy.value = false }
}
async function startPreview() {
  if (!draft.value || busy.value) return
  if (dirty.value || !draft.value.revision) { error.value = w('请先保存草稿；预览使用已保存的固定快照。','Save the draft first. Preview uses a fixed saved snapshot.'); return }
  busy.value = true; error.value = ''; previewError.value = ''
  try { preview.value = await editorApi.preview(chapterId.value,draft.value.revision,perspective.value); previewOpen.value = true; await router.replace({ query: { chapter: chapterId.value, preview: preview.value.id } }) }
  catch (cause) { error.value = errorMessage(cause,w('无法预览，请先校验图结构。','Preview failed. Validate the graph first.')) }
  finally { busy.value = false }
}
async function stepPreview(action: string) {
  if (!preview.value || busy.value) return
  busy.value = true; previewError.value = ''
  try { preview.value = await editorApi.previewStep(preview.value.id,{ expectedRevision: preview.value.revision, action, ...(preview.value.node.type === 'dual' ? { answer: ownAnswer.value, companionAnswer: otherAnswer.value } : {}), ...(['gift','checkin'].includes(preview.value.node.type) ? { textZh: previewZh.value, textEn: previewEn.value } : {}) }); ownAnswer.value = ''; otherAnswer.value = ''; previewZh.value = ''; previewEn.value = '' }
  catch (cause) { previewError.value = errorMessage(cause,w('预览提交未确认，请重新读取进度。','Preview submission unconfirmed. Reload its state.')) }
  finally { busy.value = false }
}
async function reloadPreview() { if (!preview.value) return; try { preview.value = await editorApi.previewState(preview.value.id); previewError.value = '' } catch (cause) { previewError.value = errorMessage(cause,w('预览已过期或无权访问。','Preview expired or access denied.')) } }
function beforeUnload(event: BeforeUnloadEvent) { if (dirty.value) event.preventDefault() }
onBeforeRouteLeave((to,from) => to.path === from.path || !dirty.value || confirm(w('离开将丢弃未保存修改。', 'Leaving discards unsaved changes.')))
onMounted(async () => { addEventListener('beforeunload',beforeUnload); await load(); if (typeof route.query.preview === 'string' && canPreview.value) try { preview.value = await editorApi.previewState(route.query.preview); previewOpen.value = true } catch (cause) { error.value = errorMessage(cause,w('预览已过期，可以重新开始。','Preview expired. Start a new one.')) } })
onUnmounted(() => removeEventListener('beforeunload',beforeUnload))
</script>

<template>
  <div class="story-editor-page" :aria-busy="loading || busy"><header class="editor-heading"><div><p class="eyebrow">SUISHI · STORY STUDIO</p><h1>{{ w('剧情编织室','Story studio') }}</h1></div><div class="editor-chapter-select"><label for="editor-chapter">{{ w('当前篇章','Chapter') }}</label><select id="editor-chapter" name="editorChapter" :value="chapterId" :disabled="busy || loading" @change="switchChapter"><option v-for="c in chapters" :key="c.id" :value="c.id">{{ w(c.title.zh,c.title.en) }} · {{ c.status }}</option></select></div><div class="editor-heading-actions"><button v-if="canWrite" class="secondary-button" :disabled="busy" @click="createOpen = true">{{ w('新建篇章','New chapter') }} +</button><button class="text-button" :disabled="busy" @click="load(true)">{{ w('重新加载','Reload') }}</button></div></header>
    <div v-if="error" class="notice error editor-notice" role="alert">{{ error }}</div><div v-if="success" class="notice success editor-notice" role="status">{{ success }}</div><div v-if="loading" class="loading-panel" role="status">{{ w('正在读取数据库剧情图…','Loading the saved story graph…') }}</div>
    <template v-if="graph && draft && !loading"><div class="editor-toolbar"><span class="draft-status"><i :class="{ dirty }"/>{{ dirty ? w('有未保存修改','Unsaved changes') : w('与读取时的草稿一致','Draft in sync') }} · r{{ draft.revision }}</span><div class="editor-tools"><button v-if="canWrite" class="primary-button" :disabled="busy" @click="save">{{ w('保存草稿','Save draft') }}</button><button class="secondary-button" :disabled="busy" @click="validate">{{ w('校验图结构','Validate graph') }}</button><select v-model="perspective" :disabled="busy" :aria-label="w('预览视角','Preview perspective')"><option value="A">{{ w('预览视角 A','Preview A') }}</option><option value="B">{{ w('预览视角 B','Preview B') }}</option></select><button v-if="canPreview" class="secondary-button" :disabled="busy || dirty || !draft.revision" @click="startPreview">{{ w('运行预览','Run preview') }}</button><button class="text-button" @click="historyOpen = true">{{ w('版本记录','Version history') }} ({{ versions.length }})</button><button v-if="canPublish" class="primary-button publish-button" :disabled="busy || dirty || !draft.revision" @click="openPublish">{{ w('审校并发布','Review & publish') }}</button></div></div>
    <div v-if="validation" class="graph-validation" :class="{ valid: validation.valid }" :role="validation.valid ? 'status' : 'alert'" data-testid="graph-validation"><strong>{{ validation.valid ? w('图结构校验通过','Graph validation passed') : w('发布前需要修正以下问题','Resolve these issues before publishing') }}</strong><ul v-if="!validation.valid"><li v-for="(issue,index) in validation.issues" :key="index"><code>{{ issue.location }}</code> {{ issue.message }}</li></ul></div>
    <div class="editor-workspace"><aside class="node-library"><p class="eyebrow">THE BUILDING BLOCKS</p><h2>{{ w('节点组件库','Node library') }}</h2><p class="editor-hint">{{ w('拖入画布，或点击添加。连出节点右侧的动作点，连接到目标节点左侧。','Drag onto the canvas or click to add. Connect an action on the right to the target’s left handle.') }}</p><button v-for="(kind,index) in nodeKinds" :key="kind.type" class="library-node" :class="'kind-'+kind.type" :draggable="canWrite && !busy" :disabled="!canWrite || busy" :data-testid="'add-'+kind.type" @dragstart="dragStart($event,kind.type)" @click="add(kind.type)"><span>{{ String(index+1).padStart(2,'0') }}</span><div><strong>{{ w(kind.zh,kind.en) }}</strong><small>{{ w(kind.description,kind.english) }}</small></div><i>+</i></button><div class="editor-node-index"><label for="node-search">{{ w('定位节点','Find a node') }}</label><input id="node-search" v-model="search" :placeholder="w('搜索标题或标识','Search title or key')"/><button v-for="node in searchResults" :key="node.id" @click="selectedNodeId = node.id; selectedEdgeId = ''; fitView({ nodes: [node.id], maxZoom: 1, padding: .5 })">{{ w(node.content.title.zh,node.content.title.en) || node.id }}</button></div></aside>
      <section class="editor-canvas" :aria-label="w('可视化剧情画布','Visual story canvas')" @drop="drop" @dragover.prevent><div class="canvas-toolbar"><button :aria-label="w('放大画布','Zoom in')" @click="zoomIn()">+</button><button :aria-label="w('缩小画布','Zoom out')" @click="zoomOut()">−</button><button @click="fitView({ padding: .2 })">{{ w('适应全图','Fit graph') }}</button><span>{{ graph.nodes.length }} {{ w('节点','nodes') }} · {{ graph.edges.length }} {{ w('连线','edges') }}</span></div><VueFlow id="story-editor" v-model:nodes="nodes" v-model:edges="edges" :nodes-draggable="canWrite && !busy" :nodes-connectable="canWrite && !busy" :delete-key-code="null" :min-zoom=".12" :max-zoom="2" :snap-to-grid="true" :snap-grid="[10,10]" @connect="connect" @node-click="({node}) => { selectedNodeId = node.id; selectedEdgeId = '' }" @edge-click="({edge}) => { selectedEdgeId = edge.id; selectedNodeId = '' }" @node-drag-stop="dragStop" @pane-click="selectedNodeId = ''; selectedEdgeId = ''"><template #node-story="props"><StoryCanvasNode v-bind="props"/></template></VueFlow><div class="canvas-footer"><span>{{ w('草稿不会影响用户存档；发布创建不可变新版本。','Drafts do not affect player saves. Publishing creates a new immutable version.') }}</span><span>VUE FLOW</span></div></section>
      <aside class="editor-properties"><StoryNodeProperties v-if="selectedNode" :key="selectedNode.id" :node="selectedNode" :writable="canWrite && !busy" :start="graph.startNode === selectedNode.id" @rename="rename" @start="graph.startNode = selectedNode!.id" @remove="remove"/><section v-if="selectedNode" class="node-outgoing"><h3>{{ w('此节点的出边', 'Outgoing connections') }}</h3><button v-for="edge in graph.edges.filter(e => e.source === selectedNode!.id)" :key="edge.id" :data-edge-id="edge.id" @click="selectedEdgeId = edge.id; selectedNodeId = ''">{{ edge.action }} → {{ edge.target }}</button></section><section v-else-if="selectedEdge" class="edge-properties"><p class="eyebrow">BRANCH RULES</p><h2>{{ w('连线与条件','Connection & condition') }}</h2><fieldset :disabled="!canWrite || busy"><label>{{ w('来源','Source') }}<input :value="selectedEdge.source" readonly/></label><label>{{ w('目标','Target') }}<select v-model="selectedEdge.target" name="edgeTarget"><option v-for="node in graph.nodes" :key="node.id" :value="node.id">{{ node.id }}</option></select></label><label>{{ w('触发动作','Action') }}<select v-model="selectedEdge.action" name="edgeAction"><option v-for="action in actions(graph.nodes.find(n => n.id === selectedEdge!.source)!)" :key="action" :value="action">{{ action }}</option></select></label><label>{{ w('优先级（较高优先）','Priority (higher first)') }}<input v-model.number="selectedEdge.priority" type="number" min="0" max="1000" name="edgePriority"/></label><label class="editor-checkbox"><input type="checkbox" :checked="!!selectedEdge.condition.var" @change="selectedEdge.condition = ($event.target as HTMLInputElement).checked ? {var:'knowledge',op:'gte',value:1} : {}"/>{{ w('使用条件分支','Use a condition') }}</label><template v-if="selectedEdge.condition.var"><label>{{ w('变量','Variable') }}<select v-model="selectedEdge.condition.var"><option value="knowledge">knowledge</option><option value="harmony">harmony</option><option value="care">care</option></select></label><label>{{ w('比较方式','Comparison') }}<select v-model="selectedEdge.condition.op"><option v-for="op in ['eq','ne','gte','gt','lte','lt']" :key="op" :value="op">{{ op }}</option></select></label><label>{{ w('比较值','Value') }}<input v-model.number="selectedEdge.condition.value" type="number"/></label></template><p class="editor-hint">{{ w('每个动作需有一个默认分支；条件分支使用更高且唯一的优先级。只执行整数白名单DSL，不执行代码。','Each action needs one fallback. Conditional branches need higher, unique priorities. Only integer comparisons are supported.') }}</p><button class="editor-delete" @click="removeEdge">{{ w('删除此连线','Delete connection') }}</button></fieldset></section><section v-else class="graph-properties"><p class="eyebrow">STORY SETTINGS</p><h2>{{ w('故事设置','Story settings') }}</h2><p class="editor-hint">{{ w('选中节点或连线编辑属性。空白处显示初始变量。','Select a node or connection to edit it. Initial variables appear here.') }}</p><fieldset :disabled="!canWrite || busy"><label>{{ w('故事起点','Start node') }}<select v-model="graph.startNode"><option value="">{{ w('请选择','Select') }}</option><option v-for="node in graph.nodes" :key="node.id" :value="node.id">{{ node.id }}</option></select></label><label v-for="variable in (['knowledge','harmony','care'] as const)" :key="variable">{{ variable }}<input v-model.number="graph.initialVariables[variable]" type="number" min="0" max="10000"/></label></fieldset><p class="editor-hint">{{ w('私有文字回礼/打卡已支持。图片与公开审核尚未开放，不会因添加节点而自动启用。','Private text gifts/check-ins are supported. Adding a node does not enable uploads or public moderation.') }}</p><RouterLink v-if="chapter?.status === 'PUBLISHED'" :to="'/stories/'+chapterId" class="secondary-button">{{ w('打开用户端篇章','Open player chapter') }} ↗</RouterLink><button v-if="canPublish && chapter?.status !== 'ARCHIVED'" class="text-button danger-text" :disabled="busy" @click="archive">{{ w('归档此篇章','Archive chapter') }}</button></section></aside></div></template>
    <ElDialog v-model="historyOpen" @closed="focusEditor" :title="w('不可变发布版本','Immutable published versions')" width="760px"><p class="editor-hint">{{ w('恢复只复制为新草稿；已发布版本和旧用户存档不会修改。','Restore copies a version into a new draft. Published versions and player saves remain unchanged.') }}</p><div v-if="!versions.length" class="editor-empty">{{ w('尚无发布版本','No published versions yet') }}</div><article v-for="version in versions" :key="version.id" class="version-record"><div><strong>v{{ version.version }}</strong><span>{{ version.publishedAt || w('初始种子版本','Initial seed version') }}</span><p>{{ version.reviewNote || w('未记录管理端审校，不代表已获专家审阅。','No editor review record. This does not imply expert approval.') }}</p><small>{{ version.publishedBy ? w('发布账号ID','Publisher ID') + ': ' + version.publishedBy : '' }} · {{ version.draftRevision ? 'r'+version.draftRevision : 'seed' }}</small></div><button v-if="canWrite" class="secondary-button" :disabled="busy" @click="restore(version)">{{ w('复制为草稿','Copy to draft') }}</button></article></ElDialog>
    <ElDialog v-model="publishOpen" @closed="focusEditor" :title="w('审校当前草稿并发布','Review and publish this draft')" width="580px" :close-on-click-modal="false"><form class="editor-dialog-form" @submit.prevent="publish"><p>{{ w('发布将创建用户端实际可玩的新版本。请核对双语内容、文化事实与分支规则；已有用户继续原版本。','Publishing creates a playable version. Check both languages, cultural facts and branch rules. Existing players keep their original version.') }}</p><label class="editor-checkbox"><input v-model="culturalChecked" type="checkbox" required/>{{ w('我已核对文化内容与依据','I have checked the cultural content and sources') }}</label><label class="editor-checkbox"><input v-model="bilingualChecked" type="checkbox" required/>{{ w('我已核对中英文及剧情分支','I have checked both languages and story branches') }}</label><label>{{ w('审校与发布说明（真实记录）','Review and publication note (recorded)') }}<textarea v-model="reviewNote" name="publicationNote" rows="4" minlength="5" maxlength="500" required/></label><p v-if="error" class="editor-field-error" role="alert">{{ error }}</p><button class="primary-button" type="submit" :disabled="busy || !culturalChecked || !bilingualChecked || reviewNote.trim().length < 5">{{ w('确认发布新版本','Publish new version') }}</button></form></ElDialog>
    <ElDialog v-model="createOpen" @closed="focusEditor" :title="w('创建独立篇章','Create an independent chapter')" width="600px"><form class="editor-dialog-form" @submit.prevent="createChapter"><p class="editor-hint">{{ w('复制一个已发布图作为起点。新篇章在发布前仅管理端可见；可用于联调，不覆盖立春正文。','Copy a published graph as a starting point. The new chapter stays private until publication and can be used for integration checks.') }}</p><label>{{ w('篇章代码（大写且唯一）','Chapter code (unique uppercase key)') }}<input v-model="createForm.code" name="chapterCode" pattern="[A-Z][A-Z0-9_]{1,31}" maxlength="32" required/></label><label>{{ w('标题 · 中文','Title · Chinese') }}<input v-model="createForm.title.zh" name="chapterTitleZh" maxlength="200" required/></label><label>Title · English<input v-model="createForm.title.en" name="chapterTitleEn" maxlength="200" required/></label><label>{{ w('简介 · 中文','Intro · Chinese') }}<textarea v-model="createForm.intro.zh" name="chapterIntroZh" rows="2" maxlength="200" required/></label><label>Intro · English<textarea v-model="createForm.intro.en" name="chapterIntroEn" rows="2" maxlength="200" required/></label><label>{{ w('复制来源','Copy from') }}<select v-model="createForm.copyFromChapterId"><option v-for="c in chapters.filter(c => c.status === 'PUBLISHED')" :key="c.id" :value="c.id">{{ w(c.title.zh,c.title.en) }}</option></select></label><p v-if="error" class="editor-field-error" role="alert">{{ error }}</p><button type="submit" class="primary-button" :disabled="busy">{{ w('创建独立草稿','Create draft') }}</button></form></ElDialog>
    <ElDialog v-model="previewOpen" @closed="focusEditor" :title="w('隔离剧情预览','Isolated story preview')" width="780px" :close-on-click-modal="false"><div v-if="preview" class="editor-preview" data-testid="editor-preview"><p class="preview-isolation">{{ w('真实服务端规则 · 独立快照 · 不写入用户存档/奖励/回礼 · 一小时有效','Real server rules · Independent snapshot · No player saves, rewards or gifts · Expires in one hour') }}</p><div class="preview-meta"><span>{{ preview.node.type }} · {{ preview.state.perspective }} · r{{ preview.revision }}</span><span v-for="(value,key) in preview.state.variables" :key="key">{{ key }}: {{ value }}</span></div><h2 data-testid="preview-title">{{ w(preview.node.title.zh,preview.node.title.en) }}</h2><p>{{ w(preview.node.body.zh,preview.node.body.en) }}</p><p class="parallel-text">{{ w(preview.node.body.en,preview.node.body.zh) }}</p><aside v-if="preview.state.feedback?.zh" class="story-feedback"><p>{{ w(preview.state.feedback.zh,preview.state.feedback.en) }}</p><span v-if="preview.state.feedback.harmonyDelta !== undefined">{{ w('默契变化','Harmony change') }} +{{ preview.state.feedback.harmonyDelta }}</span></aside><p v-if="previewError" class="notice error" role="alert">{{ previewError }}</p><div class="preview-controls"><template v-if="preview.state.status !== 'COMPLETED'"><button v-if="preview.node.type === 'narrative'" class="primary-button" :disabled="busy" @click="stepPreview('continue')">{{ w('继续预览','Continue preview') }}</button><div v-if="preview.node.type === 'choice'" class="story-options"><button v-for="option in preview.node.options" :key="option.id" class="story-option" :disabled="busy" :data-testid="'preview-option-'+option.id" @click="stepPreview(option.id)">{{ w(option.label.zh,option.label.en) }}</button></div><form v-if="preview.node.type === 'dual'" class="editor-dialog-form" @submit.prevent="stepPreview('submit')"><div class="clue-paper"><small>{{ w('当前视角专属线索','Current perspective’s private clue') }}</small><p>{{ w(preview.node.clue!.zh,preview.node.clue!.en) }}</p></div><label>{{ w('当前视角选择','Current perspective answer') }}<select :disabled="busy" v-model="ownAnswer" name="previewOwnAnswer" required><option value="">{{ w('请选择','Select') }}</option><option v-for="option in preview.node.options" :key="option.id" :value="option.id">{{ w(option.label.zh,option.label.en) }}</option></select></label><label>{{ w('模拟另一位测试者的选择','Simulate the other tester’s answer') }}<select :disabled="busy" v-model="otherAnswer" name="previewCompanionAnswer" required><option value="">{{ w('请选择','Select') }}</option><option v-for="option in preview.companionOptions" :key="option.id" :value="option.id">{{ w(option.label.zh,option.label.en) }}</option></select></label><button class="primary-button" :disabled="busy" type="submit">{{ w('提交双方预览答案','Judge both preview answers') }}</button></form><form v-if="preview.node.type === 'gift' || preview.node.type === 'checkin'" class="editor-dialog-form" @submit.prevent="stepPreview('submit')"><label>中文<textarea :disabled="busy" v-model="previewZh" name="previewTextZh" rows="3" maxlength="2000"/></label><label>English<textarea :disabled="busy" v-model="previewEn" name="previewTextEn" rows="3" maxlength="2000"/></label><button type="submit" class="primary-button" :disabled="busy">{{ w('提交预览文字','Submit preview text') }}</button></form><button v-if="preview.node.type === 'ending'" class="primary-button" :disabled="busy" @click="stepPreview('finish')">{{ w('预览结算（不发印章）','Preview settlement (no stamp)') }}</button></template><p v-else class="notice success" data-testid="preview-completed">{{ w('预览已完成，没有发放真实奖励。','Preview complete. No real reward was awarded.') }}</p><button class="text-button" :disabled="busy" @click="reloadPreview">{{ w('重新读取预览进度','Reload preview state') }}</button></div></div></ElDialog>
  </div>
</template>
