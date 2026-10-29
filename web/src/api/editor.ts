import { clients } from './client'
import type { Result } from './types'
import type { Bilingual, StoryNode, StoryOption } from './story'

export type NodeKind = StoryNode['type']
export type Variable = 'knowledge' | 'harmony' | 'care'
export type Effects = Partial<Record<Variable, number>>
export interface ChoiceRule { effects?: Effects; feedback?: Bilingual }
export interface EditorConfig { effects?: Effects; choices?: Record<string, ChoiceRule>; answerA?: string; answerB?: string; explanation?: Bilingual; windowHours?: number; harmonyBase?: number; harmonyBonus?: number; knowledgeBonus?: number }
export interface EditorContent { title: Bilingual; body: Bilingual; options?: StoryOption[]; clueA?: Bilingual; clueB?: Bilingual; optionsA?: StoryOption[]; optionsB?: StoryOption[] }
export interface EditorNode { id: string; type: NodeKind; content: EditorContent; config: EditorConfig; position: { x: number; y: number } }
export interface Condition { var?: Variable; op?: 'eq' | 'ne' | 'gte' | 'gt' | 'lte' | 'lt'; value?: number }
export interface EditorEdge { id: string; source: string; target: string; action: string; priority: number; condition: Condition }
export interface EditorGraph { startNode: string; initialVariables: Effects; nodes: EditorNode[]; edges: EditorEdge[] }
export interface Draft { chapterId: string; revision: number; graph: EditorGraph; updatedBy?: string; updatedAt?: string }
export interface EditorChapter { id: string; code: string; title: Bilingual; intro: Bilingual; status: string }
export interface Validation { valid: boolean; issues: { location: string; code: string; message: string }[] }
export interface Publication { id: string; version: number; status: string; draftRevision?: number; publishedBy?: string; reviewNote?: string; publishedAt?: string }
export interface Preview { id: string; revision: number; expiresAt: string; node: StoryNode; companionOptions: StoryOption[]; state: { status: string; perspective: string; variables: Record<Variable, number>; nodeKey: string; feedback?: Bilingual & { success?: boolean; harmonyDelta?: number }; history: { nodeKey: string; action: string }[] } }
async function unwrap<T>(request: Promise<{ data: Result<T> }>) { return (await request).data.data }
export const editorApi = {
  chapters: () => unwrap(clients.admin.get<Result<EditorChapter[]>>('/story/chapters')),
  draft: (id: string) => unwrap(clients.admin.get<Result<Draft>>(`/story/chapters/${id}/draft`)),
  save: (id: string, expectedRevision: number, graph: EditorGraph) => unwrap(clients.admin.put<Result<Draft>>(`/story/chapters/${id}/draft`, { expectedRevision, graph })),
  validate: (graph: EditorGraph) => unwrap(clients.admin.post<Result<Validation>>('/story/validate', graph)),
  publish: (id: string, expectedRevision: number, note: string, culturalChecked: boolean, bilingualChecked: boolean) => unwrap(clients.admin.post<Result<{ storyId: string; version: number; alreadyPublished: boolean }>>(`/story/chapters/${id}/publish`, { expectedRevision, note, culturalChecked, bilingualChecked })),
  versions: (id: string) => unwrap(clients.admin.get<Result<Publication[]>>(`/story/chapters/${id}/versions`)),
  restore: (id: string, expectedRevision: number, storyId: string) => unwrap(clients.admin.post<Result<Draft>>(`/story/chapters/${id}/restore`, { expectedRevision, storyId })),
  create: (body: { code: string; title: Bilingual; intro: Bilingual; copyFromChapterId: number | string }) => unwrap(clients.admin.post<Result<Draft>>('/story/chapters', body)),
  archive: (id: string) => clients.admin.post(`/story/chapters/${id}/archive`),
  preview: (id: string, expectedRevision: number, perspective: string) => unwrap(clients.admin.post<Result<Preview>>(`/story/chapters/${id}/preview`, { expectedRevision, perspective })),
  previewState: (id: string) => unwrap(clients.admin.get<Result<Preview>>(`/story/previews/${id}`)),
  previewStep: (id: string, body: { expectedRevision: number; action: string; answer?: string; companionAnswer?: string; textZh?: string; textEn?: string }) => unwrap(clients.admin.post<Result<Preview>>(`/story/previews/${id}/step`, body)),
}
