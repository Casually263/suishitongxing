export type Channel = 'portal' | 'admin'
export interface Result<T> { code: string; msg: string; data: T; traceId: string }
export interface User {
  id: string; username: string; cultureRole: 'GUIDE' | 'PARTNER'; status: 'ACTIVE' | 'DISABLED';
  adultConfirmed: boolean; emailVerified: boolean; authVersion: number; roles: string[]; permissions: string[]; createdAt: string
}
export interface Session { accessToken: string; expiresIn: number; user: User }
export interface Slot { dayOfWeek: number; start: string; end: string }
export interface Profile {
  displayName: string; country: string | null; nativeLang: string; learnLang: string;
  langLevel: Record<string, number>; timezone: string; availSlots: Slot[]; tagIds: string[];
  privacy: Record<string, boolean>; notificationPreferences: Record<string, boolean>; version: number
}
export interface Tag { id: string; code: string; name: { zh: string; en: string } }
export interface Meta { interestTags: Tag[]; languages: string[]; termsVersion: string; features: Record<string, boolean> }
export interface Page<T> { items: T[]; page: number; size: number; total: number }
