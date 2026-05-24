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
import MobileAudioPlayer from '../components/Read/MobileAudioPlayer.vue'

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
const selectedParagraphs = ref([])
const selectedText = ref('')
const showAiActions = ref(false)
const showMenu = ref(false)

const selectedParagraphsText = computed(() => {
  return selectedParagraphs.value
    .map(idx => reading.lines.value[idx])
    .join('\n')
})

let isLongPressFired = false

const handleContainerClick = () => {
  if (isLongPressFired) {
    isLongPressFired = false
    return
  }
  if (selectedParagraphs.value.length > 0) {
    selectedParagraphs.value = []
    selectedParagraphIndex.value = -1
  } else {
    showMenu.value = !showMenu.value
  }
  showAiActions.value = false
}

const reading = useMobileReading(bookId, userInfo, route)
const tts = useMobileTTS()
const ai = useMobileAI(bookId, userInfo, reading.bookInfo, selectedText)
const shelf = useMobileShelf(bookId, userInfo)
const share = useMobileShare(bookId, userInfo)
const comments = useMobileComments(bookId, userInfo)

const showAiDrawer = ai.showAiDrawer
const activeAiTab = ai.activeAiTab

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
  router.back()
}

const handleParagraphClick = (index) => {
  if (window.getSelection().toString().trim().length > 0) return
  
  const pos = selectedParagraphs.value.indexOf(index)
  if (pos >= 0) {
    selectedParagraphs.value.splice(pos, 1)
  } else {
    selectedParagraphs.value.push(index)
  }
  selectedParagraphs.value.sort((a, b) => a - b)
  selectedParagraphIndex.value = selectedParagraphs.value.length > 0 ? selectedParagraphs.value[0] : -1
}

let touchTimer = null
let touchStartPos = { x: 0, y: 0 }

const onTouchStart = (e, index) => {
  touchStartPos = { x: e.touches[0].clientX, y: e.touches[0].clientY }
  touchTimer = setTimeout(() => {
    isLongPressFired = true
    handleParagraphClick(index)
  }, 450)
}

const onTouchMove = (e) => {
  if (!touchTimer) return
  const dx = e.touches[0].clientX - touchStartPos.x
  const dy = e.touches[0].clientY - touchStartPos.y
  if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
    clearTimeout(touchTimer)
    touchTimer = null
  }
}

const onTouchEnd = () => {
  if (touchTimer) {
    clearTimeout(touchTimer)
    touchTimer = null
  }
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
  const textToUse = selectedText.value || selectedParagraphsText.value
  if (!textToUse) return

  showAiActions.value = false
  showAiDrawer.value = true

  let modeInstruction = ''
  let displayMessage = ''

  if (type === 'EXPLAIN') {
    modeInstruction = '请用大白话详细解释这段话，越通俗越好'
    displayMessage = `【释疑】${textToUse}`
  } else if (type === 'SUMMARY') {
    modeInstruction = '请提炼这段话的核心摘要和关键点'
    displayMessage = `【提炼摘要】${textToUse}`
  } else if (type === 'CONTINUE') {
    modeInstruction = '请根据这段话的语境和风格，发挥想象继续往下续写'
    displayMessage = `【续写】${textToUse}`
  } else if (type === 'TTS') {
    showAiDrawer.value = false
    tts.openAudioPlayer({
      text: textToUse,
      voice: readingConfig.voice,
      bookId: Number(bookId),
      chapterId: reading.catalog.value[reading.chapterIndex.value]?.id,
      chapterIndex: reading.chapterIndex.value,
      paragraphIndex: selectedParagraphs.value.length > 0 ? selectedParagraphs.value[0] : null,
      title: `《${reading.bookInfo.value.title}》片段朗读`,
      sourceType: 'paragraph'
    })
    return
  }
  ai.sendChat(textToUse, modeInstruction, displayMessage)
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

const handleChangeChapter = (offset) => {
  selectedParagraphs.value = []
  selectedParagraphIndex.value = -1
  showAiActions.value = false
  reading.changeChapter(offset, tts.stopAudioPlayback)
}

const handleJumpChapter = (idx) => {
  selectedParagraphs.value = []
  selectedParagraphIndex.value = -1
  showAiActions.value = false
  reading.jumpToChapter(idx, tts.stopAudioPlayback)
}

onMounted(async () => {
  loadReadingSettings()
  await reading.loadBookInfo()
  await reading.loadCatalog()
  await reading.loadProgress()
  reading.applyRouteReadTarget(selectedParagraphIndex)
  await reading.loadCurrentChapter(tts.stopAudioPlayback)
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
  <div class="read-container" :class="`theme-${readingConfig.theme}`" @click="handleContainerClick">
    <!-- Header -->
    <transition name="slide-down">
      <van-nav-bar v-show="showMenu" fixed @click-left="goBack" class="custom-nav" @click.stop>
        <template #left><van-icon name="arrow-left" size="18" /></template>
        <template #title><span class="book-title">{{ reading.bookInfo.value.title }}</span></template>
        <template #right>
          <van-icon name="setting-o" size="18" @click.stop="reading.showSettings.value = true" style="margin-right: 15px;" />
          <van-icon name="wap-nav" size="18" @click.stop="reading.showCatalog.value = true" />
        </template>
      </van-nav-bar>
    </transition>

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
        :class="{ 'selected-paragraph': selectedParagraphs.includes(index) }"
        @touchstart="onTouchStart($event, index)"
        @touchmove="onTouchMove"
        @touchend="onTouchEnd"
      >
        {{ line }}
      </div>

      <van-empty v-if="reading.lines.value.length === 0" description="暂无内容" />

      <!-- Chapter Nav -->
      <div class="chapter-nav" v-if="reading.catalog.value.length > 0">
        <van-button size="small" round :disabled="reading.chapterIndex.value === 0" @click="handleChangeChapter(-1)">上一章</van-button>
        <van-button size="small" round :disabled="reading.chapterIndex.value >= reading.catalog.value.length - 1" @click="handleChangeChapter(1)">下一章</van-button>
      </div>
    </div>

    <!-- AI Actions Bar (When text is selected) -->
    <van-action-bar v-if="showAiActions && selectedText" class="ai-action-bar">
      <van-action-bar-button text="朗读" icon="volume-o" @click.stop="handleAiAction('TTS')" />
      <van-action-bar-button text="释意" icon="chat-o" @click.stop="handleAiAction('EXPLAIN')" />
      <van-action-bar-button text="续写" icon="edit" @click.stop="handleAiAction('CONTINUE')" />
      <van-action-bar-button text="提炼" icon="label-o" @click.stop="handleAiAction('SUMMARY')" />
    </van-action-bar>

    <!-- Bottom Integrated Panel -->
    <transition name="slide-up">
      <div class="bottom-menu-bar" v-show="showMenu" @click.stop>
        <div class="progress-bar-section">
          <van-button size="small" icon="arrow-left" class="btn-nav-chapter" :disabled="reading.chapterIndex.value === 0" @click="handleChangeChapter(-1)"></van-button>
          <div class="chapter-info-text">{{ reading.chapterIndex.value + 1 }} / {{ reading.catalog.value.length }}</div>
          <van-button size="small" icon="arrow" class="btn-nav-chapter" :disabled="reading.chapterIndex.value >= reading.catalog.value.length - 1" @click="handleChangeChapter(1)"></van-button>
        </div>
        
        <!-- Controls Grid Section -->
        <div class="actions-section">
          <div class="menu-action-btn" @click="showAiDrawer = true; showMenu = false">
            <van-icon name="notes-o" />
            <span>AI助手</span>
          </div>
          <div class="menu-action-btn" @click="toggleChapterTts">
            <van-loading v-if="tts.isAudioLoading.value && tts.audioPlayback.sourceType === 'chapter'" type="spinner" size="18" />
            <van-icon v-else :name="tts.isChapterPlaying.value ? 'pause-circle-o' : 'play-circle-o'" />
            <span>{{ (tts.isAudioLoading.value && tts.audioPlayback.sourceType === 'chapter') ? '生成中' : (tts.isChapterPlaying.value ? '暂停听书' : '听本章') }}</span>
          </div>
          <div class="menu-action-btn" @click="shelf.toggleShelf" :class="{ 'is-added': shelf.isAddedToShelf.value }">
            <van-icon :name="shelf.isAddedToShelf.value ? 'star' : 'star-o'" />
            <span>{{ shelf.isAddedToShelf.value ? '已收藏' : '收藏' }}</span>
          </div>
          <div class="menu-action-btn" @click="share.openSharePopup('book')">
            <van-icon name="share-o" />
            <span>分享</span>
          </div>
          <div class="menu-action-btn" @click="reading.showSettings.value = true">
            <van-icon name="setting-o" />
            <span>设置</span>
          </div>
        </div>
      </div>
    </transition>

    <!-- Paragraph Tap Sheet -->
    <transition name="slide-up">
      <div v-if="selectedParagraphs.length > 0" class="paragraph-action-bar glass-panel" @click.stop>
        <div class="paragraph-preview-text">“{{ selectedParagraphsText }}”</div>
        <div class="paragraph-action-buttons">
          <div class="para-btn" @click="openParagraphComments(selectedParagraphs[0])">
            <van-icon name="chat-o" />
            <span>写评论</span>
          </div>
          <div class="para-btn" @click="handleAiAction('TTS')">
            <van-icon name="volume-o" />
            <span>朗读</span>
          </div>
          <div class="para-btn" @click="handleAiAction('EXPLAIN')">
            <van-icon name="bulb-o" />
            <span>释义</span>
          </div>
          <div class="para-btn" @click="handleAiAction('SUMMARY')">
            <van-icon name="notes-o" />
            <span>提炼</span>
          </div>
          <div class="para-btn" @click="share.openSharePopup('paragraph', selectedParagraphs[0])">
            <van-icon name="share-o" />
            <span>分享</span>
          </div>
          <div class="para-btn close-btn" @click="selectedParagraphs = []; selectedParagraphIndex = -1">
            <van-icon name="cross" />
            <span>取消</span>
          </div>
        </div>
      </div>
    </transition>

    <!-- Components -->
    <MobileCatalogPopup 
      v-model:show="reading.showCatalog.value"
      :catalog="reading.catalog.value"
      :chapterIndex="reading.chapterIndex.value"
      @jump-to="handleJumpChapter"
    />

    <MobileAIPanel 
      v-model:show="showAiDrawer"
      v-model:activeTab="activeAiTab"
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
      :selectedParagraphIndex="selectedParagraphIndex"
      :comments="comments.paragraphComments.value"
      v-model:newComment="comments.newParagraphComment.value"
      :isLoading="comments.isLoadingComments.value"
      :isSubmitting="comments.isSubmittingComment.value"
      :userInfo="userInfo"
      @submit="comments.submitParagraphComment(reading.chapterIndex.value, selectedParagraphsText)"
      @delete="(id) => comments.deleteComment(id, reading.chapterIndex.value)"
      @like="(c) => comments.toggleLike(c)"
    />

    <MobileShareActionSheet 
      v-model:show="share.showSharePopup.value"
      :shareMode="share.shareMode.value"
      :isLoadingFriends="share.isLoadingShareFriends.value"
      :friendOptions="share.shareFriends.value"
      :bookInfo="reading.bookInfo.value"
      :selectedParagraphText="selectedParagraphsText"
      :audioTitle="tts.audioPlayback.title"
      :audioSourceLabel="tts.audioSourceLabel.value"
      v-model:selectedFriendId="share.selectedShareFriendId.value"
      v-model:shareMessage="share.shareMessage.value"
      :isSubmitting="share.isSubmittingShare.value"
      @submit="share.submitShare(reading.chapterIndex.value, selectedParagraphsText)"
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
    <van-popup v-model:show="reading.showSettings.value" position="bottom" round :style="{ padding: '24px 20px' }" @click.stop>
      <div class="setting-title">阅读设置</div>
      
      <div class="setting-item theme-setting-row">
        <span class="label">背景主题</span>
        <div class="theme-picker">
          <div class="theme-circle-btn theme-wheat" :class="{active: readingConfig.theme==='default'}" @click="readingConfig.theme='default'" title="雅致麦香">
            <van-icon v-if="readingConfig.theme==='default'" name="success" size="12" />
          </div>
          <div class="theme-circle-btn theme-pink" :class="{active: readingConfig.theme==='pink'}" @click="readingConfig.theme='pink'" title="淡雅粉蔻">
            <van-icon v-if="readingConfig.theme==='pink'" name="success" size="12" />
          </div>
          <div class="theme-circle-btn theme-teal" :class="{active: readingConfig.theme==='green'}" @click="readingConfig.theme='green'" title="沁心碧茗">
            <van-icon v-if="readingConfig.theme==='green'" name="success" size="12" />
          </div>
          <div class="theme-circle-btn theme-deepsea" :class="{active: readingConfig.theme==='blue'}" @click="readingConfig.theme='blue'" title="深海幽蓝">
            <van-icon v-if="readingConfig.theme==='blue'" name="success" size="12" />
          </div>
          <div class="theme-circle-btn theme-charcoal" :class="{active: readingConfig.theme==='dark'}" @click="readingConfig.theme='dark'" title="极夜玄碳">
            <van-icon v-if="readingConfig.theme==='dark'" name="success" size="12" />
          </div>
        </div>
      </div>
      
      <div class="setting-item" style="flex-direction: column; align-items: flex-start; gap: 15px;">
        <span class="label">听书音色</span>
        <div class="theme-options" style="flex-wrap: wrap; width: 100%; gap: 12px;">
          <van-button 
            v-for="v in voiceOptions" 
            :key="v.key" 
            size="small" 
            round 
            :type="readingConfig.voice === v.key ? 'primary' : 'default'" 
            @click.stop="readingConfig.voice = v.key"
          >
            {{ v.label }}
          </van-button>
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
    <!-- Extracted Mobile Audio Player Component -->
    <MobileAudioPlayer
      v-model:show="tts.audioPlayerVisible.value"
      :isAudioLoading="tts.isAudioLoading.value"
      :playback="tts.audioPlayback"
      :sourceLabel="tts.audioSourceLabel.value"
      :playableUrl="tts.playableAudioUrl.value"
      :currentTime="tts.audioCurrentTime.value"
      :duration="tts.audioDuration.value"
      :isAudioPlaying="tts.isAudioPlaying.value"
      :formatTime="tts.formatAudioTime"
      @closed="tts.closeAudioPlayer"
      @loadedmetadata="tts.handleAudioLoadedMetadata"
      @timeupdate="tts.handleAudioTimeUpdate"
      @play="tts.handleAudioPlay"
      @pause="tts.handleAudioPause"
      @ended="tts.handleAudioEnded"
      @toggle-playback="tts.toggleDialogAudioPlayback"
      @open-share="share.loadShareFriends().then(() => tts.audioSharePopupVisible.value = true)"
      @register-audio-ref="(ref) => tts.currentAudio.value = ref.value"
    />

  </div>
</template>

<style scoped>
/* Base Styles & Padding Fix */
.read-container {
  min-height: 100vh;
  background-color: #f7f2e8;
  color: #333;
  transition: background-color 0.3s;
}

.read-content {
  padding: calc(20px + var(--safe-top)) 20px calc(32px + var(--safe-bottom));
}

/* Custom Navigation Bar Overrides */
.custom-nav {
  z-index: 100;
}
:deep(.van-nav-bar__title) {
  font-family: var(--font-serif), serif;
  font-weight: 700;
}

/* Professional Paper Themes */
/* Wheat 麦香 */
.theme-default { background-color: #f4efe2; color: #2c261e; }
.theme-default .custom-nav { background-color: #ede7d7; border-bottom: 1px solid rgba(0, 0, 0, 0.05); }
.theme-default :deep(.van-nav-bar__title), .theme-default :deep(.van-icon) { color: #40362b !important; }

/* Pink 粉蔻 */
.theme-pink { background-color: #f6eef0; color: #463438; }
.theme-pink .custom-nav { background-color: #efdee2; border-bottom: 1px solid rgba(0, 0, 0, 0.05); }
.theme-pink :deep(.van-nav-bar__title), .theme-pink :deep(.van-icon) { color: #5a3d43 !important; }

/* Teal 碧茗 */
.theme-green { background-color: #e5eed9; color: #2a3c26; }
.theme-green .custom-nav { background-color: #dbecd0; border-bottom: 1px solid rgba(0, 0, 0, 0.05); }
.theme-green :deep(.van-nav-bar__title), .theme-green :deep(.van-icon) { color: #324a2e !important; }

/* DeepSea 幽蓝 */
.theme-blue { background-color: #162436; color: #a1b8cf; }
.theme-blue .custom-nav { background-color: #1a2c42; border-bottom: 1px solid rgba(255, 255, 255, 0.05); }
.theme-blue :deep(.van-nav-bar__title), .theme-blue :deep(.van-icon) { color: #b9d3eb !important; }
.theme-blue .bottom-menu-bar { background: rgba(26, 44, 66, 0.9); border-color: rgba(255, 255, 255, 0.08); color: #fff; }
.theme-blue .bottom-menu-bar .chapter-info-text, .theme-blue .bottom-menu-bar .menu-action-btn { color: #b9d3eb; }
.theme-blue .bottom-menu-bar .progress-bar-section { border-bottom-color: rgba(255, 255, 255, 0.08); }

/* Charcoal 玄碳 */
.theme-dark { background-color: #121212; color: #999999; }
.theme-dark .custom-nav { background-color: #1c1c1c; border-bottom: 1px solid rgba(255, 255, 255, 0.05); }
.theme-dark :deep(.van-nav-bar__title), .theme-dark :deep(.van-icon) { color: #b3b3b3 !important; }
.theme-dark .bottom-menu-bar { background: rgba(28, 28, 28, 0.92); border-color: rgba(255, 255, 255, 0.06); }
.theme-dark .bottom-menu-bar .chapter-info-text, .theme-dark .bottom-menu-bar .menu-action-btn { color: #b3b3b3; }
.theme-dark .bottom-menu-bar .progress-bar-section { border-bottom-color: rgba(255, 255, 255, 0.06); }

/* Chapter & Paragraph Styles */
.chapter-title {
  font-size: 24px;
  font-weight: 800;
  margin-bottom: 32px;
  text-align: center;
  font-family: var(--font-serif), serif;
}

.text-paragraph {
  text-indent: 2em;
  margin-bottom: 24px;
  transition: all 0.25s ease;
  padding: 6px 8px;
  border-radius: 4px;
}

/* Elegant selection highlight with golden indent bar */
.text-paragraph.selected-paragraph {
  background-color: rgba(163, 107, 70, 0.08) !important;
  box-shadow: inset 4px 0 0 0 #a36b46;
  border-radius: 0 4px 4px 0;
}
.theme-dark .text-paragraph.selected-paragraph {
  background-color: rgba(255, 255, 255, 0.05) !important;
  box-shadow: inset 4px 0 0 0 rgba(255, 255, 255, 0.3);
}

.chapter-nav {
  display: flex;
  justify-content: space-around;
  margin-top: 48px;
  padding-bottom: 12px;
}

/* Glassmorphism Bottom menu bar */
.bottom-menu-bar {
  position: fixed;
  bottom: calc(10px + var(--safe-bottom));
  left: 12px;
  right: 12px;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 20px;
  box-shadow: 0 12px 32px rgba(60, 40, 20, 0.15);
  border: 1px solid rgba(255, 255, 255, 0.5);
  padding: 16px;
  z-index: 99;
}

.progress-bar-section {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid rgba(139, 111, 82, 0.08);
}

.btn-nav-chapter {
  background: transparent !important;
  border: none !important;
  color: inherit !important;
}

.chapter-info-text {
  font-size: 13px;
  font-weight: 700;
  max-width: 60%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.actions-section {
  display: flex;
  justify-content: space-around;
  align-items: center;
}

.menu-action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  font-weight: 600;
  color: inherit;
  opacity: 0.8;
  transition: transform 0.2s;
}
.menu-action-btn:active {
  transform: scale(0.95);
}

.menu-action-btn .van-icon {
  font-size: 20px;
}

.menu-action-btn.is-added {
  color: #a36b46 !important;
  opacity: 1;
}

/* Elegant Paragraph tap action sheet */
.paragraph-action-bar {
  position: fixed;
  bottom: calc(16px + var(--safe-bottom));
  left: 12px;
  right: 12px;
  background: rgba(44, 38, 30, 0.94);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border-radius: 20px;
  box-shadow: 0 16px 40px rgba(0, 0, 0, 0.35);
  padding: 16px;
  z-index: 101;
  color: #fff;
}

.paragraph-preview-text {
  font-size: 12px;
  line-height: 1.6;
  color: #d1c8bd;
  max-height: 48px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  margin-bottom: 14px;
  font-style: italic;
  border-left: 2px solid #a36b46;
  padding-left: 8px;
}

.paragraph-action-buttons {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 4px;
  text-align: center;
}

.para-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-size: 10px;
  color: #eae3da;
}
.para-btn .van-icon {
  font-size: 18px;
}
.para-btn.close-btn {
  color: #ff8b8b;
}

/* Settings dialog styles */
.setting-title {
  font-size: 15px;
  font-weight: 700;
  margin-bottom: 24px;
  text-align: center;
  font-family: var(--font-serif), serif;
}

.setting-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.label {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-muted);
}

/* Premium circular theme picker */
.theme-picker {
  display: flex;
  gap: 14px;
  align-items: center;
}

.theme-circle-btn {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2px solid transparent;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.06);
  transition: all 0.25s cubic-bezier(0.25, 0.8, 0.25, 1);
}

.theme-circle-btn.active {
  transform: scale(1.15);
  border-color: #a36b46;
  box-shadow: 0 6px 14px rgba(163, 107, 70, 0.25);
}
.theme-circle-btn .van-icon {
  font-weight: bold;
}

/* Up-to-date color styles for theme circles */
.theme-wheat { background-color: #f4efe2; color: #40362b; }
.theme-pink { background-color: #f6eef0; color: #5a3d43; }
.theme-teal { background-color: #e5eed9; color: #324a2e; }
.theme-deepsea { background-color: #162436; color: #b9d3eb; }
.theme-charcoal { background-color: #121212; color: #b3b3b3; }

/* Transition animations */
.slide-down-enter-active,
.slide-down-leave-active {
  transition: all 0.3s cubic-bezier(0.25, 1, 0.5, 1);
}
.slide-down-enter-from,
.slide-down-leave-to {
  transform: translateY(-100%);
  opacity: 0;
}

.slide-up-enter-active,
.slide-up-leave-active {
  transition: all 0.3s cubic-bezier(0.25, 1, 0.5, 1);
}
.slide-up-enter-from,
.slide-up-leave-to {
  transform: translateY(120%);
  opacity: 0;
}
</style>
