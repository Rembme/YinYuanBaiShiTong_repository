<script setup lang="ts">
import { computed, onMounted, ref, type Component } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from './api'
import AssistantView from './views/AssistantView.vue'
import NoticesView from './views/NoticesView.vue'
import SubscriptionsView from './views/SubscriptionsView.vue'
import WeeklyView from './views/WeeklyView.vue'
import AdminView from './views/AdminView.vue'

type User = { username: string; role: string; email?: string | null; weeklyEmailOptIn?: boolean }
const token = ref(localStorage.getItem('yinyuan-token') || '')
const user = ref<User | null>(null)
const active = ref('assistant')
const busy = ref(false)
const loginMode = ref<'login' | 'register'>('login')
const form = ref({ username: '', password: '', email: '', weeklyEmailOptIn: false })
const isAdmin = computed(() => user.value?.role === 'ADMIN')
const menu = computed(() => [
  { id: 'assistant', label: '智能问答', icon: '✳' },
  { id: 'notices', label: '校园通知', icon: '▤' },
  { id: 'subscriptions', label: '我的订阅', icon: '◉' },
  { id: 'weekly', label: '每周简报', icon: '▦' },
  ...(isAdmin.value ? [{ id: 'admin', label: '管理工作台', icon: '⌘' }] : []),
])
const currentTitle = computed(() => menu.value.find(item => item.id === active.value)?.label || '智能问答')
const viewMap: Record<string, Component> = {
  assistant: AssistantView, notices: NoticesView, subscriptions: SubscriptionsView, weekly: WeeklyView, admin: AdminView,
}
const view = computed(() => viewMap[active.value] || AssistantView)

function toggleLoginMode() {
  loginMode.value = loginMode.value === 'login' ? 'register' : 'login'
  form.value.weeklyEmailOptIn = false
}

async function authenticate() {
  busy.value = true
  try {
    const result = loginMode.value === 'login'
      ? await api.login(form.value.username, form.value.password)
      : await api.register(form.value.username, form.value.password, form.value.email || undefined, form.value.weeklyEmailOptIn)
    token.value = result.data.token
    localStorage.setItem('yinyuan-token', token.value)
    user.value = {
      username: result.data.username,
      role: result.data.role,
      email: result.data.email,
      weeklyEmailOptIn: Boolean(result.data.weeklyEmailOptIn),
    }
    ElMessage.success(loginMode.value === 'login' ? '登录成功' : '账号创建成功')
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '登录失败，请检查账号或服务状态')
  } finally { busy.value = false }
}

async function loadUser() {
  if (!token.value) return
  try {
    const data = (await api.me()).data
    user.value = {
      username: data.username, role: data.role, email: data.email,
      weeklyEmailOptIn: Boolean(data.weekly_email_enabled),
    }
  } catch { localStorage.removeItem('yinyuan-token'); token.value = ''; user.value = null }
}
function logout() {
  localStorage.removeItem('yinyuan-token'); token.value = ''; user.value = null; active.value = 'assistant'
}
onMounted(loadUser)
</script>

<template>
  <div v-if="!user" class="login-shell">
    <div class="login-art">
      <div class="login-brand"><img src="/assets/school_badge.jpg" alt="北京印刷学院校徽" /><span>北京印刷学院</span></div>
      <div class="login-copy">
        <div class="eyebrow">CAMPUS KNOWLEDGE ASSISTANT</div>
        <h1>让校园信息<br /><em>一问即达</em></h1>
        <p>通知、制度与校园动态，统一检索，清晰溯源。</p>
      </div>
      <div class="login-foot">印苑百事通 · 校园信息助手</div>
    </div>
    <div class="login-form-wrap">
      <div class="login-card">
        <img class="assistant-mark" src="/assets/AI_Agent_logo.jpg" alt="印苑百事通" />
        <div class="eyebrow muted">欢迎使用</div>
        <h2>{{ loginMode === 'login' ? '登录印苑百事通' : '创建学生账号' }}</h2>
        <p class="muted">使用项目账号进入校园信息工作台</p>
        <el-form label-position="top" @submit.prevent="authenticate">
          <el-form-item label="用户名"><el-input v-model="form.username" size="large" autocomplete="username" placeholder="请输入用户名" @keyup.enter="authenticate" /></el-form-item>
          <el-form-item v-if="loginMode === 'register'" label="邮箱（可选）"><el-input v-model="form.email" size="large" autocomplete="email" placeholder="用于接收周报" /></el-form-item>
          <el-form-item v-if="loginMode === 'register'">
            <el-checkbox v-model="form.weeklyEmailOptIn">我同意通过邮件接收每周校园通知周报（可在订阅设置中随时关闭）</el-checkbox>
          </el-form-item>
          <el-form-item label="密码"><el-input v-model="form.password" size="large" show-password autocomplete="current-password" placeholder="至少 8 位" @keyup.enter="authenticate" /></el-form-item>
          <el-button class="primary-button full" type="primary" size="large" :loading="busy" @click="authenticate">{{ loginMode === 'login' ? '登录' : '创建账号' }}</el-button>
        </el-form>
        <button class="text-button" @click="toggleLoginMode">{{ loginMode === 'login' ? '还没有账号？创建学生账号' : '已有账号？返回登录' }}</button>
        <div class="login-note">本地首版账号系统。邮件周报需主动勾选同意并配置邮件服务。</div>
      </div>
    </div>
  </div>
  <div v-else class="app-shell">
    <aside class="sidebar">
      <div class="brand"><img src="/assets/AI_Agent_logo.jpg" alt="" /><div><strong>印苑百事通</strong><small>校园信息助手</small></div></div>
      <div class="side-label">工作台</div>
      <nav class="side-nav">
        <button v-for="item in menu" :key="item.id" :class="['nav-item', { active: active === item.id }]" @click="active = item.id"><span class="nav-icon">{{ item.icon }}</span>{{ item.label }}</button>
      </nav>
      <div class="sidebar-bottom">
        <div class="user-card"><div class="user-avatar">{{ user.username.slice(0,1).toUpperCase() }}</div><div class="user-meta"><strong>{{ user.username }}</strong><small>{{ isAdmin ? '系统管理员' : '学生用户' }}</small></div><el-dropdown trigger="click"><button class="more-button">···</button><template #dropdown><el-dropdown-menu><el-dropdown-item @click="logout">退出登录</el-dropdown-item></el-dropdown-menu></template></el-dropdown></div>
      </div>
    </aside>
    <main class="main-panel">
      <header class="topbar"><div><span class="breadcrumb">印苑百事通</span><span class="crumb-sep">/</span><strong>{{ currentTitle }}</strong></div><div class="topbar-right"><span class="online-dot"></span><span>校内信息工作台</span></div></header>
      <section class="page-content"><component :is="view" :user="user" /></section>
      <footer class="app-footer">信息来自学校公开页面与已导入制度文档 · 请以原始来源为准</footer>
    </main>
  </div>
</template>