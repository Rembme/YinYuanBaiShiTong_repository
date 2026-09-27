<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, type Notice, type Zone } from '../api'
const rows=ref<Notice[]>([]), zones=ref<Zone[]>([]), query=ref(''), zone=ref(''), busy=ref(false)
async function load(){busy.value=true;try{rows.value=(await api.notices({q:query.value||undefined,zoneId:zone.value||undefined})).data}catch{ElMessage.error('通知数据暂时无法加载')}finally{busy.value=false}}
function date(value?:string){return value?new Date(value).toLocaleDateString('zh-CN'):'日期待确认'}
onMounted(async()=>{try{zones.value=(await api.zones()).data}catch{} await load()})
</script>
<template>
  <div class="view-wrap"><div class="view-heading"><div><div class="eyebrow muted">CAMPUS UPDATES</div><h1>校园通知</h1><p>来自学校公开页面的通知与动态，点击原文核对详细信息。</p></div><div class="view-count"><strong>{{ rows.length }}</strong><small>条相关内容</small></div></div>
    <div class="filter-bar"><el-input v-model="query" clearable placeholder="搜索标题或通知内容" @keyup.enter="load"><template #prefix>⌕</template></el-input><el-select v-model="zone" clearable placeholder="全部专区" @change="load"><el-option v-for="item in zones" :key="item.id" :label="item.name" :value="item.id" /></el-select><el-button type="primary" @click="load">搜索</el-button></div>
    <div v-loading="busy" class="notice-grid"><a v-for="item in rows" :key="item.id" class="notice-card" :href="item.canonical_url" target="_blank" rel="noreferrer"><div class="notice-card-top"><el-tag size="small" effect="plain">{{ item.zone_name }}</el-tag><span>{{ date(item.published_at) }}</span></div><h3>{{ item.title }}</h3><p>{{ item.body?.slice(0,180) || '打开原文查看详情。' }}{{ item.body?.length>180?'…':'' }}</p><div class="notice-card-foot"><span>查看学校原文</span><b>↗</b></div></a><el-empty v-if="!busy && rows.length===0" description="暂无通知。管理员可在管理工作台运行采集任务。" /></div>
  </div>
</template>
