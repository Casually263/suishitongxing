import { describe, expect, it } from 'vitest'
import { safeRedirect } from '../src/app/navigation'

describe('login redirect safety', () => {
  it.each(['https://evil.invalid', '//evil.invalid', '/\\evil.invalid', '/%2f%2fevil.invalid', '/%5cevil', '/\n/evil', undefined])('rejects an unsafe redirect %s', value => {
    expect(safeRedirect(value, false)).toBe('/')
    expect(safeRedirect(value, true)).toBe('/admin')
  })
  it('keeps portal and admin return paths in the correct workspace', () => {
    expect(safeRedirect('/profile', false)).toBe('/profile')
    expect(safeRedirect('/admin/users', false)).toBe('/')
    expect(safeRedirect('/profile', true)).toBe('/admin')
    expect(safeRedirect('/admin/users?page=2', true)).toBe('/admin/users?page=2')
  })
})
