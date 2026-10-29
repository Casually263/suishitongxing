import type { EditorGraph, EditorNode, NodeKind } from '../api/editor'

export const nodeKinds: { type: NodeKind; zh: string; en: string; description: string; english: string }[] = [
  { type: 'narrative', zh: '叙述', en: 'Narrative', description: '讲述场景，推进故事', english: 'Set a scene and continue' },
  { type: 'choice', zh: '单人选择', en: 'Choice', description: '选项、反馈与变量变化', english: 'Choices, feedback and effects' },
  { type: 'dual', zh: '双视角协作', en: 'Two perspectives', description: '独立线索，统一判定', english: 'Private clues, shared judgment' },
  { type: 'gift', zh: '文化回礼', en: 'Cultural gift', description: '记录双语文化体验', english: 'A bilingual cultural exchange' },
  { type: 'checkin', zh: '习俗打卡', en: 'Check-in', description: '提交自己的习俗手记', english: 'Record an everyday ritual' },
  { type: 'ending', zh: '结局', en: 'Ending', description: '总结剧情，服务端结算', english: 'Close the story and settle rewards' },
]
export function actions(node?: EditorNode) { if (!node) return []; return node.type === 'ending' ? [] : node.type === 'choice' ? (node.content.options || []).map(o => o.id) : [node.type === 'narrative' ? 'continue' : 'submit'] }
export function newNode(type: NodeKind, position: { x: number; y: number }): EditorNode {
  const node: EditorNode = { id: `${type}_${crypto.randomUUID().replaceAll('-', '').slice(0, 12)}`, type, position, content: { title: { zh: `新的${nodeKinds.find(k => k.type === type)!.zh}`, en: `New ${type}` }, body: { zh: '', en: '' } }, config: {} }
  const options = () => [{ id: 'first', label: { zh: '', en: '' } }, { id: 'second', label: { zh: '', en: '' } }]
  if (type === 'choice') { node.content.options = options(); node.config.choices = { first: { effects: {} }, second: { effects: {} } } }
  if (['narrative', 'gift', 'checkin'].includes(type)) node.config.effects = {}
  if (type === 'dual') {
    node.content.clueA = { zh: '', en: '' }; node.content.clueB = { zh: '', en: '' }; node.content.optionsA = options(); node.content.optionsB = options()
    node.config = { answerA: 'first', answerB: 'first', explanation: { zh: '', en: '' }, windowHours: 48, harmonyBase: 10, harmonyBonus: 5, knowledgeBonus: 1 }
  }
  return node
}
export function renameNode(graph: EditorGraph, oldId: string, newId: string) {
  if (!/^[a-z][a-z0-9_-]{0,63}$/.test(newId) || graph.nodes.some(n => n.id === newId && n.id !== oldId)) throw new Error('节点标识须唯一，使用小写字母开头及字母、数字、下划线或短横线')
  const node = graph.nodes.find(n => n.id === oldId)
  if (!node) throw new Error('节点不存在')
  node.id = newId
  if (graph.startNode === oldId) graph.startNode = newId
  for (const edge of graph.edges) { if (edge.source === oldId) edge.source = newId; if (edge.target === oldId) edge.target = newId }
}
export function removeNode(graph: EditorGraph, id: string) {
  graph.nodes = graph.nodes.filter(node => node.id !== id)
  graph.edges = graph.edges.filter(edge => edge.source !== id && edge.target !== id)
  if (graph.startNode === id) graph.startNode = ''
}
