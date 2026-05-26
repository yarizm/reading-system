<template>
  <div class="note-ai-toolbar">
    <el-button-group>
      <el-button type="primary" plain :loading="isSummarizing" @click="runAiAction('summarize', '全书笔记总结')">
        <el-icon><Document /></el-icon> 总结本书笔记
      </el-button>
      <el-button type="success" plain :loading="isQuizzing" @click="runAiAction('quiz', '生成复习题')">
        <el-icon><QuestionFilled /></el-icon> 基于笔记生成复习题
      </el-button>
    </el-button-group>

    <el-dialog
      v-model="showResult"
      :title="resultTitle"
      width="60%"
      destroy-on-close
    >
      <div class="ai-result-content markdown-body" v-html="parsedResult"></div>
      <template #footer>
        <el-button type="primary" @click="showResult = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Document, QuestionFilled } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { getAuthHeaders } from '../utils/authHeaders'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps({
  bookId: {
    type: [Number, String],
    required: true
  }
})

const isSummarizing = ref(false)
const isQuizzing = ref(false)
const showResult = ref(false)
const resultTitle = ref('')
const resultContent = ref('')

const parsedResult = computed(() => DOMPurify.sanitize(marked.parse(resultContent.value || '')))

const runAiAction = async (action, title) => {
  if (action === 'summarize') isSummarizing.value = true
  if (action === 'quiz') isQuizzing.value = true

  try {
    const res = await axios.post(`/api/note/ai/run/${props.bookId}`, {
      action,
      title
    }, {
      headers: getAuthHeaders()
    })

    if (res.data && res.data.result) {
      resultContent.value = res.data.result
      resultTitle.value = title
      showResult.value = true
      ElMessage.success('生成成功')
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('AI 任务执行失败')
  } finally {
    isSummarizing.value = false
    isQuizzing.value = false
  }
}
</script>

<style scoped>
.note-ai-toolbar {
  margin-bottom: 15px;
}
.ai-result-content {
  max-height: 60vh;
  overflow-y: auto;
  line-height: 1.6;
}
</style>
