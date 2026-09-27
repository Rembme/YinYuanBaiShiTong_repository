<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api } from '../api'
const reports=ref<any[]>([]),busy=ref(false),expanded=ref('')
onMounted(async()=>{busy.value=true;try{reports.value=(await api.reports()).data}catch{ElMessage.error('周报暂时无法加载')}finally{busy.value=false}})
function period(item:any){return String(item.period_start).slice(0,10)+' — '+String(item.period_end).slice(0,10)}
function emailStatus(value:string){
  if(value==='SENT')return '邮件已发送'
  if(value==='FAILED')return '邮件发送失败'
  if(value==='NOT_ENABLED')return '邮件未开启'
  return '仅站内简报'
}
</script>
<template><div class="view-wrap"><div class="view-heading"><div><div class="eyebrow muted">WEEKLY DIGEST</div><h1>每周简报</h1><p>按你的专区订阅整理近期校园信息。</p></div></div><div v-loading="busy" class="report-list"><article v-for="item in reports" :key="item.id" class="report-card"><button class="report-head" @click="expanded=expanded===item.id?'':item.id"><div class="report-icon">▦</div><div class="report-meta"><strong>校园通知周报</strong><small>{{ period(item) }} · {{ emailStatus(item.email_status) }}</small></div><span class="report-arrow">{{ expanded===item.id?'−':'＋' }}</span></button><div v-if="expanded===item.id" class="report-content">{{ item.content }}</div></article><el-empty v-if="!busy && reports.length===0" description="还没有周报。订阅专区后，系统会在每周一生成上一周的简报。" /></div></div></template>