<template>
  <div class="insights-container">
    <el-card class="insight-card">
      <template #header>
        <div class="card-header">
          <span>阅读洞察报告</span>
          <el-button type="primary" :loading="isGenerating" @click="generateInsight">
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
import axios from 'axios'
import { getAuthHeaders } from '@/utils/authHeaders'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

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
      reportTime.value = new Date().toISOString()
      ElMessage.success('报告生成成功')
      fetchLatestInsight() // 刷新获取最新
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
  padding: 20px;
  max-width: 1000px;
  margin: 0 auto;
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: bold;
  font-size: 18px;
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
