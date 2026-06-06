<template>
  <div class="review-page">
    <van-nav-bar title="每日回顾" left-arrow @click-left="$router.back()" />

    <div v-if="!loading && reviewList.length === 0" class="empty-state">
      <div class="empty-icon">🎉</div>
      <p>今日回顾已完成！</p>
      <van-button type="primary" size="small" @click="$router.push('/notes')">查看笔记</van-button>
    </div>

    <div v-if="reviewList.length > 0" class="review-content">
      <div class="progress-bar">
        <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
      </div>
      <div class="progress-text">{{ currentIndex + 1 }} / {{ reviewList.length }}</div>

      <div class="card-wrapper" @click="flipped = !flipped">
        <div class="review-card" :class="{ 'is-flipped': flipped }">
          <div class="card-front">
            <div class="card-book">{{ currentNote.bookTitle }}</div>
            <div class="card-quote">"{{ currentNote.selectedText || '无选文' }}"</div>
            <div class="card-hint">点击翻转</div>
          </div>
          <div class="card-back">
            <div class="card-book">📝 笔记</div>
            <div class="card-content">{{ currentNote.content }}</div>
          </div>
        </div>
      </div>

      <div class="rate-buttons">
        <van-button class="rate-btn" type="danger" plain @click="rate(0)">😵 忘记</van-button>
        <van-button class="rate-btn" type="warning" plain @click="rate(3)">🤔 模糊</van-button>
        <van-button class="rate-btn" type="success" plain @click="rate(5)">😊 记得</van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { showToast } from 'vant'
import request from '../utils/request'

const loading = ref(true)
const reviewList = ref([])
const currentIndex = ref(0)
const flipped = ref(false)

const currentNote = computed(() => reviewList.value[currentIndex.value]?.note || {})
const progressPercent = computed(() => reviewList.value.length ? Math.round((currentIndex.value / reviewList.value.length) * 100) : 0)

const loadToday = async () => {
  try {
    const res = await request.get('/api/review/today')
    if (res.data.code === '200') reviewList.value = res.data.data?.reviews || []
  } catch (e) { console.error(e) }
  finally { loading.value = false }
}

const rate = async (score) => {
  try {
    await request.post('/api/review/rate', { noteId: currentNote.value.id, score })
    flipped.value = false
    if (currentIndex.value < reviewList.value.length - 1) currentIndex.value++
    else reviewList.value = []
  } catch (e) { showToast('提交失败') }
}

onMounted(() => loadToday())
</script>

<style scoped>
.review-page { min-height: 100vh; background: #f5f5f5; }
.empty-state { text-align: center; padding: 80px 20px; }
.empty-icon { font-size: 48px; margin-bottom: 16px; }
.review-content { padding: 15px; }
.progress-bar { height: 4px; background: #eee; border-radius: 2px; margin-bottom: 8px; }
.progress-fill { height: 100%; background: linear-gradient(90deg, #667eea, #764ba2); border-radius: 2px; transition: width 0.3s; }
.progress-text { text-align: center; font-size: 13px; color: #999; margin-bottom: 20px; }
.card-wrapper { perspective: 1000px; cursor: pointer; margin-bottom: 20px; }
.review-card { position: relative; width: 100%; min-height: 250px; transition: transform 0.6s; transform-style: preserve-3d; }
.review-card.is-flipped { transform: rotateY(180deg); }
.card-front, .card-back { position: absolute; width: 100%; min-height: 250px; backface-visibility: hidden; border-radius: 12px; padding: 24px; box-shadow: 0 2px 12px rgba(0,0,0,0.08); display: flex; flex-direction: column; justify-content: center; }
.card-front { background: #fff; }
.card-back { background: #f8f9ff; transform: rotateY(180deg); }
.card-book { font-size: 13px; color: #409eff; margin-bottom: 16px; }
.card-quote { font-size: 16px; color: #333; line-height: 1.8; font-style: italic; text-align: center; }
.card-content { font-size: 15px; color: #333; line-height: 1.8; }
.card-hint { text-align: center; color: #ccc; font-size: 12px; margin-top: 16px; }
.rate-buttons { display: flex; gap: 12px; }
.rate-btn { flex: 1; height: 50px; border-radius: 10px; font-size: 15px; }
</style>
