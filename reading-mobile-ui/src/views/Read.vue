<script setup>
import { ref, onMounted, onBeforeUnmount, reactive, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { showToast, showConfirmDialog } from 'vant'

// Composables
import { useMobileReading } from '../composables/useMobileReading'
import { useMobileTTS } from '../composables/useMobileTTS'
import { useMobileAI } from '../composables/useMobileAI'
import { useMobileShelf } from '../composables/useMobileShelf'
import { useMobileShare } from '../composables/useMobileShare'
import { useMobileComments } from '../composables/useMobileComments'

// Components
import MobileCatalogPopup from '../components/Read/MobileCatalogPopup.vue'
import MobileAIPanel from '../components/Read/MobileAIPanel.vue'
import MobileCommentsPopup from '../components/Read/MobileCommentsPopup.vue'
import MobileShareActionSheet from '../components/Read/MobileShareActionSheet.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const userInfo = computed(() => authStore.user || {})
const bookId = route.params.id

// Reading Setting
const readingConfig = reactive({
  fontSize: 18, lineHeight: 1.8, theme: 'default', voice: 'cherry'
})

const voiceOptions = [
  { key: 'cherry', label: '甜美女声' },
  { key: 'zhiqi', label: '温柔女声' },
  { key: 'zhiying', label: '知性女声' },
  { key: 'zhiyuan', label: '阳光男声' }
]

const selectedParagraphIndex = ref(-1)
const selectedText = ref('')
const showAiActions = ref(false)

const reading = useMobileReading(bookId, userInfo, route)
const tts = useMobileTTS()
const ai = useMobileAI(bookId, userInfo, reading.bookInfo, selectedText)
const shelf = useMobileShelf(bookId, userInfo)
const share = useMobileShare(bookId, userInfo)
const comments = useMobileComments(bookId, userInfo)

const loadReadingSettings = () => {
  const saved = localStorage.getItem('mobileReadingConfig')
  if (saved) Object.assign(readingConfig, JSON.parse(saved))
}

watch(readingConfig, (newVal) => {
  localStorage.setItem('mobileReadingConfig', JSON.stringify(newVal))
}, { deep: true })

const handleBeforeUnload = () => { reading.saveProgress() }

const goBack = async () => {
  tts.stopAudioPlayback()
  await reading.saveProgress()
  router.push('/shelf')
}

const handleParagraphClick = (index) => {
  if (window.getSelection().toString().trim().length > 0) return
  selectedParagraphIndex.value = index
}

const handleSelectionEnd = () => {
  const selection = window.getSelection()
  const text = selection.toString().trim()
  if (text.length > 0) {
    selectedText.value = text
    showAiActions.value = true
  } else {
    showAiActions.value = false
  }
}

const handleAiAction = (type) => {
  showAiActions.value = false
  ai.showAiDrawer.value = true

  let modeInstruction = ''
  let displayMessage = ''

  if (type === 'EXPLAIN') {
    modeInstruction = '请用大白话详细解释这段话，越通俗越好'
    displayMessage = `【释疑】${selectedText.value}`
  } else if (type === 'SUMMARY') {
    modeInstruction = '请提炼这段话的核心摘要和关键点'
    displayMessage = `【提炼摘要】${selectedText.value}`
  } else if (type === 'CONTINUE') {
    modeInstruction = '请根据这段话的语境和风格，发挥想象继续往下续写'
    displayMessage = `【续写】${selectedText.value}`
  } else if (type === 'TTS') {
    ai.showAiDrawer.value = false
    tts.openAudioPlayer({
      text: selectedText.value,
      voice: readingConfig.voice,
      bookId: Number(bookId),
      chapterId: reading.catalog.value[reading.chapterIndex.value]?.id,
      chapterIndex: reading.chapterIndex.value,
      paragraphIndex: selectedParagraphIndex.value >= 0 ? selectedParagraphIndex.value : null,
      title: `《${reading.bookInfo.value.title}》片段朗读`,
      sourceType: 'paragraph'
    })
    return
  }
  ai.sendChat(selectedText.value, modeInstruction, displayMessage)
}

const openParagraphComments = (index) => {
  selectedParagraphIndex.value = index
  comments.showParagraphDrawer.value = true
  comments.fetchParagraphComments(reading.chapterIndex.value, index)
}

const toggleChapterTts = async () => {
  const chapter = reading.catalog.value[reading.chapterIndex.value]
  const fullText = reading.lines.value.join('，')
  if (!chapter || !fullText) {
    showToast('当前章节无内容，无法朗读')
    return
  }

  if (tts.audioPlayback.audioUrl && tts.audioPlayback.sourceType === 'chapter' && tts.audioPlayback.chapterId === chapter.id) {
    tts.audioPlayerVisible.value = true
    return
  }

  await tts.openAudioPlayer({
    text: fullText,
    voice: readingConfig.voice,
    bookId: Number(bookId),
    chapterId: chapter.id,
    chapterIndex: reading.chapterIndex.value,
    paragraphIndex: null,
    title: `《${reading.bookInfo.value.title}》第 ${reading.chapterIndex.value + 1} 章`,
    sourceType: 'chapter'
  })
}

onMounted(async () => {
  loadReadingSettings()
  await reading.loadBookInfo()
  await reading.loadCatalog()
  await reading.loadProgress()
  reading.applyRouteReadTarget(selectedParagraphIndex)
  await reading.loadCurrentChapter(selectedParagraphIndex, tts.stopAudioPlayback)
  shelf.checkShelfStatus()
  ai.fetchNotes()
  
  document.addEventListener('selectionchange', handleSelectionEnd)
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  document.removeEventListener('selectionchange', handleSelectionEnd)
  tts.stopAudioPlayback()
  reading.disconnectObserver()
  window.removeEventListener('beforeunload', handleBeforeUnload)
  reading.saveProgress()
})
</script>

<template>
  <div class="read-container" :class="`theme-${readingConfig.theme}`" @click="showAiActions = false">
    <!-- Header -->
    <van-nav-bar fixed placeholder @click-left="goBack" class="custom-nav">
      <template #left><van-icon name="arrow-left" size="18" /></template>
      <template #title><span class="book-title">{{ reading.bookInfo.value.title }}</span></template>
      <template #right>
        <van-icon name="setting-o" size="18" @click="reading.showSettings.value = true" style="margin-right: 15px;" />
        <van-icon name="wap-nav" size="18" @click="reading.showCatalog.value = true" />
      </template>
    </van-nav-bar>

    <van-loading v-if="reading.isLoading.value" type="spinner" vertical style="margin-top: 50px;">加载章节中...</van-loading>

    <!-- Content -->
    <div v-else class="read-content" :style="{ fontSize: readingConfig.fontSize + 'px', lineHeight: readingConfig.lineHeight }">
      <h2 class="chapter-title" v-if="reading.currentChapterTitle.value">{{ reading.currentChapterTitle.value }}</h2>

      <div
        v-for="(line, index) in reading.lines.value"
        :key="index"
        :id="'line-' + index"
        :data-index="index"
        class="text-paragraph"
        :class="{ 'selected-paragraph': index === selectedParagraphIndex }"
        @click.stop="handleParagraphClick(index)"
      >
        {{ line }}
        <!-- Paragraph Tools (visible when tapped) -->
        <div v-if="index === selectedParagraphIndex" class="paragraph-tools">
          <div class="tool-item" @click.stop="openParagraphComments(index)">
            <van-icon name="chat-o" /><span>评论</span>
          </div>
          <div class="tool-item" @click.stop="share.openSharePopup('paragraph', index)">
            <van-icon name="share-o" /><span>分享</span>
          </div>
        </div>
      </div>

      <van-empty v-if="reading.lines.value.length === 0" description="暂无内容" />

      <!-- Chapter Nav -->
      <div class="chapter-nav" v-if="reading.catalog.value.length > 0">
        <van-button size="small" :disabled="reading.chapterIndex.value === 0" @click="reading.changeChapter(-1, selectedParagraphIndex, tts.stopAudioPlayback)">上一章</van-button>
        <van-button size="small" :disabled="reading.chapterIndex.value >= reading.catalog.value.length - 1" @click="reading.changeChapter(1, selectedParagraphIndex, tts.stopAudioPlayback)">下一章</van-button>
      </div>
    </div>

    <!-- AI Actions Bar (When text is selected) -->
    <van-action-bar v-if="showAiActions && selectedText" class="ai-action-bar">
      <van-action-bar-button text="朗读" icon="volume-o" @click.stop="handleAiAction('TTS')" />
      <van-action-bar-button text="释意" icon="chat-o" @click.stop="handleAiAction('EXPLAIN')" />
      <van-action-bar-button text="续写" icon="edit" @click.stop="handleAiAction('CONTINUE')" />
      <van-action-bar-button text="提炼" icon="label-o" @click.stop="handleAiAction('SUMMARY')" />
    </van-action-bar>

    <!-- Side Floating Actions -->
    <div class="floating-actions">
      <div class="fab-btn" @click.stop="ai.showAiDrawer.value = true">
        <van-icon name="notes-o" />
        <span>助手</span>
      </div>
      <div class="fab-btn" @click.stop="toggleChapterTts">
        <van-loading v-if="tts.isAudioLoading.value && tts.audioPlayback.sourceType === 'chapter'" type="spinner" size="16" />
        <van-icon v-else :name="tts.isChapterPlaying.value ? 'pause-circle-o' : 'play-circle-o'" />
        <span>{{ (tts.isAudioLoading.value && tts.audioPlayback.sourceType === 'chapter') ? '生成中' : (tts.isChapterPlaying.value ? '暂停' : '听本章') }}</span>
      </div>
      <div class="fab-btn" @click.stop="shelf.toggleShelf" :class="{ 'is-added': shelf.isAddedToShelf.value }">
        <van-icon :name="shelf.isAddedToShelf.value ? 'star' : 'star-o'" />
        <span>收藏</span>
      </div>
      <div class="fab-btn" @click.stop="share.openSharePopup('book')">
        <van-icon name="share-o" />
        <span>分享</span>
      </div>
    </div>

    <!-- Components -->
    <MobileCatalogPopup 
      v-model:show="reading.showCatalog.value"
      :catalog="reading.catalog.value"
      :chapterIndex="reading.chapterIndex.value"
      @jump-to="(idx) => reading.jumpToChapter(idx, selectedParagraphIndex, tts.stopAudioPlayback)"
    />

    <MobileAIPanel 
      v-model:show="ai.showAiDrawer.value"
      v-model:activeTab="ai.activeAiTab.value"
      :chatList="ai.chatList.value"
      v-model:inputMessage="ai.inputMessage.value"
      :isThinking="ai.isThinking.value"
      :noteList="ai.noteList.value"
      @send-chat="ai.sendChat"
      @save-note="ai.saveNote"
      @delete-note="ai.handleDeleteNote"
    />

    <MobileCommentsPopup 
      v-model:show="comments.showParagraphDrawer.value"
      :lines="reading.lines.value"
      :selectedParagraphIndex="selectedParagraphIndex.value"
      :comments="comments.paragraphComments.value"
      v-model:newComment="comments.newParagraphComment.value"
      :isLoading="comments.isLoadingComments.value"
      :isSubmitting="comments.isSubmittingComment.value"
      :userInfo="userInfo"
      @submit="comments.submitParagraphComment(reading.chapterIndex.value, reading.lines.value[selectedParagraphIndex.value])"
      @delete="(id) => comments.deleteComment(id, reading.chapterIndex.value)"
      @like="(c) => comments.toggleLike(c)"
    />

    <MobileShareActionSheet 
      v-model:show="share.showSharePopup.value"
      :shareMode="share.shareMode.value"
      :isLoadingFriends="share.isLoadingShareFriends.value"
      :friendOptions="share.shareFriends.value"
      :bookInfo="reading.bookInfo.value"
      :selectedParagraphText="reading.lines.value[selectedParagraphIndex.value]"
      :audioTitle="tts.audioPlayback.title"
      :audioSourceLabel="tts.audioSourceLabel.value"
      v-model:selectedFriendId="share.selectedShareFriendId.value"
      v-model:shareMessage="share.shareMessage.value"
      :isSubmitting="share.isSubmittingShare.value"
      @submit="share.submitShare(reading.chapterIndex.value, reading.lines.value[selectedParagraphIndex.value])"
    />

    <!-- Shared Audio Share -->
    <MobileShareActionSheet 
      v-model:show="tts.audioSharePopupVisible.value"
      shareMode="audio"
      :isLoadingFriends="share.isLoadingShareFriends.value"
      :friendOptions="share.shareFriends.value"
      :bookInfo="reading.bookInfo.value"
      :audioTitle="tts.audioPlayback.title"
      :audioSourceLabel="tts.audioSourceLabel.value"
      v-model:selectedFriendId="share.selectedShareFriendId.value"
      v-model:shareMessage="share.shareMessage.value"
      :isSubmitting="share.isSubmittingShare.value"
      @submit="share.submitAudioShare(tts.audioPlayback).then(res => { if(res) tts.audioSharePopupVisible.value = false })"
    />

    <!-- Settings Popup -->
    <van-popup v-model:show="reading.showSettings.value" position="bottom" round :style="{ padding: '20px' }">
      <div class="setting-title">阅读设置</div>
      
      <div class="setting-item">
        <span class="label">主题</span>
        <div class="theme-options">
          <div class="theme-btn" :class="{active: readingConfig.theme==='default'}" @click="readingConfig.theme='default'">默认</div>
          <div class="theme-btn" :class="{active: readingConfig.theme==='green'}" @click="readingConfig.theme='green'" style="background: #eaf5df; color: #2e4a2d">护眼</div>
          <div class="theme-btn" :class="{active: readingConfig.theme==='dark'}" @click="readingConfig.theme='dark'" style="background: #1e1e1e; color: #eee">暗夜</div>
        </div>
      </div>
      
      <div class="setting-item">
        <span class="label">听书音色</span>
        <div class="theme-options" style="flex-wrap: wrap;">
          <div class="theme-btn" v-for="v in voiceOptions" :key="v.key" :class="{active: readingConfig.voice === v.key}" @click="readingConfig.voice = v.key">{{ v.label }}</div>
        </div>
      </div>

      <div class="setting-item">
        <span class="label">字号 ({{ readingConfig.fontSize }}px)</span>
        <van-stepper v-model="readingConfig.fontSize" min="12" max="30" step="1" />
      </div>

      <div class="setting-item">
        <span class="label">行距 ({{ readingConfig.lineHeight }})</span>
        <van-stepper v-model="readingConfig.lineHeight" min="1.2" max="2.5" step="0.1" decimal-length="1" />
      </div>
    </van-popup>

    <!-- Global Audio Player Mini Bar -->
    <div class="global-audio-bar" v-if="tts.audioPlayerVisible.value">
      <div class="audio-info">
        <van-icon name="music-o" class="music-icon" :class="{ rotating: tts.isAudioPlaying.value }" />
        <div class="audio-text">
          <div class="audio-title van-ellipsis">{{ tts.audioPlayback.title || '朗读中...' }}</div>
          <div class="audio-time">{{ tts.formatAudioTime(tts.audioCurrentTime.value) }} / {{ tts.formatAudioTime(tts.audioDuration.value) }}</div>
        </div>
      </div>
      <div class="audio-controls">
        <van-icon :name="tts.isAudioPlaying.value ? 'pause-circle-o' : 'play-circle-o'" @click="tts.toggleDialogAudioPlayback" />
        <van-icon name="share-o" @click="share.loadShareFriends().then(() => tts.audioSharePopupVisible.value = true)" />
        <van-icon name="cross" @click="tts.closeAudioPlayer" />
      </div>
      <audio
        ref="audioRef"
        style="display:none;"
        :src="tts.playableAudioUrl.value || undefined"
        @loadedmetadata="tts.handleAudioLoadedMetadata"
        @timeupdate="tts.handleAudioTimeUpdate"
        @play="tts.handleAudioPlay"
        @pause="tts.handleAudioPause"
        @ended="tts.handleAudioEnded"
      />
    </div>

  </div>
</template>

<style scoped>
/* Base Styles */
.read-container { min-height: 100vh; background-color: #f7f2e8; color: #333; transition: background-color 0.3s; padding-bottom: 80px; }
.read-content { padding: 15px; }

/* Themes */
.theme-default { background-color: #fdfcf8; color: #2c2925; }
.theme-green { background-color: #eaf5df; color: #2e4a2d; }
.theme-dark { background-color: #111; color: #aaa; }
.theme-dark .custom-nav { background-color: #1a1a1a; }
.theme-dark :deep(.van-nav-bar__title), .theme-dark :deep(.van-icon) { color: #ccc !important; }

/* Chapter & Paragraph */
.chapter-title { font-size: 22px; font-weight: bold; margin-bottom: 30px; text-align: center; }
.text-paragraph { text-indent: 2em; margin-bottom: 20px; transition: background-color 0.2s; padding: 5px; border-radius: 4px; position: relative; }
.text-paragraph.selected-paragraph { background-color: rgba(25, 137, 250, 0.1); }
.theme-dark .text-paragraph.selected-paragraph { background-color: rgba(255, 255, 255, 0.1); }

/* Paragraph Tools */
.paragraph-tools { position: absolute; right: 0; bottom: -30px; display: flex; gap: 15px; background: rgba(0,0,0,0.7); color: #fff; padding: 6px 12px; border-radius: 20px; z-index: 10; font-size: 12px; }
.tool-item { display: flex; align-items: center; gap: 4px; }

.chapter-nav { display: flex; justify-content: space-around; margin-top: 40px; }

/* Floating Actions */
.floating-actions { position: fixed; right: 15px; bottom: 80px; display: flex; flex-direction: column; gap: 15px; z-index: 99; }
.fab-btn { width: 44px; height: 44px; background: rgba(255,255,255,0.9); border-radius: 50%; display: flex; flex-direction: column; justify-content: center; align-items: center; box-shadow: 0 4px 12px rgba(0,0,0,0.1); font-size: 10px; color: #666; }
.theme-dark .fab-btn { background: rgba(40,40,40,0.9); color: #ccc; }
.fab-btn .van-icon { font-size: 18px; margin-bottom: 2px; }
.fab-btn.is-added { color: #1989fa; }

/* AI Action Bar */
.ai-action-bar { bottom: 60px; padding: 5px 10px; z-index: 100; border-radius: 20px; margin: 0 15px; box-shadow: 0 4px 12px rgba(0,0,0,0.15); width: auto; }

/* Settings */
.setting-title { font-size: 16px; font-weight: bold; margin-bottom: 20px; text-align: center; }
.setting-item { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.theme-options { display: flex; gap: 10px; }
.theme-btn { padding: 5px 15px; border-radius: 15px; font-size: 13px; border: 1px solid #ddd; }
.theme-btn.active { border-color: #1989fa; color: #1989fa; font-weight: bold; }

/* Audio Player Bar */
.global-audio-bar { position: fixed; bottom: 0; left: 0; right: 0; height: 60px; background: rgba(255,255,255,0.95); display: flex; align-items: center; justify-content: space-between; padding: 0 15px; box-shadow: 0 -2px 10px rgba(0,0,0,0.05); z-index: 110; }
.theme-dark .global-audio-bar { background: rgba(30,30,30,0.95); }
.audio-info { display: flex; align-items: center; flex: 1; overflow: hidden; }
.music-icon { font-size: 32px; color: #1989fa; margin-right: 10px; }
.rotating { animation: rotate 3s linear infinite; }
@keyframes rotate { 100% { transform: rotate(360deg); } }
.audio-text { flex: 1; overflow: hidden; }
.audio-title { font-size: 14px; font-weight: bold; color: #333; }
.theme-dark .audio-title { color: #eee; }
.audio-time { font-size: 11px; color: #999; }
.audio-controls { display: flex; gap: 15px; font-size: 24px; color: #666; }
.theme-dark .audio-controls { color: #ccc; }
</style>
