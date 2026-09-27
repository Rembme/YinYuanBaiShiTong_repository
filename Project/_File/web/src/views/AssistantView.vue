<script setup lang="ts">
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, streamMessage, type ChatMessage, type Citation } from '../api'

defineProps<{ user: { username: string; role: string } }>()
const sessions = ref<any[]>([])
const currentId = ref('')
const messages = ref<ChatMessage[]>([])
const input = ref('')
const loading = ref(false)
const sending = ref(false)
const scrollBox = ref<HTMLElement | null>(null)
const suggestions = ['我想了解本科生请假规定', '最近有哪些校园通知？', '转专业需要符合什么条件？', '帮我查找教学实验室相关制度']

async function loadSessions() {
  loading.value = true
  try {
    sessions.value = (await api.sessions()).data
    if (!currentId.value && sessions.value.length) await selectSession(sessions.value[0].id)
  } catch { ElMessage.error('会话列表暂时无法加载') }
  finally { loading.value = false }
}
async function newSession() {
  try {
    const result = await api.createSession()
    sessions.value.unshift(result.data); currentId.value = result.data.id; messages.value = []; await nextTick()
  } catch { ElMessage.error('新建会话失败') }
}
async function selectSession(id: string) {
  currentId.value = id
  try { messages.value = (await api.messages(id)).data; await scrollToBottom() }
  catch { ElMessage.error('无法读取此会话') }
}
async function removeSession(id: string) {
  try {
    await ElMessageBox.confirm('删除后将移除此会话及其消息。', '删除会话', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await api.deleteSession(id); sessions.value = sessions.value.filter(s => s.id !== id)
    if (currentId.value === id) { currentId.value = ''; messages.value = []; if (sessions.value.length) await selectSession(sessions.value[0].id) }
  } catch { /* cancelled */ }
}
async function send(text = input.value) {
  const question = text.trim()
  if (!question || sending.value) return
  if (!currentId.value) await newSession()
  if (!currentId.value) return
  input.value = ''; sending.value = true
  messages.value.push({ id: crypto.randomUUID(), role: 'USER', content: question, citations: [] })
  const assistant: ChatMessage = { id: crypto.randomUUID(), role: 'ASSISTANT', content: '', citations: [] }
  messages.value.push(assistant); await scrollToBottom()
  try {
    const intent = await api.subscriptionIntent(currentId.value, question)
    if (intent.data.handled) {
      assistant.content = intent.data.message
      return
    }
    await streamMessage(currentId.value, question, (event, data) => {
      if (event === 'token') assistant.content += data
      else if (event === 'citation') { try { assistant.citations.push(JSON.parse(data) as Citation) } catch { /* ignore malformed item */ } }
      else if (event === 'error') assistant.content += `\n\n${data}`
      void scrollToBottom()
    })
    const active = sessions.value.find(s => s.id === currentId.value)
    if (active) { active.title = question.slice(0,36); sessions.value.sort((a,b) => String(b.updated_at).localeCompare(String(a.updated_at))) }
  } catch (error: any) {
    assistant.content += assistant.content ? '\n\n连接中断，可以重试。' : (error?.message || '请求失败，请稍后重试。')
  } finally { sending.value = false; await loadSessions(); await scrollToBottom() }
}
function scrollToBottom() { return nextTick(() => { if (scrollBox.value) scrollBox.value.scrollTop = scrollBox.value.scrollHeight }) }
function formatDate(value?: string) { return value ? new Date(value).toLocaleDateString('zh-CN') : '日期待确认' }
onMounted(loadSessions)
</script>

<template>
  <div class="assistant-layout">
    <aside class="conversation-rail">
      <div class="rail-heading"><div><strong>对话记录</strong><small>最多保留 20 轮上下文</small></div><el-button circle plain @click="newSession">＋</el-button></div>
      <el-button class="new-chat-button" @click="newSession">＋　开启新对话</el-button>
      <div class="session-list" v-loading="loading">
        <div v-for="session in sessions" :key="session.id" :class="['session-item', { selected: currentId === session.id }]" @click="selectSession(session.id)">
          <span class="session-dot"></span><span class="session-title">{{ session.title }}</span><el-dropdown trigger="click"><button class="session-more" @click.stop>···</button><template #dropdown><el-dropdown-menu><el-dropdown-item @click="removeSession(session.id)">删除会话</el-dropdown-item></el-dropdown-menu></template></el-dropdown>
        </div>
        <el-empty v-if="!loading && sessions.length === 0" description="还没有对话" :image-size="56" />
      </div>
    </aside>
    <section class="chat-stage">
      <div ref="scrollBox" class="message-scroll">
        <div v-if="messages.length === 0" class="welcome-state">
          <div class="welcome-orb"><img src="/assets/AI_Agent_logo.jpg" alt="" /></div>
          <div class="eyebrow muted">CAMPUS ASSISTANT</div>
          <h1>你好，今天想了解什么？</h1>
          <p>我可以帮你检索校园通知和教学管理制度，并提供可核对的来源。</p>
          <div class="suggestion-grid"><button v-for="item in suggestions" :key="item" @click="send(item)">{{ item }}<span>↗</span></button></div>
        </div>
        <div v-else class="message-list">
          <article v-for="(message,index) in messages" :key="message.id" :class="['message-row', message.role === 'USER' ? 'from-user' : 'from-assistant']">
            <div class="message-avatar">{{ message.role === 'USER' ? user.username.slice(0,1).toUpperCase() : '印' }}</div>
            <div class="message-body"><div class="message-label">{{ message.role === 'USER' ? '我' : '印苑百事通' }}</div><div class="message-content" :class="{ 'user-bubble': message.role === 'USER' }">{{ message.content }}<span v-if="sending && index === messages.length-1 && message.role === 'ASSISTANT'" class="typing-caret"></span></div>
              <div v-if="message.role === 'ASSISTANT' && message.citations.length" class="citation-list"><div class="citation-title">参考来源</div><a v-for="(citation,i) in message.citations" :key="`${citation.title}-${i}`" class="citation-card" :href="citation.url || undefined" :target="citation.url ? '_blank' : undefined" rel="noreferrer"><span class="citation-index">{{ i+1 }}</span><span><strong>{{ citation.title }}</strong><small>{{ citation.type === 'policy' ? `知识库 PDF · 第 ${citation.page || '?'} 页` : `${citation.publishedAt ? formatDate(citation.publishedAt) + ' · ' : ''}学校通知` }}</small></span><span v-if="citation.url" class="external-link">↗</span></a></div>
            </div>
          </article>
        </div>
      </div>
      <div class="composer-wrap"><div class="composer"><el-input v-model="input" type="textarea" :autosize="{ minRows: 1, maxRows: 5 }" resize="none" placeholder="输入问题，查询通知或制度…" :disabled="sending" @keydown.enter.exact.prevent="send()" /><button class="send-button" :disabled="!input.trim() || sending" @click="send()">{{ sending ? '…' : '↑' }}</button></div><div class="composer-hint">回答依据当前已采集和导入的资料，请通过原文链接核对时效性</div></div>
    </section>
  </div>
</template>

