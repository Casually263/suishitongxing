export function safeRedirect(value: unknown, admin: boolean): string {
  const fallback = admin ? '/admin' : '/'
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || /[\\\r\n]/.test(value)) return fallback
  // Reject encoded separators and scheme-like paths before handing them to Router.
  if (/%(?:2f|5c|0a|0d)/i.test(value)) return fallback
  return admin ? (value === '/admin' || value.startsWith('/admin/') ? value : fallback) : (value.startsWith('/admin') ? fallback : value)
}
