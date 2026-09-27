<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, type Zone } from '../api'

const zones = ref<Zone[]>([])
const busy = ref(false)
const emailBusy = ref(false)
const email = ref('')
const weeklyEmailOptIn = ref(false)

async function load() {
  busy.value = true
  try {
    const [subscriptionResult, userResult] = await Promise.all([api.subscriptions(), api.me()])
    zones.value = subscriptionResult.data
    email.value = userResult.data.email || ''
    weeklyEmailOptIn.value = Boolean(userResult.data.weekly_email_enabled)
  } catch { ElMessage.error('订阅资料暂时无法加载') }
  finally { busy.value = false }
}
async function toggle(zone: Zone) {
  busy.value = true
  try {
    if (zone.subscribed) await api.unsubscribe(zone.id)
    else await api.subscribe(zone.id)
    zone.subscribed = !zone.subscribed
    ElMessage.success(zone.subscribed ? '已订阅专区' : '已取消订阅')
  } catch { ElMessage.error('修改订阅失败') }
  finally { busy.value = false }
}
async function saveEmailPreference() {
  emailBusy.value = true
  try {
    const result = await api.preferences(email.value.trim() || null, weeklyEmailOptIn.value)
    email.value = result.data.email || ''
    weeklyEmailOptIn.value = Boolean(result.data.weekly_email_enabled)
    ElMessage.success(weeklyEmailOptIn.value ? '邮件周报偏好已保存' : '邮件周报已关闭')
  } catch (error: any) {
    ElMessage.error(error?.response?.data?.message || '邮件偏好保存失败')
  } finally { emailBusy.value = false }
}
onMounted(load)
</script>

<template>
  <div class="view-wrap">
    <div class="view-heading"><div><div class="eyebrow muted">YOUR INTERESTS</div><h1>我的订阅</h1><p>选择你关心的专区，周报将优先汇总这些内容。</p></div></div>
    <div v-loading="busy" class="zone-grid">
      <article v-for="(zone,index) in zones" :key="zone.id" class="zone-card">
        <div :class="['zone-symbol','tone-' + (index + 1)]">{{ ['院','校','闻','＋'][index] }}</div>
        <div class="zone-copy"><h3>{{ zone.name }}</h3><p>{{ zone.description }}</p></div>
        <el-button :type="zone.subscribed?'primary':'default'" :plain="zone.subscribed" @click="toggle(zone)">{{ zone.subscribed?'已订阅':'订阅专区' }}</el-button>
      </article>
    </div>
    <section class="admin-card email-preference">
      <div class="section-heading"><div><h2>周报邮件</h2><p>只有主动开启且系统配置邮件服务后，周报才会发送到此邮箱。</p></div></div>
      <el-input v-model="email" type="email" autocomplete="email" placeholder="name@example.com" class="email-input" />
      <el-checkbox v-model="weeklyEmailOptIn">我同意通过邮件接收每周校园通知周报</el-checkbox>
      <div><el-button type="primary" :loading="emailBusy" @click="saveEmailPreference">保存邮件偏好</el-button></div>
    </section>
    <div class="sub-note"><span>✦</span><p>也可以在智能问答中说“订阅学校官网通知”等自然语言指令。识别专区后，系统会更新你的订阅。</p></div>
  </div>
</template>