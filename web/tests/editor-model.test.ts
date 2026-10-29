import { describe, expect, it } from 'vitest'
import { actions, newNode, removeNode, renameNode } from '../src/experience/editor-model'
import type { EditorGraph } from '../src/api/editor'

function graph(): EditorGraph {
  const a = newNode('narrative', { x: 10, y: 20 }), b = newNode('ending', { x: 300, y: 40 })
  a.id = 'opening'; b.id = 'ending'
  return { startNode: a.id, initialVariables: {}, nodes: [a,b], edges: [{ id: 'edge1', source: a.id, target: b.id, action: 'continue', priority: 0, condition: {} }] }
}
describe('authoring graph identity operations', () => {
  it('renaming preserves the start and every incoming/outgoing reference', () => {
    const g = graph(); renameNode(g, 'opening', 'spring_letter')
    expect(g.startNode).toBe('spring_letter'); expect(g.edges[0]!.source).toBe('spring_letter')
    renameNode(g, 'ending', 'spring_returns'); expect(g.edges[0]!.target).toBe('spring_returns')
  })
  it('rejects duplicate or executable-looking keys without mutating the graph', () => {
    const g = graph(), original = JSON.stringify(g)
    expect(() => renameNode(g, 'opening', 'ending')).toThrow()
    expect(() => renameNode(g, 'opening', '__proto__')).toThrow()
    expect(() => renameNode(g, 'opening', 'a;eval()')).toThrow()
    expect(JSON.stringify(g)).toBe(original)
  })
  it('removing a start node removes its edges and requires a new explicit start', () => {
    const g = graph(); removeNode(g, 'opening')
    expect(g.startNode).toBe(''); expect(g.edges).toEqual([]); expect(g.nodes.map(n => n.id)).toEqual(['ending'])
  })
  it('connection handles track actual choices instead of inventing actions', () => {
    const choice = newNode('choice', { x: 0, y: 0 })
    choice.content.options = [{ id: 'observe', label: { zh: '观察', en: 'Observe' } }]
    expect(actions(choice)).toEqual(['observe']); expect(actions(newNode('ending', { x:0,y:0 }))).toEqual([])
    expect(actions(undefined)).toEqual([])
  })
})
