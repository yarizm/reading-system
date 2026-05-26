<template>
  <div class="mobile-note-ai-toolbar">
    <van-button 
      type="primary" 
      size="small" 
      plain 
      round 
      icon="notes-o" 
      :loading="isSummarizing" 
      @click="runAiAction('summarize', '全书笔记总结')"
    >
      总结笔记
    </van-button>
    <van-button 
      type="success" 
      size="small" 
      plain 
      round 
      icon="question-o" 
      :loading="isQuizzing" 
      @click="runAiAction('quiz', '生成复习题')"
    >
      生成复习题
    </van-button>

    <van-action-sheet v-model:show="showResult" :title="resultTitle">
      <div class="ai-result-content markdown-body" v-html="parsedResult"></div>
    </van-action-sheet>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { showToast } from 'vant'
import axios from 'axios'
import { getAuthHeaders } from '@/utils/authHeaders'
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
      showToast('生成成功')
    }
  } catch (error) {
    console.error(error)
    showToast('AI 任务执行失败')
  } finally {
    isSummarizing.value = false
    isQuizzing.value = false
  }
}
</script>

<style scoped>
.mobile-note-ai-toolbar {
  display: flex;
  gap: 10px;
  justify-content: center;
  margin-bottom: 15px;
  padding: 0 10px;
}
.ai-result-content {
  padding: 15px;
  max-height: 60vh;
  overflow-y: auto;
  line-height: 1.6;
}
</style>
