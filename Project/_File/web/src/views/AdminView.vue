<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, type Zone } from '../api'

const props = defineProps<{ user: { username: string } }>()
const status=ref<any>({})
const sources=ref<any[]>([])
const runs=ref<any[]>([])
const documents=ref<any[]>([])
const users=ref<any[]>([])
const zones=ref<Zone[]>([])
const busy=ref(false)
const selectedFile=ref<File|null>(null)
const sourceForm=ref({name:'',zoneId:'mie',baseUrl:''})

async function refresh(){
  busy.value=true
  try{
    const [a,b,c,d,e,f]=await Promise.all([api.adminStatus(),api.sources(),api.crawlRuns(),api.documents(),api.zones(),api.users()])
    status.value=a.data;sources.value=b.data;runs.value=c.data;documents.value=d.data;zones.value=e.data;users.value=f.data
  }catch{ElMessage.error('管理数据加载失败，请确认管理员权限')}
  finally{busy.value=false}
}
async function crawl(){try{await api.crawl();ElMessage.success('采集任务已启动');await refresh()}catch(error:any){ElMessage.error(error?.response?.data?.message||'无法启动采集')}}
async function importPdf(){
  if(!selectedFile.value)return
  busy.value=true
  try{
    const result=await api.importPdf(selectedFile.value)
    ElMessage.success('导入完成：'+result.data.chunks+' 个知识片段，'+(result.data.embeddingMode==='dashscope'?'DashScope 向量':'本地回退向量'))
    selectedFile.value=null
    await refresh()
  }catch(error:any){ElMessage.error(error?.response?.data?.message||'PDF 导入失败')}
  finally{busy.value=false}
}
async function addSource(){
  try{await api.addSource(sourceForm.value);ElMessage.success('来源已添加');sourceForm.value={name:'',zoneId:'mie',baseUrl:''};await refresh()}
  catch(error:any){ElMessage.error(error?.response?.data?.message||'添加来源失败')}
}
async function saveUser(row:any){
  try{await api.updateUser(row.id,row.role,row.active);ElMessage.success('用户权限已更新');await refresh()}
  catch(error:any){ElMessage.error(error?.response?.data?.message||'用户权限更新失败');await refresh()}
}
function handleFileChange(event:Event){selectedFile.value=(event.target as HTMLInputElement).files?.[0]||null}
onMounted(refresh)
</script>

<template>
  <div class="view-wrap">
    <div class="view-heading"><div><div class="eyebrow muted">OPERATIONS</div><h1>管理工作台</h1><p>管理校园来源、采集任务、用户和制度知识库。</p></div><el-button @click="refresh">刷新状态</el-button></div>
    <div v-loading="busy" class="metric-grid">
      <article><small>已采集通知</small><strong>{{ status.notices||0 }}</strong></article>
      <article><small>知识库文档</small><strong>{{ status.documents||0 }}</strong></article>
      <article><small>有效数据源</small><strong>{{ status.sources||0 }}</strong></article>
      <article><small>采集任务</small><strong>{{ status.crawlRunning?'运行中':'待命' }}</strong></article>
    </div>
    <div class="admin-grid">
      <section class="admin-card">
        <div class="section-heading"><div><h2>数据来源</h2><p>只允许学校官方域名</p></div><el-button type="primary" @click="crawl">运行采集</el-button></div>
        <div class="source-list"><div v-for="source in sources" :key="source.id" class="source-row"><span class="source-status" :class="source.last_status?.toLowerCase()"></span><div><strong>{{ source.name }}</strong><small>{{ source.zone_name }} · {{ source.base_url }}</small></div><el-tag size="small" effect="plain">{{ source.last_status||'NEVER' }}</el-tag></div></div>
        <div class="source-form"><el-input v-model="sourceForm.name" placeholder="来源名称" /><el-select v-model="sourceForm.zoneId"><el-option v-for="zone in zones" :key="zone.id" :label="zone.name" :value="zone.id" /></el-select><el-input v-model="sourceForm.baseUrl" placeholder="https://xxx.bigc.edu.cn/" /><el-button @click="addSource">添加官方来源</el-button></div>
      </section>
      <section class="admin-card">
        <div class="section-heading"><div><h2>制度知识库</h2><p>上传 PDF 以建立带页码的检索索引</p></div></div>
        <div class="upload-box"><input type="file" accept="application/pdf,.pdf" @change="handleFileChange" /><div v-if="selectedFile" class="selected-file">{{ selectedFile.name }} · {{ (selectedFile.size/1024/1024).toFixed(1) }} MB</div><el-button type="primary" :disabled="!selectedFile" :loading="busy" @click="importPdf">导入 PDF</el-button></div>
        <div class="document-list"><div v-for="doc in documents" :key="doc.id" class="source-row"><span class="document-mark">PDF</span><div><strong>{{ doc.filename }}</strong><small>{{ doc.page_count }} 页 · {{ doc.chunk_count }} 个片段</small></div><el-tag size="small">{{ doc.status }}</el-tag></div><el-empty v-if="documents.length===0" description="尚未导入文档" :image-size="42" /></div>
      </section>
    </div>
    <section class="admin-card runs-card">
      <div class="section-heading"><div><h2>用户与权限</h2><p>最多显示最近 200 个账号；停用或角色变更立即影响后端权限。</p></div></div>
      <el-table :data="users" size="small">
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="email" label="邮箱" min-width="180" />
        <el-table-column label="角色" width="150"><template #default="{row}"><el-select v-model="row.role" :disabled="row.username===props.user.username"><el-option label="学生" value="STUDENT" /><el-option label="管理员" value="ADMIN" /></el-select></template></el-table-column>
        <el-table-column label="账号状态" width="130"><template #default="{row}"><el-switch v-model="row.active" :disabled="row.username===props.user.username" active-text="启用" inactive-text="停用" /></template></el-table-column>
        <el-table-column label="操作" width="100"><template #default="{row}"><el-button size="small" @click="saveUser(row)">保存</el-button></template></el-table-column>
      </el-table>
    </section>
    <section class="admin-card runs-card">
      <div class="section-heading"><div><h2>最近采集任务</h2><p>查看运行结果和失败原因</p></div></div>
      <el-table :data="runs" size="small">
        <el-table-column prop="started_at" label="开始时间" min-width="170" />
        <el-table-column prop="status" label="状态" width="180" />
        <el-table-column prop="source_count" label="来源数" width="90" />
        <el-table-column prop="notice_count" label="更新条目" width="100" />
        <el-table-column prop="error_summary" label="错误摘要" min-width="200" />
      </el-table>
      <el-empty v-if="runs.length===0" description="尚无采集记录" :image-size="42" />
    </section>
  </div>
</template>