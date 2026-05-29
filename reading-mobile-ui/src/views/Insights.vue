<template>
  <div class="insights-container">
    <van-nav-bar
      title="阅读洞察"
      left-arrow
      @click-left="$router.back()"
      fixed
      placeholder
    />
    
    <div class="insights-content">
      <div v-if="!hasReport && !isGenerating" class="empty-state">
        <van-empty description="暂无洞察报告">
          <van-button round type="primary" class="bottom-button" @click="generateInsight">
            生成最新报告
          </van-button>
        </van-empty>
      </div>
      
      <div v-if="isGenerating" class="generating-state">
        <van-skeleton title :row="15" />
        <div class="gen-tip">正在分析您的阅读数据，请稍候...</div>
      </div>
      
      <div v-if="hasReport && !isGenerating" class="report-box">
        <div class="report-header">
          <van-button size="small" type="primary" plain round @click="generateInsight">重新生成</van-button>
        </div>
        <div class="markdown-body report-text" v-html="parsedReport"></div>
        <div class="report-footer">生成时间：{{ reportTime?.replace('T', ' ') }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import { getAuthHeaders } from '../utils/authHeaders'
import { showToast } from 'vant'
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
      reportTime.value = res.data.createTime || new Date().toISOString()
      showToast('报告生成成功')
    }
  } catch (err) {
    console.error(err)
    showToast('生成报告失败，请稍后再试')
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
  min-height: 100vh;
  background:
    radial-gradient(circle at top left, rgba(214, 191, 165, 0.22), transparent 28%),
    linear-gradient(180deg, #f8f2ea 0%, #f6efe5 34%, #faf6f0 100%);
}
.insights-content {
  padding: 15px;
}
.bottom-button {
  width: 160px;
  height: 40px;
}
.generating-state, .empty-state {
  margin-top: 20px;
  padding: 15px;
  border-radius: 22px;
  background: var(--color-bg-card, rgba(255, 255, 255, 0.45));
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid var(--color-border, rgba(255, 255, 255, 0.6));
  box-shadow: 0 8px 32px rgba(139, 111, 82, 0.08);
}
.gen-tip {
  text-align: center;
  color: #8a725d;
  margin-top: 20px;
  font-size: 14px;
}
.report-box {
  margin-top: 10px;
  padding: 18px;
  border-radius: 22px;
  background: var(--color-bg-card, rgba(255, 255, 255, 0.45));
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid var(--color-border, rgba(255, 255, 255, 0.6));
  box-shadow: 0 8px 32px rgba(139, 111, 82, 0.08);
}
.report-header {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 10px;
  border-bottom: 1px solid rgba(143, 117, 87, 0.12);
  padding-bottom: 10px;
}
.report-text {
  font-size: 14px;
  line-height: 1.8;
  color: #3d2c1f;
}
.report-footer {
  margin-top: 20px;
  text-align: right;
  font-size: 12px;
  color: #9a826c;
}
</style>
