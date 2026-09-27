import axios from 'axios'

export const http = axios.create({ baseURL: '/api', timeout: 30000 })
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('yinyuan-token')
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

export interface Notice {
  id: string; zone_id: string; zone_name: string; title: string; body: string
  canonical_url: string; published_at?: string; fetched_at?: string
}
export interface Zone {
  id: string; name: string; description: string; subscribed?: boolean
}
export interface Citation {
  type: string; title: string; url?: string | null; page?: number | null
  section?: string | null; excerpt?: string; publishedAt?: string | null
}
export interface ChatMessage {
  id: string; role: 'USER' | 'ASSISTANT'; content: string; citations: Citation[]; createdAt?: string
}

export const api = {
  login: (username: string, password: string) => http.post('/auth/login', { username, password }),
  register: (username: string, password: string, email?: string, weeklyEmailOptIn = false) =>
    http.post('/auth/register', { username, password, email: email || null, weeklyEmailOptIn }),
  me: () => http.get('/auth/me'),
  preferences: (email: string | null, weeklyEmailOptIn: boolean) =>
    http.put('/auth/preferences', { email, weeklyEmailOptIn }),
  zones: () => http.get<Zone[]>('/zones'),
  notices: (params: { q?: string; zoneId?: string; limit?: number } = {}) => http.get<Notice[]>('/notices', { params }),
  subscriptions: () => http.get<Zone[]>('/subscriptions'),
  subscribe: (zoneId: string) => http.post('/subscriptions', { zoneId }),
  unsubscribe: (zoneId: string) => http.delete('/subscriptions/' + encodeURIComponent(zoneId)),
  subscriptionIntent: (sessionId: string, message: string) =>
    http.post('/subscriptions/intent', { sessionId, message }),
  sessions: () => http.get('/chat/sessions'),
  createSession: (title?: string) => http.post('/chat/sessions', { title }),
  messages: (id: string) => http.get<ChatMessage[]>('/chat/sessions/' + id + '/messages'),
  deleteSession: (id: string) => http.delete('/chat/sessions/' + id),
  reports: () => http.get('/weekly'),
  adminStatus: () => http.get('/admin/status'),
  sources: () => http.get('/admin/sources'),
  crawlRuns: () => http.get('/admin/crawl-runs'),
  documents: () => http.get('/admin/knowledge/documents'),
  users: () => http.get('/admin/users'),
  updateUser: (id: string, role: string, active: boolean) =>
    http.put('/admin/users/' + encodeURIComponent(id), { role, active }),
  addSource: (payload: { name: string; zoneId: string; baseUrl: string }) => http.post('/admin/sources', payload),
  crawl: () => http.post('/admin/crawl'),
  importPdf: (file: File) => {
    const data = new FormData(); data.append('file', file)
    return http.post('/admin/knowledge/import', data, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 180000 })
  },
}

export async function streamMessage(
  sessionId: string,
  message: string,
  onEvent: (name: string, data: string) => void,
): Promise<void> {
  const token = localStorage.getItem('yinyuan-token')
  const response = await fetch('/api/chat/sessions/' + encodeURIComponent(sessionId) + '/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: JSON.stringify({ message }),
  })
  if (!response.ok || !response.body) {
    const detail = await response.text()
    throw new Error(detail || '请求失败（' + response.status + '）')
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const blocks = buffer.split(/\r?\n\r?\n/)
    buffer = blocks.pop() || ''
    for (const block of blocks) {
      let name = 'message'
      const data: string[] = []
      for (const line of block.split(/\r?\n/)) {
        if (line.startsWith('event:')) name = line.slice(6).trim()
        if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
      }
      if (data.length) onEvent(name, data.join('\n'))
    }
  }
}