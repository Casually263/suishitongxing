import { clients } from './client'
import type { Result } from './types'
export interface Bilingual { zh: string; en: string }
export interface KnowledgeCard { id: string; content: { title: Bilingual; body: Bilingual }; sourceTitle: string; sourceUrl: string; reviewStatus: string }
export interface StoryOption { id: string; label: Bilingual }
export interface StoryNode { id: string; key: string; type: 'narrative' | 'choice' | 'dual' | 'gift' | 'checkin' | 'ending'; title: Bilingual; body: Bilingual; options?: StoryOption[]; clue?: Bilingual }
export interface StoryEvent { nodeId: string; nodeKey: string; action: string; at: string; feedback?: Bilingual & { success?: boolean; harmonyDelta?: number; partnerMode?: string; skipped?: boolean } }
export interface StoryReward { chapterId: string; stampCode: string; endingKey: string; partnerMode: string; awardedAt: string }
export interface Chapter { id: string; code: string; title: Bilingual; intro: Bilingual; releaseMode: string; progressStatus: string }
export interface StoryState {
  started: boolean; chapterId?: string; storyId?: string; storyVersion?: number; version?: number; status?: string;
  node?: StoryNode; variables?: Record<string, number>; history?: StoryEvent[]; perspective?: string;
  partnerMode?: 'SCRIPT' | 'HUMAN'; partnerJoined?: boolean; inviteCode?: string;
  dual?: { state: string; deadline: string; extended: boolean; submitted: boolean };
  submissions?: { nodeId: string; kind: string; content: Bilingual; visibility: string; auditStatus: string }[];
  rewards?: StoryReward[];
}
export interface StoryAction { nodeId: string; expectedVersion: number; requestId: string; action: string; answer?: string; textZh?: string; textEn?: string }
async function unwrap<T>(request: Promise<{ data: Result<T> }>) { return (await request).data.data }
export const storyApi = {
  chapters: () => unwrap(clients.portal.get<Result<Chapter[]>>('/chapters')),
  rewards: () => unwrap(clients.portal.get<Result<StoryReward[]>>('/stories/rewards')),
  state: (id: string) => unwrap(clients.portal.get<Result<StoryState>>(`/stories/${id}/state`)),
  cards: (id: string) => unwrap(clients.portal.get<Result<KnowledgeCard[]>>(`/chapters/${id}/cards`)),
  start: (id: string, mode: 'SCRIPT' | 'HUMAN') => unwrap(clients.portal.post<Result<StoryState>>(`/stories/${id}/start`, { mode })),
  join: (id: string, inviteCode: string) => unwrap(clients.portal.post<Result<StoryState>>(`/stories/${id}/join`, { inviteCode })),
  choose: (id: string, action: StoryAction) => unwrap(clients.portal.post<Result<StoryState>>(`/stories/${id}/choose`, action)),
}
