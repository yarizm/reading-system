<template>
  <div class="page-glass-container review-container-page">
    <div class="page-header">
      <div class="header-left">
        <el-button plain round class="back-btn glass-btn" @click="router.push('/')">
          <el-icon><ArrowLeft /></el-icon> 返回首页
        </el-button>
        <el-divider direction="vertical" />
        <div class="header-title-box">
          <h2>📖 每日回顾</h2>
        </div>
      </div>
      <div class="review-stats" v-if="reviewList.length > 0">
        今日 {{ currentIndex + 1 }} / {{ reviewList.length }} 完成
      </div>
    </div>

    <div class="review-page">
      <div v-if="!loading && reviewList.length === 0" class="empty-state">
        <div class="empty-icon">🎉</div>
        <h3>今日回顾已完成！</h3>
        <p>所有待回顾笔记都已复习完毕，明天再来吧。</p>
        <el-button type="primary" @click="$router.push('/notes')">查看笔记</el-button>
      </div>

      <div v-if="reviewList.length > 0" class="review-container">
        <div class="progress-bar">
          <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
        </div>

        <div class="card-wrapper" @click="flipped = !flipped">
          <div class="review-card" ref="cardRef" :class="{ 'is-flipped': flipped }">
            <div class="card-front" ref="frontRef">
              <div class="card-book">{{ currentNote.bookTitle }}</div>
              <div class="card-quote">"{{ currentNote.selectedText || '无选文' }}"</div>
              <div class="card-hint">点击翻转查看笔记</div>
            </div>
            <div class="card-back" ref="backRef">
              <div class="card-book">📝 我的笔记</div>
              <div class="card-content">{{ currentNote.content }}</div>
            </div>
          </div>
        </div>

        <div class="rate-buttons">
          <el-button class="rate-btn rate-forget" @click="rate(0)">
            <span class="rate-emoji">😵</span><span>忘记</span>
          </el-button>
          <el-button class="rate-btn rate-vague" @click="rate(3)">
            <span class="rate-emoji">🤔</span><span>模糊</span>
          </el-button>
          <el-button class="rate-btn rate-remember" @click="rate(5)">
            <span class="rate-emoji">😊</span><span>记得</span>
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const router = useRouter()

const loading = ref(true)
const reviewList = ref([])
const currentIndex = ref(0)
const flipped = ref(false)
const cardRef = ref(null)
const frontRef = ref(null)
const backRef = ref(null)

const currentNote = computed(() => reviewList.value[currentIndex.value]?.note || {})
const progressPercent = computed(() => reviewList.value.length ? Math.round((currentIndex.value / reviewList.value.length) * 100) : 0)

const updateCardHeight = () => {
  const activeFace = flipped.value ? backRef.value : frontRef.value
  if (activeFace && cardRef.value) {
    cardRef.value.style.height = activeFace.scrollHeight + 'px'
  }
}

watch([flipped, currentIndex], () => nextTick(updateCardHeight))

const loadToday = async () => {
  try {
    const res = await request.get('/api/review/today')
    if (res.data.code === '200') reviewList.value = res.data.data?.reviews || []
  } catch (e) { console.error(e) }
  finally {
    loading.value = false
    await nextTick()
    updateCardHeight()
  }
}

const rate = async (score) => {
  try {
    await request.post('/api/review/rate', { noteId: currentNote.value.id, score })
    flipped.value = false
    if (currentIndex.value < reviewList.value.length - 1) {
      currentIndex.value++
      await nextTick()
      cardRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    } else {
      reviewList.value = []
    }
  } catch (e) { ElMessage.error('提交失败') }
}

onMounted(() => loadToday())
</script>

<style scoped>
.review-container-page { padding: 18px 24px; }
.review-page { max-width: 600px; margin: 0 auto; padding: 20px 0; }
.review-stats { font-size: 14px; color: #666; }
.empty-state { text-align: center; padding: 60px 20px; }
.empty-icon { font-size: 48px; margin-bottom: 16px; }
.empty-state h3 { margin: 0 0 8px; color: #333; }
.empty-state p { color: #999; margin-bottom: 20px; }
.progress-bar { height: 6px; background: #eee; border-radius: 3px; margin-bottom: 30px; overflow: hidden; }
.progress-fill { height: 100%; background: linear-gradient(90deg, #667eea, #764ba2); border-radius: 3px; transition: width 0.3s; }
.card-wrapper { perspective: 1000px; cursor: pointer; margin-bottom: 30px; }
.review-card { position: relative; width: 100%; min-height: 300px; transition: transform 0.6s; transform-style: preserve-3d; }
.review-card.is-flipped { transform: rotateY(180deg); }
.card-front, .card-back { position: absolute; top: 0; left: 0; width: 100%; min-height: 300px; backface-visibility: hidden; border-radius: 12px; padding: 30px; box-shadow: 0 4px 20px rgba(0,0,0,0.1); display: flex; flex-direction: column; justify-content: center; }
.card-front { background: #fff; z-index: 2; }
.card-back { background: #f8f9ff; transform: rotateY(180deg); }
.card-book { font-size: 14px; color: #409eff; margin-bottom: 20px; }
.card-quote { font-size: 18px; color: #333; line-height: 1.8; font-style: italic; text-align: center; }
.card-content { font-size: 16px; color: #333; line-height: 1.8; }
.card-hint { text-align: center; color: #ccc; font-size: 12px; margin-top: 20px; }
.rate-buttons { display: flex; gap: 16px; justify-content: center; }
.rate-btn { flex: 1; max-width: 140px; height: 60px; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; border-radius: 12px; font-size: 14px; }
.rate-emoji { font-size: 20px; }
.rate-forget { border-color: #e74c3c; color: #e74c3c; }
.rate-forget:hover { background: #fdf0ef; }
.rate-vague { border-color: #f39c12; color: #f39c12; }
.rate-vague:hover { background: #fef9f0; }
.rate-remember { border-color: #2ecc71; color: #2ecc71; }
.rate-remember:hover { background: #f0fdf4; }
</style>
