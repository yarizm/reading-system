<template>
  <div class="insights-container page-glass-container">
    <el-card class="insight-card" shadow="never">
      <template #header>
        <div class="card-header">
          <el-page-header @back="goBack">
            <template #content>
              <span class="header-title">阅读洞察报告</span>
            </template>
          </el-page-header>
          <el-button type="primary" :loading="isGenerating" @click="generateInsight" round>
            生成最新报告
          </el-button>
        </div>
      </template>
      
      <div v-if="!hasReport && !isGenerating" class="empty-state">
        <el-empty description="暂无洞察报告，点击右上角生成您的第一份阅读洞察报告吧" />
      </div>
      
      <div v-if="isGenerating" class="generating-state">
        <el-skeleton :rows="10" animated />
        <div style="text-align: center; margin-top: 20px; color: #909399;">正在分析您的阅读数据，请稍候...</div>
      </div>
      
      <div v-if="hasReport && !isGenerating" class="report-content markdown-body" v-html="parsedReport"></div>
      
      <div class="report-footer" v-if="hasReport && !isGenerating">
        <span class="report-time">报告生成时间：{{ reportTime?.replace('T', ' ') }}</span>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { getAuthHeaders } from '../utils/authHeaders'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const router = useRouter()
const goBack = () => router.back()

const isGenerating = ref(false)
const hasReport = ref(false)
const reportContent = ref('')
const reportTime = ref('')

const parsedReport = computed(() => DOMPurify.sanitize(marked.parse(reportContent.value || '')))

const fetchLatestInsight = async () => {
  try {
    const res = await axios.get('/api/insight/latest', { headers: getAuthHeaders() })
    if (res.data && res.data.hasReport) {
      hasReport.value = true
      reportContent.value = res.data.report
      reportTime.value = res.data.createTime
    } else {
      hasReport.value = false
    }
  } catch (err) {
    console.error(err)
  }
}

const generateInsight = async () => {
  isGenerating.value = true
  try {
    const res = await axios.post('/api/insight/generate', {}, { headers: getAuthHeaders() })
    if (res.data && res.data.report) {
      hasReport.value = true
      reportContent.value = res.data.report
      reportTime.value = res.data.createTime || new Date().toISOString()
      ElMessage.success('报告生成成功')
    }
  } catch (err) {
    console.error(err)
    ElMessage.error('生成报告失败，请稍后再试')
  } finally {
    isGenerating.value = false
  }
}

onMounted(() => {
  fetchLatestInsight()
})
</script>

<style scoped>
.insights-container {
  padding: 24px;
  max-width: 1000px;
  margin: 0 auto;
}
:deep(.insight-card) {
  background: rgba(255, 255, 255, 0.65) !important;
  backdrop-filter: blur(24px) !important;
  -webkit-backdrop-filter: blur(24px) !important;
  border-radius: 16px !important;
  border: 1px solid rgba(255, 255, 255, 0.6) !important;
  box-shadow: 0 8px 32px rgba(60, 40, 20, 0.05) !important;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.header-title {
  font-weight: bold;
  font-size: 18px;
  color: #2e2520;
}
.report-content {
  line-height: 1.8;
  font-size: 15px;
  padding: 20px;
  background-color: #fcfcfc;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}
.report-footer {
  margin-top: 20px;
  text-align: right;
  color: #909399;
  font-size: 13px;
}
</style>
