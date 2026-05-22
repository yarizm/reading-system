<script setup>
import { ref, onMounted, onBeforeUnmount, reactive, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  ArrowLeft, Microphone, EditPen, ChatLineRound, DocumentCopy, Delete, Notebook, UserFilled,
  Operation, InfoFilled, Collection, ChatDotRound, Comment, Loading, Setting, MoreFilled, Share
} from '@element-plus/icons-vue'

// Composables
import { useReading } from '../composables/useReading'
import { useTTS } from '../composables/useTTS'
import { useAI } from '../composables/useAI'
import { useShelf } from '../composables/useShelf'
import { useShare } from '../composables/useShare'
import { useComments } from '../composables/useComments'

// Components
import CatalogDrawer from '../components/Read/CatalogDrawer.vue'
import AIAssistantPanel from '../components/Read/AIAssistantPanel.vue'
import CommentsDrawer from '../components/Read/CommentsDrawer.vue'
import AudioPlayer from '../components/Read/AudioPlayer.vue'
import ShareDialogs from '../components/Read/ShareDialogs.vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

const bookId = route.params.id
const userInfo = computed(() => authStore.user || {})

const selectedParagraphIndex = ref(-1)
const selectedText = ref('')

// Settings
const showSettings = ref(false)
const readingConfig = reactive({
  fontSize: 19, lineHeight: 1.8, theme: 'default', eyeCareMode: false, timerDuration: 45, voice: 'cherry'
})

const isTeenager = computed(() => {
  const age = userInfo.value.age
  return age !== undefined && age !== null && age < 18
})

let eyeCareInterval = null
let readingTime = 0

// Setup Composables
const reading = useReading(bookId, userInfo, route)
const tts = useTTS()
const ai = useAI(bookId, userInfo, reading.bookInfo, selectedText)
const shelf = useShelf(bookId, userInfo)
const share = useShare(bookId, userInfo)
const comments = useComments(bookId, userInfo)

const loadReadingSettings = () => {
  const saved = localStorage.getItem('readingConfig')
  const userAge = userInfo.value.age || 18
  if (saved) {
    Object.assign(readingConfig, JSON.parse(saved))
  } else {
    if (userAge < 18) {
      readingConfig.theme = 'green'
      readingConfig.fontSize = 20
      readingConfig.eyeCareMode = true
    } else if (userAge >= 55) {
      readingConfig.theme = 'high-contrast'
      readingConfig.fontSize = 24
      readingConfig.lineHeight = 2.0
    }
  }
  if (isTeenager.value) {
    readingConfig.eyeCareMode = true
    if (readingConfig.timerDuration > 45) {
      readingConfig.timerDuration = 45
    }
  }
}

const updateGlobalTheme = (theme) => {
  const html = document.documentElement
  html.classList.remove('theme-default', 'theme-green', 'theme-dark', 'theme-high-contrast', 'dark')
  html.classList.add(`theme-${theme}`)
  if (theme === 'dark' || theme === 'high-contrast') {
    html.classList.add('dark')
  }
}

// 立即读取本地配置，确保在 watch 初始化之前执行
loadReadingSettings()

watch(readingConfig, (newVal) => {
  localStorage.setItem('readingConfig', JSON.stringify(newVal))
  if (!newVal.eyeCareMode) {
    readingTime = 0
  }
  updateGlobalTheme(newVal.theme)
}, { deep: true, immediate: true })

const startEyeCareTimer = () => {
  if (eyeCareInterval) clearInterval(eyeCareInterval)
  eyeCareInterval = setInterval(() => {
    if (!readingConfig.eyeCareMode) return
    readingTime++
    const limitSeconds = readingConfig.timerDuration * 60
    if (readingTime >= limitSeconds) {
      ElMessageBox.alert(
          `您已经连续阅读 ${readingConfig.timerDuration} 分钟了，为了保护视力，请眺望远方休息一下吧！🌿`,
          '眼保健操时间',
          {
            confirmButtonText: '我休息好了',
            center: true,
            type: 'success',
            closeOnClickModal: !isTeenager.value,
            showClose: !isTeenager.value
          }
      ).then(() => {
        readingTime = 0
      })
    }
  }, 1000)
}

const handleGlobalClick = (e) => {
  const menu = document.querySelector('.ai-menu')
  if (ai.menuVisible.value && menu && !menu.contains(e.target)) {
    ai.menuVisible.value = false
    window.getSelection().removeAllRanges()
  }
  if (ai.drawerVisible.value) {
    const drawer = document.querySelector('.ai-drawer')
    const toggleBtn = document.querySelector('.sidebar-toggle.ai-toggle')
    if ((drawer && !drawer.contains(e.target)) && (toggleBtn && !toggleBtn.contains(e.target))) {
      ai.drawerVisible.value = false
    }
  }
}

const handleBeforeUnload = () => { reading.saveProgress() }

const goBack = async () => {
  tts.stopAudioPlayback()
  await reading.saveProgress()
  if (window.history.state && window.history.state.back) {
    router.back()
  } else {
    router.push(`/book/${bookId}`)
  }
}

const goToBookDetail = () => {
  reading.saveProgress()
  router.push(`/book/${bookId}`)
}

const handleParagraphClick = (index, event) => {
  if (window.getSelection().toString().trim().length > 0) return
  selectedParagraphIndex.value = index
}

const handleJumpToChapter = (idx) => {
  reading.jumpToChapter(idx, selectedParagraphIndex, tts.stopAudioPlayback)
}

const handleChangeChapter = (offset) => {
  reading.changeChapter(offset, selectedParagraphIndex, tts.stopAudioPlayback)
}

const openCommentDrawer = (index) => {
  comments.showParagraphCommentsDrawer.value = true
  comments.fetchParagraphComments(reading.chapterIndex.value, index)
}

const jumpToMyComment = (comment) => {
  if (comment.chapterIndex !== reading.chapterIndex.value) {
    reading.chapterIndex.value = comment.chapterIndex
    reading.loadCurrentChapter(selectedParagraphIndex, tts.stopAudioPlayback).then(() => {
      reading.scrollToLine(comment.paragraphIndex)
      handleParagraphClick(comment.paragraphIndex)
    })
  } else {
    reading.scrollToLine(comment.paragraphIndex)
    selectedParagraphIndex.value = comment.paragraphIndex
    handleParagraphClick(comment.paragraphIndex)
  }
  comments.showMyCommentsDrawer.value = false
}

const toggleChapterTts = async () => {
  const chapter = reading.catalog.value[reading.chapterIndex.value]
  const fullText = reading.lines.value.join('，')
  if (!chapter || !fullText) {
    ElMessage.warning('当前章节无内容，无法朗读')
    return
  }

  if (
    tts.audioPlayback.audioUrl &&
    tts.audioPlayback.sourceType === 'chapter' &&
    tts.audioPlayback.chapterId === chapter.id
  ) {
    tts.audioPlayerVisible.value = true
    return
  }

  await tts.openAudioPlayer({
    text: fullText,
    voice: readingConfig.voice || 'cherry',
    bookId: Number(bookId),
    chapterId: chapter.id,
    chapterIndex: reading.chapterIndex.value,
    paragraphIndex: null,
    title: `《${reading.bookInfo.value.title}》第 ${reading.chapterIndex.value + 1} 章`,
    sourceType: 'chapter'
  })
}

const handleAiAction = (type) => {
  ai.menuVisible.value = false
  ai.drawerVisible.value = true

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
    ai.drawerVisible.value = false
    tts.openAudioPlayer({
      text: selectedText.value,
      voice: readingConfig.voice || 'cherry',
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

onMounted(async () => {
  await reading.loadBookInfo()
  await reading.loadCatalog()
  await reading.loadProgress()
  reading.applyRouteReadTarget(selectedParagraphIndex)
  await reading.loadCurrentChapter(selectedParagraphIndex, tts.stopAudioPlayback)
  shelf.checkShelfStatus()

  startEyeCareTimer()

  document.addEventListener('mousedown', handleGlobalClick)
  ai.fetchNotes()
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  document.documentElement.classList.remove('theme-default', 'theme-green', 'theme-dark', 'theme-high-contrast', 'dark')
  document.removeEventListener('mousedown', handleGlobalClick)
  tts.stopAudioPlayback()
  reading.disconnectObserver()
  window.removeEventListener('beforeunload', handleBeforeUnload)
  reading.saveProgress()
  if (eyeCareInterval) clearInterval(eyeCareInterval)
})
</script>

<template>
  <div class="read-container" :class="`theme-${readingConfig.theme}`" @mouseup="ai.handleMouseUp" v-loading="reading.isLoading.value">

    <header class="read-header">
      <div class="left">
        <el-button link @click="goBack">
          <el-icon :size="20"><ArrowLeft /></el-icon> 返回书架
        </el-button>
        <span class="book-title">{{ reading.bookInfo.value.title }}</span>
      </div>
      <div class="right">
        <el-button link @click="showSettings = true">
          <el-icon :size="20"><Setting /></el-icon> 设置
        </el-button>
        <el-button link @click="reading.showBookInfoDialog.value = true">
          <el-icon :size="20"><InfoFilled /></el-icon> 信息
        </el-button>
        <el-button link @click="reading.showCatalog.value = true">
          <el-icon :size="20"><Operation /></el-icon> 目录
        </el-button>
      </div>
    </header>

    <main class="read-content">
      <h2 class="chapter-title" v-if="reading.currentChapterTitle.value">{{ reading.currentChapterTitle.value }}</h2>

      <div
          v-for="(line, index) in reading.lines.value"
          :key="index"
          :id="'line-' + index"
          :data-index="index"
          class="text-paragraph"
          :class="{ 'selected-paragraph': index === selectedParagraphIndex }"
          :style="{
            fontSize: readingConfig.fontSize + 'px',
            lineHeight: readingConfig.lineHeight
          }"
          @click="handleParagraphClick(index, $event)"
      >
        {{ line }}
        <div v-if="index === selectedParagraphIndex" class="paragraph-tools">
          <el-tooltip content="评论" placement="top">
            <el-icon class="tool-icon" @click.stop="openCommentDrawer(index)"><ChatDotRound /></el-icon>
          </el-tooltip>
          <el-tooltip content="分享段落" placement="top">
            <el-icon class="tool-icon" @click.stop="share.openParagraphShareDialog(index)"><Share /></el-icon>
          </el-tooltip>
        </div>
      </div>

      <div v-if="reading.lines.value.length === 0 && !reading.isLoading.value" class="empty-tip">
        暂无内容
      </div>

      <div class="chapter-nav" v-if="reading.catalog.value.length > 0">
        <el-button :disabled="reading.chapterIndex.value === 0" @click="handleChangeChapter(-1)">上一章</el-button>
        <el-button :disabled="reading.chapterIndex.value >= reading.catalog.value.length - 1" @click="handleChangeChapter(1)">下一章</el-button>
      </div>
    </main>

    <!-- Side Toggles -->
    <div class="sidebar-toggle ai-toggle" @click.stop="ai.toggleSidebar">
      <el-icon size="24"><Notebook /></el-icon>
      <span class="toggle-text">助手</span>
    </div>

    <div class="sidebar-toggle chapter-tts-toggle" @click.stop="toggleChapterTts">
      <el-icon size="24" v-if="tts.isAudioLoading.value && tts.audioPlayback.sourceType === 'chapter'" class="is-loading"><Loading /></el-icon>
      <el-icon size="24" v-else-if="tts.isChapterPlaying.value"><Microphone style="color: #67C23A;" /></el-icon>
      <el-icon size="24" v-else><Microphone /></el-icon>
      <span class="toggle-text">{{ (tts.isAudioLoading.value && tts.audioPlayback.sourceType === 'chapter') ? '生成中' : (tts.isChapterPlaying.value ? '暂停' : '听本章') }}</span>
    </div>

    <div class="sidebar-toggle add-to-shelf-toggle" @click.stop="shelf.toggleShelf" :class="{ 'is-added': shelf.isAddedToShelf.value }">
      <el-icon size="24" v-if="shelf.isCheckingShelf.value"><Loading /></el-icon>
      <el-icon size="24" v-else-if="shelf.isAddedToShelf.value"><Collection /></el-icon>
      <el-icon size="24" v-else><Collection /></el-icon>
      <span class="toggle-text">{{ shelf.isAddedToShelf.value ? '已收藏' : '收藏' }}</span>
    </div>

    <div class="sidebar-toggle my-comments-toggle" @click.stop="comments.openMyComments">
      <el-icon size="24"><Comment /></el-icon>
      <span class="toggle-text">足迹</span>
    </div>

    <div class="sidebar-toggle share-book-toggle" @click.stop="share.openBookShareDialog">
      <el-icon size="24"><Share /></el-icon>
      <span class="toggle-text">分享</span>
    </div>

    <!-- Modals & Drawers -->
    <CatalogDrawer 
      v-model:show="reading.showCatalog.value" 
      :catalog="reading.catalog.value" 
      :chapterIndex="reading.chapterIndex.value" 
      @jump-to="handleJumpToChapter"
    />

    <CommentsDrawer 
      v-model:show="comments.showParagraphCommentsDrawer.value"
      :lines="reading.lines.value"
      :selectedParagraphIndex="selectedParagraphIndex"
      :comments="comments.paragraphComments.value"
      v-model:newComment="comments.newParagraphComment.value"
      :isLoading="comments.isLoadingComments.value"
      :isSubmitting="comments.isSubmittingComment.value"
      :userInfo="userInfo"
      @submit="comments.submitParagraphComment(reading.chapterIndex.value, reading.lines.value[selectedParagraphIndex])"
      @delete="(id) => comments.deleteComment(id, reading.chapterIndex.value)"
      @like="(comment) => comments.toggleLike(comment)"
      @go-to-user="(id) => { if(id) router.push(`/user/${id}`) }"
    />

    <AIAssistantPanel 
      v-model:show="ai.drawerVisible.value"
      :aiTitle="ai.aiTitle.value"
      :drawerDirection="ai.drawerDirection.value"
      :drawerWidth="ai.drawerWidth.value"
      v-model:activeTab="ai.activeTab.value"
      :chatList="ai.chatList.value"
      v-model:inputMessage="ai.inputMessage.value"
      :isThinking="ai.isThinking.value"
      :noteList="ai.noteList.value"
      @toggle-direction="ai.toggleDrawerDirection"
      @start-resize="ai.startResize"
      @send-chat="ai.sendChat"
      @save-note="ai.saveNote"
      @delete-note="ai.handleDeleteNote"
    />

    <AudioPlayer 
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
      @download="tts.downloadCurrentAudio"
      @open-share="tts.audioShareDialogVisible.value = true"
      @register-audio-ref="(ref) => tts.currentAudio.value = ref.value"
    />

    <ShareDialogs 
      v-model:showBookShare="share.showBookShareDialog.value"
      v-model:showParagraphShare="share.showParagraphShareDialog.value"
      v-model:showAudioShare="tts.audioShareDialogVisible.value"
      :isLoadingFriends="share.isLoadingShareFriends.value"
      :friendOptions="share.shareFriendOptions.value"
      :bookInfo="reading.bookInfo.value"
      
      v-model:bookShareFriendId="share.bookShareFriendId.value"
      v-model:bookShareMessage="share.bookShareMessage.value"
      :isSubmittingBookShare="share.isSubmittingBookShare.value"
      
      v-model:paragraphShareFriendId="share.paragraphShareFriendId.value"
      v-model:paragraphShareMessage="share.paragraphShareMessage.value"
      :paragraphShareIndex="share.paragraphShareIndex.value"
      :chapterIndex="reading.chapterIndex.value"
      :selectedParagraphText="reading.lines.value[share.paragraphShareIndex.value]"
      :isSubmittingParagraphShare="share.isSubmittingParagraphShare.value"
      
      v-model:audioShareFriendId="share.audioShareFriendId.value"
      v-model:audioShareMessage="share.audioShareMessage.value"
      :audioPlayback="tts.audioPlayback"
      :audioSourceLabel="tts.audioSourceLabel.value"
      :isSubmittingAudioShare="share.isSubmittingAudioShare.value"

      @submit-book="share.submitBookShare"
      @submit-paragraph="share.submitParagraphShare(reading.chapterIndex.value, reading.lines.value[share.paragraphShareIndex.value])"
      @submit-audio="share.submitAudioShare(tts.audioPlayback)"
    />

    <!-- Settings Drawer & Modals kept inline as they are small -->
    <el-drawer v-model="showSettings" title="阅读设置" direction="rtl" size="320px" append-to-body>
      <div class="setting-group">
        <div class="setting-label">背景主题</div>
        <div class="theme-options">
          <div class="theme-btn t-default" :class="{active: readingConfig.theme==='default'}" @click="readingConfig.theme='default'">默认</div>
          <div class="theme-btn t-green" :class="{active: readingConfig.theme==='green'}" @click="readingConfig.theme='green'">护眼</div>
          <div class="theme-btn t-dark" :class="{active: readingConfig.theme==='dark'}" @click="readingConfig.theme='dark'">暗夜</div>
          <div class="theme-btn t-high" :class="{active: readingConfig.theme==='high-contrast'}" @click="readingConfig.theme='high-contrast'">适老</div>
        </div>
      </div>
      <div class="setting-group">
        <div class="setting-label">听书音色</div>
        <div class="theme-options">
          <div class="theme-btn t-default" :class="{active: readingConfig.voice==='cherry'}" @click="readingConfig.voice='cherry'">甜美女声</div>
          <div class="theme-btn t-default" :class="{active: readingConfig.voice==='zhiqi'}" @click="readingConfig.voice='zhiqi'">温柔女声</div>
          <div class="theme-btn t-default" :class="{active: readingConfig.voice==='zhiying'}" @click="readingConfig.voice='zhiying'">知性女声</div>
          <div class="theme-btn t-default" :class="{active: readingConfig.voice==='zhiyuan'}" @click="readingConfig.voice='zhiyuan'">阳光男声</div>
        </div>
      </div>
      <div class="setting-group">
        <div class="setting-label">字体大小 ({{ readingConfig.fontSize }}px)</div>
        <el-slider v-model="readingConfig.fontSize" :min="14" :max="36" :step="1" show-input :show-input-controls="false"/>
      </div>
      <div class="setting-group">
        <div class="setting-label">行间距 ({{ readingConfig.lineHeight }})</div>
        <el-slider v-model="readingConfig.lineHeight" :min="1.4" :max="2.5" :step="0.1" />
      </div>
      <div class="setting-group">
        <div class="setting-label"><span>青少年护眼模式</span><el-switch v-model="readingConfig.eyeCareMode" :disabled="isTeenager" /></div>
        <div class="desc" v-if="isTeenager" style="color: #F56C6C; margin-top: 5px;"><el-icon><InfoFilled /></el-icon> 系统检测到您未满18岁，已强制开启视力保护</div>
      </div>
    </el-drawer>

    <el-dialog v-model="reading.showBookInfoDialog.value" title="书籍信息" width="400px" center append-to-body class="book-info-dialog">
      <div class="book-info-content">
        <img :src="reading.bookInfo.value.coverUrl || 'https://via.placeholder.com/150'" class="book-cover-large"  alt=""/>
        <h3 class="info-title">{{ reading.bookInfo.value.title }}</h3>
        <p class="info-meta"><el-icon><UserFilled /></el-icon> 作者：{{ reading.bookInfo.value.author }}</p>
        <div class="info-desc"><h4>简介：</h4><p>{{ reading.bookInfo.value.description || '暂无简介' }}</p></div>
        <div style="margin-top: 20px; width: 100%;"><el-button type="primary" round style="width: 100%" @click="goToBookDetail"><el-icon style="margin-right: 5px"><MoreFilled /></el-icon> 查看书籍详情 & 评分</el-button></div>
      </div>
    </el-dialog>

    <el-drawer v-model="comments.showMyCommentsDrawer.value" title="我的评论足迹" direction="ltr" size="350px">
      <div class="my-comment-list">
        <el-empty v-if="comments.myCommentsList.value.length === 0" description="暂无评论" />
        <div v-for="c in comments.myCommentsList.value" :key="c.id" class="my-comment-item" @click="jumpToMyComment(c)">
          <div class="my-comment-pos">第 {{ c.chapterIndex + 1 }} 章 · 第 {{ c.paragraphIndex + 1 }} 段</div>
          <div class="my-comment-quote">{{ c.quote }}</div>
          <div class="my-comment-content">{{ c.content }}</div>
          <div class="my-comment-time">{{ c.createTime?.replace('T', ' ') }}</div>
        </div>
      </div>
    </el-drawer>

    <div v-if="ai.menuVisible.value" class="ai-menu" :style="ai.menuStyle.value" @mousedown.stop>
      <div class="menu-item" @mousedown.prevent="handleAiAction('TTS')"><el-icon><Microphone /></el-icon> 朗读</div>
      <div class="menu-item" @mousedown.prevent="handleAiAction('EXPLAIN')"><el-icon><ChatLineRound /></el-icon> 释意</div>
      <div class="menu-item" @mousedown.prevent="handleAiAction('CONTINUE')"><el-icon><EditPen /></el-icon> 续写</div>
      <div class="menu-item" @mousedown.prevent="handleAiAction('SUMMARY')"><el-icon><DocumentCopy /></el-icon> 提炼</div>
    </div>

  </div>
</template>

<style scoped>
/* ==================================================
   Modernized Premium Reading UI Styles
================================================== */
.read-container { 
  min-height: 100vh; 
  transition: background-color 0.4s ease, color 0.4s ease;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  background: linear-gradient(180deg, #f7f2e8 0%, #fdfcf8 38%, #f6f1e7 100%);
  color: #2c2925;
}

/* === Themes === */
/* Default: Elegant paper-like reading experience */
.read-container.theme-default {
  background: linear-gradient(180deg, #f7f2e8 0%, #fdfcf8 38%, #f6f1e7 100%);
  color: #2c2925;
}
.theme-default .read-header { background: rgba(253, 252, 248, 0.85); border-bottom: 1px solid rgba(0,0,0,0.04); color: #2c2925; }
.theme-default .text-paragraph { color: #2c2925; }

/* Eye-care (Green): Soothing pastel mint */
.read-container.theme-green {
  background: linear-gradient(180deg, #dceccf 0%, #eaf5df 38%, #d7e8c7 100%);
  color: #2e4a2d;
}
.theme-green .read-header { background: rgba(220, 237, 200, 0.85); border-bottom: 1px solid rgba(46,74,45,0.1); color: #2e4a2d; }
.theme-green .text-paragraph { color: #2e4a2d; }
.theme-green .text-paragraph:hover { background-color: rgba(46, 74, 45, 0.04); }

/* Dark: Deep slate for OLED / night reading */
.read-container.theme-dark {
  background: linear-gradient(180deg, #111315 0%, #181a1b 38%, #0d0f10 100%);
  color: #d0d0d0;
}
.theme-dark .read-header { background: rgba(24, 26, 27, 0.85); border-bottom: 1px solid rgba(255,255,255,0.05); color: #e8e8e8; }
.theme-dark .text-paragraph { color: #d0d0d0; }
.theme-dark .chapter-title { color: #e8e8e8; }
.theme-dark .text-paragraph:hover { background-color: rgba(255, 255, 255, 0.03); }
.theme-dark .book-title { color: #e8e8e8; }

/* High-contrast: Maximum readability */
.read-container.theme-high-contrast {
  background: #000000;
  color: #ffffff;
}
.theme-high-contrast .read-header { background: #000; border-bottom: 2px solid #555; color: #fff; }
.theme-high-contrast .text-paragraph { color: #ffffff; font-weight: 500; }
.theme-high-contrast .chapter-title { color: #ffffff; text-decoration: underline; }

/* === Header (Glassmorphism) === */
.read-header { 
  position: fixed; top: 0; left: 0; right: 0; height: 60px; 
  display: flex; justify-content: space-between; align-items: center; 
  padding: 0 24px; z-index: 100; 
  backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.03); 
  transition: all 0.3s ease;
}
.book-title { margin-left: 15px; font-weight: 700; font-family: 'Noto Serif SC', 'Source Han Serif SC', serif; letter-spacing: 0.5px; }

.read-content { padding-top: 100px; padding-bottom: 80px; max-width: 800px; margin: 0 auto; }
.chapter-title { 
  text-align: center; font-size: 26px; margin-bottom: 48px; 
  font-family: 'Noto Serif SC', 'Source Han Serif SC', serif; 
  font-weight: 700; letter-spacing: 1px;
}

/* === Paragraphs === */
.text-paragraph { 
  font-family: 'Noto Serif SC', 'Source Han Serif SC', "Georgia", serif; 
  margin-bottom: 24px; text-indent: 2em; text-align: justify; 
  cursor: pointer; padding: 8px 16px; border-radius: 8px; 
  transition: background-color 0.2s ease; position: relative; 
}
.text-paragraph:hover { background-color: rgba(0, 0, 0, 0.02); }
.theme-dark .text-paragraph:hover { background-color: rgba(255, 255, 255, 0.03); }
.text-paragraph.selected-paragraph { background-color: rgba(0, 102, 204, 0.06); }
.theme-dark .text-paragraph.selected-paragraph { background-color: rgba(255, 255, 255, 0.08); }

/* === Paragraph Tools === */
.paragraph-tools { 
  position: absolute; right: -60px; top: 50%; transform: translateY(-50%); 
  display: flex; flex-direction: column; gap: 8px; 
  background: rgba(255,255,255,0.9); padding: 8px; border-radius: 12px; 
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08); border: 1px solid rgba(0,0,0,0.05); 
  backdrop-filter: blur(8px);
}
.theme-dark .paragraph-tools { background: rgba(40,40,40,0.9); border-color: rgba(255,255,255,0.1); box-shadow: 0 4px 16px rgba(0,0,0,0.3); }
.tool-icon { color: #515154; cursor: pointer; font-size: 18px; transition: all 0.2s ease; }
.theme-dark .tool-icon { color: #b0b0b0; }
.tool-icon:hover { transform: scale(1.15); color: #0066cc; }

/* === Floating Action Buttons (FABs) === */
.sidebar-toggle { 
  position: fixed; width: 52px; height: 52px; 
  background: rgba(255, 255, 255, 0.85); backdrop-filter: blur(12px);
  border-radius: 16px; box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08); 
  border: 1px solid rgba(0,0,0,0.05); 
  display: flex; flex-direction: column; align-items: center; justify-content: center; 
  cursor: pointer; z-index: 900; color: #515154; 
  transition: all 0.3s cubic-bezier(0.25, 0.8, 0.25, 1); 
}
.theme-dark .sidebar-toggle { background: rgba(30, 30, 30, 0.85); border-color: rgba(255,255,255,0.08); color: #ccc; }
.theme-green .sidebar-toggle { background: rgba(220, 237, 200, 0.85); border-color: rgba(46,74,45,0.1); color: #2e4a2d; }
.theme-high-contrast .sidebar-toggle { background: #000; border: 2px solid #555; color: #fff; }
.sidebar-toggle:hover { 
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.12); color: #0066cc; 
  transform: translateY(-2px) scale(1.05); border-color: rgba(0,102,204,0.2);
}
.theme-dark .sidebar-toggle:hover { color: #409eff; }
.theme-green .sidebar-toggle:hover { color: #1e3a1d; border-color: rgba(46,74,45,0.3); }
.theme-high-contrast .sidebar-toggle:hover { border-color: #fff; color: #fff; }
.toggle-text { font-size: 10px; margin-top: 2px; font-weight: 600; opacity: 0.8; }

.ai-toggle { bottom: 100px; right: 40px; }
.add-to-shelf-toggle { bottom: 164px; right: 40px; }
.chapter-tts-toggle { bottom: 228px; right: 40px; }
.my-comments-toggle { bottom: 292px; right: 40px; }
.share-book-toggle { bottom: 356px; right: 40px; }
.add-to-shelf-toggle.is-added { color: #34c759; border-color: rgba(52, 199, 89, 0.3); }

/* === AI Highlight Menu === */
.ai-menu { 
  position: absolute; background: #fff; box-shadow: 0 8px 24px rgba(0, 0, 0, 0.15); 
  border-radius: 12px; padding: 8px 0; z-index: 1000; border: 1px solid rgba(0,0,0,0.05); 
  width: 180px; 
}
.theme-dark .ai-menu { background: #222; border-color: rgba(255,255,255,0.08); box-shadow: 0 8px 24px rgba(0,0,0,0.5); }
.theme-green .ai-menu { background: #eaf5df; border-color: rgba(46,74,45,0.1); }
.theme-high-contrast .ai-menu { background: #000; border: 2px solid #555; }
.menu-item { 
  padding: 12px 20px; cursor: pointer; transition: all 0.2s; 
  display: flex; align-items: center; gap: 12px; font-size: 14px; color: #444; 
}
.theme-dark .menu-item { color: #ccc; }
.menu-item:hover { background: #f0f7ff; color: #0066cc; }
.theme-dark .menu-item:hover { background: rgba(64,158,255,0.15); color: #409eff; }
.theme-green .menu-item { color: #2e4a2d; }
.theme-green .menu-item:hover { background: rgba(46,74,45,0.08); }
.theme-high-contrast .menu-item { color: #fff; }
.theme-high-contrast .menu-item:hover { background: #333; color: #fff; }

/* === Chapter Nav === */
.chapter-nav { 
  display: flex; justify-content: center; gap: 24px; margin-top: 60px; 
}

/* === Modals / Drawers base === */
.setting-group { margin-bottom: 24px; }
.setting-label { font-size: 14px; font-weight: bold; margin-bottom: 12px; display: flex; justify-content: space-between; }
.theme-options { display: flex; gap: 10px; flex-wrap: wrap; }
.theme-btn { 
  padding: 8px 16px; border-radius: 8px; border: 2px solid transparent; cursor: pointer; 
  font-weight: bold; transition: all 0.2s; 
}
.t-default { background: #fdfcf8; color: #2c2925; border-color: #ddd; }
.t-green { background: #eaf5df; color: #2e4a2d; border-color: #c5dcb3; }
.t-dark { background: #181a1b; color: #d0d0d0; border-color: #333; }
.t-high { background: #000; color: #fff; border-color: #fff; }
.theme-btn.active { border-color: #409eff; transform: scale(1.05); }

.book-info-content { display: flex; flex-direction: column; align-items: center; text-align: center; }
.book-cover-large { width: 120px; height: 160px; object-fit: cover; border-radius: 8px; margin-bottom: 15px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); }
.info-title { font-size: 18px; margin-bottom: 8px; font-weight: bold; }
.info-meta { color: #666; margin-bottom: 15px; font-size: 14px; }
.info-desc h4 { margin-bottom: 8px; font-size: 14px; }
.info-desc p { text-align: left; font-size: 13px; color: #555; line-height: 1.6; }

.empty-tip { text-align: center; color: #999; font-size: 16px; margin: 40px 0; }

.my-comment-list { padding: 10px; }
.my-comment-item { 
  background: rgba(0,0,0,0.02); padding: 12px; border-radius: 8px; margin-bottom: 15px; cursor: pointer; 
  transition: all 0.2s; border: 1px solid rgba(0,0,0,0.05);
}
.my-comment-item:hover { background: rgba(0,102,204,0.05); border-color: rgba(0,102,204,0.1); }
.my-comment-pos { font-size: 12px; color: #999; margin-bottom: 6px; }
.my-comment-quote { font-style: italic; color: #666; font-size: 13px; border-left: 3px solid #ccc; padding-left: 8px; margin-bottom: 8px; }
.my-comment-content { font-size: 14px; color: #333; line-height: 1.5; margin-bottom: 8px; }
.my-comment-time { font-size: 12px; color: #999; text-align: right; }
</style>
