<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick, reactive, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  ArrowLeft, Microphone, EditPen, ChatLineRound, DocumentCopy, Close,
  Delete, DocumentAdd, Notebook, UserFilled, Service, Position, Operation,
  InfoFilled, Collection, CollectionTag, ChatDotRound, Star, StarFilled, Comment, Loading,
  Setting, Timer, MoreFilled, Share // NEW: 引入 Share 图标
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import { fetchEventSource } from '@microsoft/fetch-event-source'

const route = useRoute()
const router = useRouter()
const bookId = route.params.id

// === 核心阅读状态 ===
const content = ref('正在加载书籍内容...')
const bookInfo = ref({ title: '阅读', author: '', coverUrl: '', description: '', category: '' })
const contentRef = ref(null)
const lines = ref([])        // 当前章节的段落
const currentLine = ref(0)   // 当前阅读进度(行)
const userInfo = ref({})
let observer = null          // 滚动监听器

// === 章节相关状态 ===
const catalog = ref([])          // 目录列表
const chapterIndex = ref(0)      // 当前是第几章 (索引)
const currentChapterTitle = ref('') // 当前章节标题
const showCatalog = ref(false)   // 是否显示目录侧边栏
const isLoading = ref(false)     // 加载中状态

// === 弹窗状态 ===
const showBookInfoDialog = ref(false)
const showBookShareDialog = ref(false)
const showParagraphShareDialog = ref(false)

const shareFriendOptions = ref([])
const isLoadingShareFriends = ref(false)
const bookShareFriendId = ref(null)
const bookShareMessage = ref('')
const isSubmittingBookShare = ref(false)
const paragraphShareFriendId = ref(null)
const paragraphShareMessage = ref('')
const paragraphShareIndex = ref(-1)
const isSubmittingParagraphShare = ref(false)

// === 收藏状态 ===
const isAddedToShelf = ref(false)
const isCheckingShelf = ref(false)

// === 段落评论 ===
const showParagraphCommentsDrawer = ref(false)
const selectedParagraphIndex = ref(-1) // 当前选中的段落索引
const paragraphComments = ref([]) // 段落评论列表
const newParagraphComment = ref('') // 新评论内容
const isLoadingComments = ref(false)
const isSubmittingComment = ref(false)

// === 我的评论 ===
const showMyCommentsDrawer = ref(false)
const myCommentsList = ref([])

// === AI & 笔记状态 ===
const currentAudio = ref(null)
const currentChapterAudio = ref(null) // NEW: 整章听书音频实例
const isGeneratingTts = ref(false)    // NEW: 听书生成中状态
const isChapterPlaying = ref(false)   // NEW: 听书播放中状态
const audioPlayerVisible = ref(false)
const audioShareDialogVisible = ref(false)
const isAudioLoading = ref(false)
const isAudioPlaying = ref(false)
const audioCurrentTime = ref(0)
const audioDuration = ref(0)
const audioShareFriendId = ref(null)
const audioShareMessage = ref('')
const isSubmittingAudioShare = ref(false)
const audioPlayback = reactive({
  audioUrl: '',
  title: '',
  sourceType: 'paragraph',
  bookId: null,
  chapterId: null,
  chapterIndex: null,
  paragraphIndex: null
})
const activeTab = ref('ai')
const noteList = ref([])
const menuVisible = ref(false)
const menuStyle = ref({ top: '0px', left: '0px' })
const selectedText = ref('')
const drawerVisible = ref(false)
const chatList = ref([])
const inputMessage = ref('')
const isThinking = ref(false)
const aiTitle = ref('AI 助手')
const currentConversationId = ref('');

// === 阅读设置 (护眼/适老) ===
const showSettings = ref(false)
const readingConfig = reactive({
  fontSize: 19,       // 字号
  lineHeight: 1.8,    // 行高
  theme: 'default',   // 主题: default, green, dark, high-contrast
  eyeCareMode: false, // 护眼提醒开关
  timerDuration: 45,  // 提醒间隔(分钟)
  voice: 'cherry'     // 听书音色
})

let eyeCareInterval = null
let readingTime = 0 // 已阅读秒数

// === 计算是否为青少年 ===
const isTeenager = computed(() => {
  const age = userInfo.value.age
  return age !== undefined && age !== null && age < 18
})

const selectedParagraphText = computed(() => {
  const index = paragraphShareIndex.value
  if (index < 0 || index >= lines.value.length) return ''
  return lines.value[index] || ''
})

const audioSourceLabel = computed(() => {
  if (audioPlayback.sourceType === 'chapter' && audioPlayback.chapterIndex !== null) {
    return `第 ${audioPlayback.chapterIndex + 1} 章`
  }
  if (audioPlayback.sourceType === 'paragraph' && audioPlayback.paragraphIndex !== null) {
    return `第 ${audioPlayback.paragraphIndex + 1} 段`
  }
  return '朗读音频'
})

const getRouteReadTarget = () => {
  const rawChapter = route.query.chapterIndex
  const rawParagraph = route.query.paragraphIndex
  const chapter = rawChapter === undefined ? null : Number(rawChapter)
  const paragraph = rawParagraph === undefined ? null : Number(rawParagraph)

  return {
    chapterIndex: Number.isInteger(chapter) ? chapter : null,
    paragraphIndex: Number.isInteger(paragraph) ? paragraph : null
  }
}

const applyRouteReadTarget = () => {
  const target = getRouteReadTarget()
  if (target.chapterIndex === null && target.paragraphIndex === null) return

  if (target.chapterIndex !== null && catalog.value.length > 0) {
    chapterIndex.value = Math.min(Math.max(target.chapterIndex, 0), catalog.value.length - 1)
  }
  if (target.paragraphIndex !== null) {
    const safeParagraph = Math.max(target.paragraphIndex, 0)
    currentLine.value = safeParagraph
    selectedParagraphIndex.value = safeParagraph
  }
}

// === 初始化 ===
onMounted(async () => {
  const userStr = localStorage.getItem('user')
  if (userStr) userInfo.value = JSON.parse(userStr)

  loadReadingSettings()

  await loadBookInfo()
  await loadCatalog()
  await loadProgress()
  applyRouteReadTarget()
  await loadCurrentChapter()
  initObserver()
  checkShelfStatus()

  startEyeCareTimer()

  document.addEventListener('mousedown', handleGlobalClick)
  fetchNotes()
  window.addEventListener('beforeunload', handleBeforeUnload)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', handleGlobalClick)
  stopAudioPlayback()
  if (observer) observer.disconnect()
  window.removeEventListener('beforeunload', handleBeforeUnload)
  saveProgress()
  if (eyeCareInterval) clearInterval(eyeCareInterval)
})

// === 阅读配置逻辑 ===
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

watch(readingConfig, (newVal) => {
  localStorage.setItem('readingConfig', JSON.stringify(newVal))
  if (!newVal.eyeCareMode) {
    readingTime = 0
  }
}, { deep: true })

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

// === 数据加载 ===
const loadBookInfo = async () => {
  try {
    const infoRes = await axios.get(`/api/sysBook/${bookId}`)
    if (infoRes.data.code === '200') {
      bookInfo.value = infoRes.data.data
    }
  } catch (err) {
    console.error(err)
    ElMessage.error('获取书籍信息失败')
  }
}

const goToBookDetail = () => {
  saveProgress()
  router.push(`/book/${bookId}`)
}

const loadCatalog = async () => {
  try {
    const res = await axios.get(`/api/sysBook/catalog/${bookId}`)
    if (res.data.code === '200') {
      catalog.value = res.data.data
      if (catalog.value.length === 0) {
        ElMessage.warning('该书暂无章节信息，正在尝试自动解析...')
        await axios.post(`/api/sysBook/analyze/${bookId}`)
        const retryRes = await axios.get(`/api/sysBook/catalog/${bookId}`)
        catalog.value = retryRes.data.data
      }
    }
  } catch (e) {
    console.error('加载目录失败', e)
  }
}

const loadProgress = async () => {
  if (!userInfo.value.id) return
  try {
    const res = await axios.get('/api/bookshelf/detail', {
      params: { userId: userInfo.value.id, bookId: bookId }
    })
    if (res.data.data) {
      chapterIndex.value = res.data.data.currentChapterIndex || 0
      currentLine.value = res.data.data.progressIndex || 0
    }
  } catch (e) { console.error(e) }
}

const loadCurrentChapter = async () => {
  if (catalog.value.length === 0) return

  isLoading.value = true
  showParagraphCommentsDrawer.value = false
  selectedParagraphIndex.value = -1

  stopAudioPlayback()

  const chapterId = catalog.value[chapterIndex.value].id
  currentChapterTitle.value = catalog.value[chapterIndex.value].title

  try {
    const res = await axios.get(`/api/sysBook/chapter/${chapterId}`)
    if (res.data.code === '200') {
      const text = res.data.data.content || ''
      lines.value = text.split(/\r?\n/).filter(line => line.trim() !== '')

      nextTick(() => {
        if (currentLine.value > 0) {
          scrollToLine(currentLine.value)
        } else {
          window.scrollTo(0, 0)
          document.documentElement.scrollTop = 0
          document.body.scrollTop = 0
        }

        const target = getRouteReadTarget()
        if (target.paragraphIndex !== null) {
          const safeParagraph = Math.max(target.paragraphIndex, 0)
          selectedParagraphIndex.value = safeParagraph
          scrollToLine(safeParagraph)
        }
        initObserver()
      })
    }
  } catch (e) {
    ElMessage.error('章节加载失败')
  } finally {
    isLoading.value = false
  }
}

const changeChapter = (offset) => {
  const newIndex = chapterIndex.value + offset
  if (newIndex >= 0 && newIndex < catalog.value.length) {
    saveProgress()
    chapterIndex.value = newIndex
    currentLine.value = 0
    loadCurrentChapter()
  }
}

const jumpToChapter = (index) => {
  saveProgress()
  chapterIndex.value = index
  currentLine.value = 0
  loadCurrentChapter()
  showCatalog.value = false
}

const scrollToCatalogActive = () => {
  nextTick(() => {
    const activeEl = document.getElementById(`catalog-item-${chapterIndex.value}`)
    if (activeEl) {
      activeEl.scrollIntoView({ behavior: 'auto', block: 'center' })
    }
  })
}

const saveProgress = async () => {
  if (!userInfo.value.id) return
  try {
    await axios.post('/api/bookshelf/updateProgress', {
      userId: userInfo.value.id,
      bookId: bookId,
      currentChapterIndex: chapterIndex.value,
      progressIndex: currentLine.value
    })
  } catch (e) { console.error(e) }
}

const scrollToLine = (index) => {
  const el = document.getElementById(`line-${index}`)
  if (el) {
    el.scrollIntoView({ behavior: 'auto', block: 'center' })
  }
}

const initObserver = () => {
  if (observer) observer.disconnect()
  const options = { root: null, rootMargin: '-40% 0px -60% 0px', threshold: 0 }
  observer = new IntersectionObserver((entries) => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        currentLine.value = parseInt(entry.target.dataset.index)
      }
    })
  }, options)

  const paragraphs = document.querySelectorAll('.text-paragraph')
  paragraphs.forEach(p => observer.observe(p))
}

// === 收藏功能 ===
const checkShelfStatus = async () => {
  if (!userInfo.value.id) return
  isCheckingShelf.value = true
  try {
    const res = await axios.get(`/api/bookshelf/list/${userInfo.value.id}`)
    if (res.data.code === '200') {
      isAddedToShelf.value = res.data.data.some(item => item.bookId === parseInt(bookId))
    }
  } catch (error) {
    console.error('检查书架状态失败', error)
  } finally {
    isCheckingShelf.value = false
  }
}

const toggleShelf = async () => {
  if (!userInfo.value.id) return ElMessage.warning('请先登录')

  if (isAddedToShelf.value) {
    ElMessageBox.confirm('确定要取消收藏吗？阅读进度将不再保存。', '提示', { type: 'warning' })
        .then(async () => {
          try {
            await axios.delete('/api/bookshelf/removeByBook', {
              params: { userId: userInfo.value.id, bookId: bookId }
            })
            ElMessage.success('已取消收藏')
            isAddedToShelf.value = false
          } catch(e) { ElMessage.error('操作失败') }
        }).catch(() => {})
  } else {
    try {
      const res = await axios.post('/api/bookshelf/add', {
        userId: userInfo.value.id, bookId: bookId
      })
      if (res.data.code === '200') {
        ElMessage.success('已加入书架')
        isAddedToShelf.value = true
      } else {
        ElMessage.error(res.data.msg || '加入失败')
      }
    } catch (e) { ElMessage.error('加入书架失败') }
  }
}

// === 段落交互 (评论与分享) ===
// 选中段落
const handleParagraphClick = (index, event) => {
  if (window.getSelection().toString().trim().length > 0) return
  selectedParagraphIndex.value = index
}

// 打开评论抽屉
const openCommentDrawer = (index) => {
  showParagraphCommentsDrawer.value = true
  fetchParagraphComments(index)
}

const ensureShareReady = () => {
  if (!userInfo.value.id) {
    ElMessage.warning('请先登录')
    return false
  }
  return true
}

const loadShareFriends = async () => {
  isLoadingShareFriends.value = true
  try {
    const res = await axios.get(`/api/friend/list/${userInfo.value.id}`)
    shareFriendOptions.value = res.data.data || []
    return true
  } catch (error) {
    shareFriendOptions.value = []
    ElMessage.error('获取好友列表失败，请稍后再试')
    return false
  } finally {
    isLoadingShareFriends.value = false
  }
}

const openBookShareDialog = async () => {
  if (!ensureShareReady()) return
  bookShareFriendId.value = null
  bookShareMessage.value = ''

  const loaded = await loadShareFriends()
  if (!loaded) return

  showBookShareDialog.value = true
}

const openParagraphShareDialog = async (index) => {
  if (!ensureShareReady()) return
  paragraphShareIndex.value = index
  paragraphShareFriendId.value = null
  paragraphShareMessage.value = ''

  const loaded = await loadShareFriends()
  if (!loaded) return

  showParagraphShareDialog.value = true
}

const submitBookShare = async () => {
  if (!bookShareFriendId.value) {
    ElMessage.warning('请选择要分享的好友')
    return
  }

  isSubmittingBookShare.value = true
  try {
    const res = await axios.post('/api/bookShare/send', {
      senderId: userInfo.value.id,
      receiverId: bookShareFriendId.value,
      bookId: Number(bookId),
      message: bookShareMessage.value.trim()
    })
    if (res.data.code === '200') {
      ElMessage.success('书籍已分享给好友')
      showBookShareDialog.value = false
    } else {
      ElMessage.warning(res.data.msg || '书籍分享失败')
    }
  } catch (error) {
    ElMessage.error('书籍分享失败')
  } finally {
    isSubmittingBookShare.value = false
  }
}

const submitParagraphShare = async () => {
  if (!paragraphShareFriendId.value) {
    ElMessage.warning('请选择要分享的好友')
    return
  }
  if (!selectedParagraphText.value) {
    ElMessage.warning('当前段落内容为空，暂时无法分享')
    return
  }

  isSubmittingParagraphShare.value = true
  try {
    const res = await axios.post('/api/paragraphShare/send', {
      senderId: userInfo.value.id,
      receiverId: paragraphShareFriendId.value,
      bookId: Number(bookId),
      chapterIndex: chapterIndex.value,
      paragraphIndex: paragraphShareIndex.value,
      quote: selectedParagraphText.value,
      message: paragraphShareMessage.value.trim()
    })
    if (res.data.code === '200') {
      ElMessage.success('段落已分享给好友')
      showParagraphShareDialog.value = false
    } else {
      ElMessage.warning(res.data.msg || '段落分享失败')
    }
  } catch (error) {
    ElMessage.error('段落分享失败')
  } finally {
    isSubmittingParagraphShare.value = false
  }
}

const fetchParagraphComments = async (paragraphIndex) => {
  isLoadingComments.value = true
  try {
    const res = await axios.get(`/api/paragraphComment/list/${bookId}/${chapterIndex.value}/${paragraphIndex}`, {
      params: { currentUserId: userInfo.value.id }
    })
    if (res.data.code === '200') {
      paragraphComments.value = res.data.data
    } else {
      paragraphComments.value = []
    }
  } catch (error) {
    paragraphComments.value = []
    ElMessage.error('获取评论失败')
  } finally {
    isLoadingComments.value = false
  }
}

const submitParagraphComment = async () => {
  if (!userInfo.value.id) return ElMessage.warning('请先登录')
  if (!newParagraphComment.value.trim()) return ElMessage.warning('评论内容不能为空')

  isSubmittingComment.value = true
  try {
    const res = await axios.post('/api/paragraphComment/add', {
      userId: userInfo.value.id,
      bookId: bookId,
      chapterIndex: chapterIndex.value,
      paragraphIndex: selectedParagraphIndex.value,
      content: newParagraphComment.value,
      quote: lines.value[selectedParagraphIndex.value].substring(0, 50) + '...'
    })

    if (res.data.code === '200') {
      ElMessage.success('评论成功')
      newParagraphComment.value = ''
      fetchParagraphComments(selectedParagraphIndex.value)
    } else {
      ElMessage.error(res.data.msg || '评论失败')
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('评论提交失败')
  } finally {
    isSubmittingComment.value = false
  }
}

const deleteComment = (commentId) => {
  ElMessageBox.confirm('确定删除这条评论吗？', '提示', { type: 'warning' })
      .then(async () => {
        await axios.delete(`/api/paragraphComment/${commentId}`, {
          params: { userId: userInfo.value.id }
        })
        ElMessage.success('已删除')
        fetchParagraphComments(selectedParagraphIndex.value)
        if (showMyCommentsDrawer.value) fetchMyComments()
      }).catch(() => {})
}

const toggleLike = async (comment) => {
  if (!userInfo.value.id) return ElMessage.warning('请先登录')
  try {
    const res = await axios.post('/api/paragraphComment/like', {
      commentId: comment.id,
      userId: userInfo.value.id
    })
    if (res.data.code === '200') {
      comment.likeCount = res.data.data.likeCount
      comment.isLiked = res.data.data.isLiked
    }
  } catch (e) { ElMessage.error('操作失败') }
}

// === 我的评论 ===
const openMyComments = async () => {
  showMyCommentsDrawer.value = true
  fetchMyComments()
}

const fetchMyComments = async () => {
  if (!userInfo.value.id) return
  const res = await axios.get(`/api/paragraphComment/my/${bookId}/${userInfo.value.id}`)
  if (res.data.code === '200') myCommentsList.value = res.data.data
}

const jumpToMyComment = (comment) => {
  if (comment.chapterIndex !== chapterIndex.value) {
    chapterIndex.value = comment.chapterIndex
    isLoading.value = true
    const chapterId = catalog.value[chapterIndex.value].id
    currentChapterTitle.value = catalog.value[chapterIndex.value].title

    axios.get(`/api/sysBook/chapter/${chapterId}`).then(res => {
      isLoading.value = false
      const text = res.data.data.content || ''
      lines.value = text.split(/\r?\n/).filter(line => line.trim() !== '')

      nextTick(() => {
        scrollToLine(comment.paragraphIndex)
        selectedParagraphIndex.value = comment.paragraphIndex
        // 自动高亮并显示工具栏
        handleParagraphClick(comment.paragraphIndex)
      })
    })
  } else {
    scrollToLine(comment.paragraphIndex)
    selectedParagraphIndex.value = comment.paragraphIndex
    handleParagraphClick(comment.paragraphIndex)
  }
  showMyCommentsDrawer.value = false
}

const resetAudioPlaybackMeta = () => {
  audioPlayback.audioUrl = ''
  audioPlayback.title = ''
  audioPlayback.sourceType = 'paragraph'
  audioPlayback.bookId = null
  audioPlayback.chapterId = null
  audioPlayback.chapterIndex = null
  audioPlayback.paragraphIndex = null
  audioCurrentTime.value = 0
  audioDuration.value = 0
}

const stopAudioPlayback = (resetMeta = true) => {
  if (currentAudio.value) {
    currentAudio.value.pause()
    currentAudio.value.removeAttribute('src')
    currentAudio.value.load()
  }
  currentChapterAudio.value = null
  isAudioPlaying.value = false
  isAudioLoading.value = false
  isGeneratingTts.value = false
  isChapterPlaying.value = false
  if (resetMeta) resetAudioPlaybackMeta()
}

const handleAudioLoadedMetadata = () => {
  if (!currentAudio.value) return
  audioDuration.value = Number.isFinite(currentAudio.value.duration) ? currentAudio.value.duration : 0
}

const handleAudioTimeUpdate = () => {
  if (!currentAudio.value) return
  audioCurrentTime.value = currentAudio.value.currentTime || 0
}

const handleAudioPlay = () => {
  isAudioPlaying.value = true
  isAudioLoading.value = false
  isGeneratingTts.value = false
  isChapterPlaying.value = audioPlayback.sourceType === 'chapter'
}

const handleAudioPause = () => {
  isAudioPlaying.value = false
  isChapterPlaying.value = false
}

const handleAudioEnded = () => {
  isAudioPlaying.value = false
  isChapterPlaying.value = false
}

const openAudioPlayer = async (payload) => {
  if (!payload.text?.trim()) {
    ElMessage.warning('当前没有可朗读的内容')
    return
  }

  stopAudioPlayback()
  audioPlayerVisible.value = true
  isAudioLoading.value = true
  if (payload.sourceType === 'chapter') {
    isGeneratingTts.value = true
  }

  try {
    const res = await axios.post('/api/ai/audio/generate', payload)
    if (res.data.code !== '200' || !res.data.data?.audioUrl) {
      ElMessage.error(res.data.msg || '生成音频失败')
      audioPlayerVisible.value = false
      stopAudioPlayback()
      return
    }

    Object.assign(audioPlayback, res.data.data)
    await nextTick()
    if (currentAudio.value) {
      currentAudio.value.currentTime = 0
      currentAudio.value.load()
      await currentAudio.value.play()
    }
  } catch (error) {
    ElMessage.error('生成音频失败')
    audioPlayerVisible.value = false
    stopAudioPlayback()
  } finally {
    isAudioLoading.value = false
    isGeneratingTts.value = false
  }
}

const closeAudioPlayer = () => {
  audioPlayerVisible.value = false
  stopAudioPlayback()
}

const toggleDialogAudioPlayback = async () => {
  if (!currentAudio.value || !audioPlayback.audioUrl) return
  if (currentAudio.value.paused) {
    await currentAudio.value.play()
  } else {
    currentAudio.value.pause()
  }
}

const formatAudioTime = (seconds) => {
  if (!Number.isFinite(seconds) || seconds < 0) return '00:00'
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
}

const downloadCurrentAudio = () => {
  if (!audioPlayback.audioUrl) return
  const link = document.createElement('a')
  link.href = audioPlayback.audioUrl
  link.download = `${(audioPlayback.title || '朗读音频').replace(/[\\/:*?"<>|]/g, '_')}.wav`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

const openAudioShareDialog = async () => {
  if (!audioPlayback.audioUrl) {
    ElMessage.warning('请先生成音频')
    return
  }
  const loaded = await loadShareFriends()
  if (!loaded) return
  audioShareFriendId.value = null
  audioShareMessage.value = ''
  audioShareDialogVisible.value = true
}

const submitAudioShare = async () => {
  if (!audioShareFriendId.value) {
    ElMessage.warning('请选择好友')
    return
  }
  if (!audioPlayback.audioUrl) {
    ElMessage.warning('当前没有可分享的音频')
    return
  }

  isSubmittingAudioShare.value = true
  try {
    const res = await axios.post('/api/audioShare/send', {
      senderId: userInfo.value.id,
      receiverId: audioShareFriendId.value,
      audioUrl: audioPlayback.audioUrl,
      title: audioPlayback.title,
      sourceType: audioPlayback.sourceType,
      bookId: audioPlayback.bookId,
      chapterIndex: audioPlayback.chapterIndex,
      paragraphIndex: audioPlayback.paragraphIndex,
      message: audioShareMessage.value.trim()
    })
    if (res.data.code === '200') {
      ElMessage.success('音频已分享给好友')
      audioShareDialogVisible.value = false
    } else {
      ElMessage.warning(res.data.msg || '分享失败')
    }
  } catch (error) {
    ElMessage.error('分享失败')
  } finally {
    isSubmittingAudioShare.value = false
  }
}

// === 交互逻辑 ===
const handleMouseUp = (e) => {
  const selection = window.getSelection()
  const text = selection.toString().trim()
  if (!text || text.length < 2) {
    menuVisible.value = false
    return
  }
  selectedText.value = text
  let left = e.pageX + 10
  let top = e.pageY + 10
  if (left + 200 > window.innerWidth) left = e.pageX - 210
  menuStyle.value = { left: `${left}px`, top: `${top}px` }
  menuVisible.value = true
}

const toggleSidebar = () => {
  drawerVisible.value = !drawerVisible.value
  if (drawerVisible.value) setTimeout(() => scrollToBottom(), 100)
}

const handleGlobalClick = (e) => {
  const menu = document.querySelector('.ai-menu')
  if (menuVisible.value && menu && !menu.contains(e.target)) {
    menuVisible.value = false
    window.getSelection().removeAllRanges()
  }
  if (drawerVisible.value) {
    const drawer = document.querySelector('.ai-drawer')
    const toggleBtn = document.querySelector('.sidebar-toggle.ai-toggle')
    if ((drawer && !drawer.contains(e.target)) && (toggleBtn && !toggleBtn.contains(e.target))) {
      drawerVisible.value = false
    }
  }
}

// === 核弹级改造：接入 Dify 流式接口 ===
const sendChat = async (contextText = null, modeOverride = null, displayMsg = null) => {
  // 如果是用户自己打字，contextText 传 null，指令就是用户的打字内容
  const instruction = modeOverride || inputMessage.value
  // 如果没有选中文本，传一个默认提示防止后端报错
  const textToAnalyze = contextText || selectedText.value || '用户没有提供具体文本，请直接回答用户的问题'
  const messageToShow = displayMsg || inputMessage.value

  if (!instruction || !instruction.trim() || isThinking.value) return

  let userId = userInfo.value.id
  if (!userId) { ElMessage.warning('请先登录'); return }

  // 1. 将用户的提问加入聊天框
  chatList.value.push({ role: 'user', content: messageToShow })
  inputMessage.value = ''
  isThinking.value = true
  scrollToBottom()

  // 2. 先创建一个空的 AI 气泡，拿到它的索引，等会儿往里塞字
  const aiMsgIndex = chatList.value.push({ role: 'ai', content: '' }) - 1

  try {
    await fetchEventSource('/api/difyreading/analyze', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream'
      },
      // 2. 👇 在发送的 body 里，加上 conversationId
      body: JSON.stringify({
        text: textToAnalyze,
        mode: instruction,
        conversationId: currentConversationId.value, // 首次为空，后续有值
        bookName: bookInfo.value.title // 🌟 把你 Vue 里的书籍标题传过去
      }),
      onmessage(event) {
        const dataJson = JSON.parse(event.data);

        // Dify 的 Chat 模式事件和 Workflow 稍微有一点不同
        // 主要是通过 message 类型的事件来推送文本
        if (dataJson.event === 'message') {
          const newText = dataJson.answer || ''; // 聊天模式通常把字放在 answer 里
          chatList.value[aiMsgIndex].content += newText;
          scrollToBottom();

          // 3. 👇 核心：从 Dify 吐出的数据中抓取 conversation_id 并存起来！
          if (dataJson.conversation_id) {
            currentConversationId.value = dataJson.conversation_id;
          }
        }


        if (dataJson.event === 'error') {
          chatList.value[aiMsgIndex].content += '\n[解析出错]';
        }
      },
      onclose() {
        isThinking.value = false;
        scrollToBottom();
      },
      onerror(err) {
        console.error("流式输出异常:", err);
        chatList.value[aiMsgIndex].content += '\n[网络异常，连接中断]';
        isThinking.value = false;
        throw err; // 阻止疯狂重连
      }
    });
  } catch (error) {
    console.error(error)
    isThinking.value = false
    scrollToBottom()
  }
}

const scrollToBottom = () => {
  nextTick(() => {
    const box = document.querySelector('.chat-history-box')
    if (box) box.scrollTop = box.scrollHeight
  })
}

const saveNote = async (msgContent) => {
  if (!userInfo.value.id) return ElMessage.warning('请先登录')
  const quote = selectedText.value || (msgContent.substring(0, 15) + '...')
  try {
    await axios.post('/api/sysNote/add', {
      userId: userInfo.value.id, bookId, selectedText: quote, content: msgContent
    })
    ElMessage.success('已保存笔记')
    fetchNotes()
  } catch (e) { ElMessage.error('保存失败') }
}

const fetchNotes = async () => {
  if (!userInfo.value.id) return
  try {
    const res = await axios.get(`/api/sysNote/list/${bookId}`, { params: { userId: userInfo.value.id } })
    if (res.data.code === '200') noteList.value = res.data.data
  } catch (e) { console.error(e) }
}

const handleDeleteNote = async (id) => {
  await axios.delete(`/api/sysNote/${id}`)
  fetchNotes()
}

const handleAiAction = (type) => {
  menuVisible.value = false
  toggleSidebar() // 打开右侧 AI 抽屉

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
    handleTTS()
    return
  }

  // 调用发送逻辑：传入原文、指令、以及要在聊天框显示的文字
  sendChat(selectedText.value, modeInstruction, displayMessage)
}

const handleTTS = async () => {
  menuVisible.value = false
  await openAudioPlayer({
    text: selectedText.value,
    voice: readingConfig.voice || 'cherry',
    bookId: Number(bookId),
    chapterId: catalog.value[chapterIndex.value]?.id,
    chapterIndex: chapterIndex.value,
    paragraphIndex: selectedParagraphIndex.value >= 0 ? selectedParagraphIndex.value : null,
    title: `《${bookInfo.value.title}》片段朗读`,
    sourceType: 'paragraph'
  })
}

const handleBeforeUnload = () => { saveProgress() }
const goBack = async () => {
  stopAudioPlayback()
  await saveProgress()
  router.push('/shelf')
}

const toggleChapterTts = async () => {
  const chapter = catalog.value[chapterIndex.value]
  const fullText = lines.value.join('，')
  if (!chapter || !fullText) {
    ElMessage.warning('当前章节无内容，无法朗读')
    return
  }

  if (
    audioPlayback.audioUrl &&
    audioPlayback.sourceType === 'chapter' &&
    audioPlayback.chapterId === chapter.id
  ) {
    audioPlayerVisible.value = true
    return
  }

  await openAudioPlayer({
    text: fullText,
    voice: readingConfig.voice || 'cherry',
    bookId: Number(bookId),
    chapterId: chapter.id,
    chapterIndex: chapterIndex.value,
    paragraphIndex: null,
    title: `《${bookInfo.value.title}》第 ${chapterIndex.value + 1} 章`,
    sourceType: 'chapter'
  })
}

const goToUserProfile = (userId) => {
  if (userId) router.push(`/user/${userId}`)
}

</script>

<template>
  <div class="read-container" :class="`theme-${readingConfig.theme}`" @mouseup="handleMouseUp" v-loading="isLoading">

    <header class="read-header">
      <div class="left">
        <el-button link @click="goBack">
          <el-icon :size="20"><ArrowLeft /></el-icon> 返回书架
        </el-button>
        <span class="book-title">{{ bookInfo.title }}</span>
      </div>
      <div class="right">
        <el-button link @click="showSettings = true">
          <el-icon :size="20"><Setting /></el-icon> 设置
        </el-button>
        <el-button link @click="showBookInfoDialog = true">
          <el-icon :size="20"><InfoFilled /></el-icon> 信息
        </el-button>
        <el-button link @click="showCatalog = true">
          <el-icon :size="20"><Operation /></el-icon> 目录
        </el-button>
      </div>
    </header>

    <main class="read-content" ref="contentRef">
      <h2 class="chapter-title" v-if="currentChapterTitle">{{ currentChapterTitle }}</h2>

      <div
          v-for="(line, index) in lines"
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
            <el-icon class="tool-icon" @click.stop="openParagraphShareDialog(index)"><Share /></el-icon>
          </el-tooltip>
        </div>
      </div>

      <div v-if="lines.length === 0 && !isLoading" class="empty-tip">
        暂无内容
      </div>

      <div class="chapter-nav" v-if="catalog.length > 0">
        <el-button :disabled="chapterIndex === 0" @click="changeChapter(-1)">上一章</el-button>
        <el-button :disabled="chapterIndex >= catalog.length - 1" @click="changeChapter(1)">下一章</el-button>
      </div>
    </main>

    <div class="sidebar-toggle ai-toggle" @click.stop="toggleSidebar">
      <el-icon size="24"><Notebook /></el-icon>
      <span class="toggle-text">助手</span>
    </div>

    <!-- NEW: 听本章 -->
    <div class="sidebar-toggle chapter-tts-toggle" @click.stop="toggleChapterTts">
      <el-icon size="24" v-if="isAudioLoading && audioPlayback.sourceType === 'chapter'" class="is-loading"><Loading /></el-icon>
      <el-icon size="24" v-else-if="isChapterPlaying"><Microphone style="color: #67C23A;" /></el-icon>
      <el-icon size="24" v-else><Microphone /></el-icon>
      <span class="toggle-text">{{ (isAudioLoading && audioPlayback.sourceType === 'chapter') ? '生成中' : (isChapterPlaying ? '暂停' : '听本章') }}</span>
    </div>

    <div class="sidebar-toggle add-to-shelf-toggle" @click.stop="toggleShelf" :class="{ 'is-added': isAddedToShelf }">
      <el-icon size="24" v-if="isCheckingShelf"><Loading /></el-icon>
      <el-icon size="24" v-else-if="isAddedToShelf"><Collection /></el-icon>
      <el-icon size="24" v-else><Collection /></el-icon>
      <span class="toggle-text">{{ isAddedToShelf ? '已收藏' : '收藏' }}</span>
    </div>

    <div class="sidebar-toggle my-comments-toggle" @click.stop="openMyComments">
      <el-icon size="24"><Comment /></el-icon>
      <span class="toggle-text">足迹</span>
    </div>

    <div class="sidebar-toggle share-book-toggle" @click.stop="openBookShareDialog">
      <el-icon size="24"><Share /></el-icon>
      <span class="toggle-text">分享</span>
    </div>

    <el-drawer v-model="showSettings" title="阅读设置" direction="rtl" size="320px">
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

    <el-dialog v-model="showBookInfoDialog" title="书籍信息" width="400px" center class="book-info-dialog">
      <div class="book-info-content">
        <img :src="bookInfo.coverUrl || 'https://via.placeholder.com/150'" class="book-cover-large" />
        <h3 class="info-title">{{ bookInfo.title }}</h3>
        <p class="info-meta"><el-icon><UserFilled /></el-icon> 作者：{{ bookInfo.author }}</p>
        <div class="info-desc"><h4>简介：</h4><p>{{ bookInfo.description || '暂无简介' }}</p></div>
        <div style="margin-top: 20px; width: 100%;"><el-button type="primary" round style="width: 100%" @click="goToBookDetail"><el-icon style="margin-right: 5px"><MoreFilled /></el-icon> 查看书籍详情 & 评分</el-button></div>
      </div>
    </el-dialog>

    <el-dialog v-model="showBookShareDialog" title="分享书籍给好友" width="430px">
      <div v-loading="isLoadingShareFriends" class="share-dialog-body">
        <el-empty v-if="!isLoadingShareFriends && shareFriendOptions.length === 0" description="你还没有好友，先去好友中心添加吧" :image-size="72" />
        <template v-else>
          <div class="share-preview-card">
            <img :src="bookInfo.coverUrl || 'https://via.placeholder.com/150'" class="share-preview-cover" />
            <div class="share-preview-main">
              <div class="share-preview-title">{{ bookInfo.title }}</div>
              <div class="share-preview-meta">{{ bookInfo.author || '未知作者' }}</div>
            </div>
          </div>
          <div class="share-dialog-form">
            <el-select v-model="bookShareFriendId" placeholder="请选择好友" filterable clearable style="width: 100%">
              <el-option
                  v-for="friend in shareFriendOptions"
                  :key="friend.friendUserId"
                  :label="friend.nickname || friend.username"
                  :value="friend.friendUserId"
              />
            </el-select>
            <el-input
                v-model="bookShareMessage"
                type="textarea"
                :rows="3"
                maxlength="200"
                show-word-limit
                placeholder="给好友捎一句话（可选）"
            />
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="showBookShareDialog = false">取消</el-button>
        <el-button type="primary" :loading="isSubmittingBookShare" :disabled="shareFriendOptions.length === 0" @click="submitBookShare">确认分享</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showParagraphShareDialog" title="分享段落给好友" width="460px">
      <div v-loading="isLoadingShareFriends" class="share-dialog-body">
        <el-empty v-if="!isLoadingShareFriends && shareFriendOptions.length === 0" description="你还没有好友，先去好友中心添加吧" :image-size="72" />
        <template v-else>
          <div class="share-preview-card is-paragraph">
            <div class="share-preview-main">
              <div class="share-preview-title">{{ bookInfo.title }}</div>
              <div class="share-position-meta">第 {{ chapterIndex + 1 }} 章 · 第 {{ paragraphShareIndex + 1 }} 段</div>
              <div class="share-preview-quote">“{{ selectedParagraphText }}”</div>
            </div>
          </div>
          <div class="share-dialog-form">
            <el-select v-model="paragraphShareFriendId" placeholder="请选择好友" filterable clearable style="width: 100%">
              <el-option
                  v-for="friend in shareFriendOptions"
                  :key="friend.friendUserId"
                  :label="friend.nickname || friend.username"
                  :value="friend.friendUserId"
              />
            </el-select>
            <el-input
                v-model="paragraphShareMessage"
                type="textarea"
                :rows="3"
                maxlength="200"
                show-word-limit
                placeholder="可以补充你为什么想分享这段（可选）"
            />
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="showParagraphShareDialog = false">取消</el-button>
        <el-button type="primary" :loading="isSubmittingParagraphShare" :disabled="shareFriendOptions.length === 0" @click="submitParagraphShare">确认分享</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="audioPlayerVisible" title="听书播放器" width="520px" @closed="closeAudioPlayer">
      <div v-loading="isAudioLoading" class="audio-player-panel">
        <div class="audio-player-heading">{{ audioPlayback.title || '朗读音频' }}</div>
        <div class="audio-player-subheading">{{ audioSourceLabel }}</div>
        <audio
            ref="currentAudio"
            class="audio-player-element"
            :src="audioPlayback.audioUrl || undefined"
            controls
            preload="metadata"
            @loadedmetadata="handleAudioLoadedMetadata"
            @timeupdate="handleAudioTimeUpdate"
            @play="handleAudioPlay"
            @pause="handleAudioPause"
            @ended="handleAudioEnded"
        />
        <div class="audio-player-time">
          <span>{{ formatAudioTime(audioCurrentTime) }}</span>
          <span>{{ formatAudioTime(audioDuration) }}</span>
        </div>
        <div class="audio-player-actions">
          <el-button :disabled="!audioPlayback.audioUrl" @click="toggleDialogAudioPlayback">
            {{ isAudioPlaying ? '暂停播放' : '继续播放' }}
          </el-button>
          <el-button :disabled="!audioPlayback.audioUrl" @click="downloadCurrentAudio">保存本地</el-button>
          <el-button type="primary" :disabled="!audioPlayback.audioUrl" @click="openAudioShareDialog">分享给好友</el-button>
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="audioShareDialogVisible" title="分享音频给好友" width="430px">
      <div class="share-dialog-body">
        <div class="share-preview-card is-paragraph">
          <div class="share-preview-main">
            <div class="share-preview-title">{{ audioPlayback.title || '朗读音频' }}</div>
            <div class="share-position-meta">{{ audioSourceLabel }}</div>
          </div>
        </div>
        <div class="share-dialog-form">
          <el-select v-model="audioShareFriendId" placeholder="请选择好友" filterable clearable style="width: 100%">
            <el-option
                v-for="friend in shareFriendOptions"
                :key="friend.friendUserId"
                :label="friend.nickname || friend.username"
                :value="friend.friendUserId"
            />
          </el-select>
          <el-input
              v-model="audioShareMessage"
              type="textarea"
              :rows="3"
              maxlength="200"
              show-word-limit
              placeholder="给好友捎一句话（可选）"
          />
        </div>
      </div>
      <template #footer>
        <el-button @click="audioShareDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="isSubmittingAudioShare" :disabled="shareFriendOptions.length === 0" @click="submitAudioShare">确认分享</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="showCatalog" title="目录" direction="rtl" size="300px" @open="scrollToCatalogActive">
      <div class="catalog-list">
        <div v-for="(chapter, index) in catalog" :key="chapter.id" :id="'catalog-item-' + index" class="catalog-item" :class="{ active: index === chapterIndex }" @click="jumpToChapter(index)">{{ chapter.title }}</div>
      </div>
    </el-drawer>

    <el-drawer v-model="showParagraphCommentsDrawer" title="段落评论" direction="rtl" size="400px" class="comment-drawer">
      <div class="paragraph-quote" v-if="selectedParagraphIndex >= 0">“{{ lines[selectedParagraphIndex] }}”</div>
      <div class="comment-list" v-loading="isLoadingComments">
        <el-empty v-if="paragraphComments.length === 0" description="暂无评论" />
        <div v-for="comment in paragraphComments" :key="comment.id" class="comment-item">
          <el-avatar :size="32" :src="comment.avatar || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" class="clickable-user" @click="goToUserProfile(comment.userId)"></el-avatar>
          <div class="comment-body">
            <div class="comment-header"><span class="comment-user clickable-user" @click="goToUserProfile(comment.userId)">{{ comment.nickname }}</span><div class="comment-ops"><el-icon v-if="userInfo.id === comment.userId || userInfo.role === 1" class="op-icon delete-icon" @click="deleteComment(comment.id)"><Delete /></el-icon><div class="like-box" @click="toggleLike(comment)"><el-icon :class="{ 'is-liked': comment.isLiked }"><StarFilled v-if="comment.isLiked"/><Star v-else/></el-icon><span class="like-count">{{ comment.likeCount || 0 }}</span></div></div></div>
            <div class="comment-content">{{ comment.content }}</div>
            <span class="comment-time">{{ comment.createTime?.replace('T', ' ') }}</span>
          </div>
        </div>
      </div>
      <div class="comment-input-area">
        <el-input v-model="newParagraphComment" type="textarea" :rows="3" placeholder="写下你的想法..." />
        <div style="text-align: right; margin-top: 10px;">
          <el-button type="primary" size="small" @click="submitParagraphComment" :loading="isSubmittingComment">发表评论</el-button>
        </div>
      </div>
    </el-drawer>

    <el-drawer v-model="showMyCommentsDrawer" title="我的评论足迹" direction="ltr" size="350px">
      <div class="my-comment-list">
        <el-empty v-if="myCommentsList.length === 0" description="暂无评论" />
        <div v-for="c in myCommentsList" :key="c.id" class="my-comment-item" @click="jumpToMyComment(c)">
          <div class="my-comment-pos">第 {{ c.chapterIndex + 1 }} 章 · 第 {{ c.paragraphIndex + 1 }} 段</div>
          <div class="my-comment-quote">{{ c.quote }}</div>
          <div class="my-comment-content">{{ c.content }}</div>
          <div class="my-comment-time">{{ c.createTime?.replace('T', ' ') }}</div>
        </div>
      </div>
    </el-drawer>

    <div v-if="menuVisible" class="ai-menu" :style="menuStyle" @mousedown.stop>
      <div class="menu-item" @mousedown.prevent="handleAiAction('TTS')"><el-icon><Microphone /></el-icon> 朗读</div>
      <div class="menu-item" @mousedown.prevent="handleAiAction('EXPLAIN')"><el-icon><ChatLineRound /></el-icon> 释意</div>
      <div class="menu-item" @mousedown.prevent="handleAiAction('CONTINUE')"><el-icon><EditPen /></el-icon> 续写</div>
      <div class="menu-item" @mousedown.prevent="handleAiAction('SUMMARY')"><el-icon><DocumentCopy /></el-icon> 提炼</div>
    </div>

    <el-drawer v-model="drawerVisible" :title="aiTitle" direction="rtl" size="400px" :modal="false" class="ai-drawer">
      <el-tabs v-model="activeTab" stretch>
        <el-tab-pane label="AI 助手" name="ai">
          <div class="chat-layout">
            <div class="chat-history-box">
              <div v-if="chatList.length === 0" class="empty-state">
                <el-icon :size="40" color="#ddd"><Service /></el-icon>
                <p>你好，我是你的智能书童。</p>
              </div>
              <div v-for="(msg, index) in chatList" :key="index" class="chat-row" :class="msg.role === 'user' ? 'row-right' : 'row-left'">
                <div class="avatar-wrapper"><el-avatar :size="32" :icon="msg.role === 'user' ? UserFilled : Service" :style="{ background: msg.role === 'user' ? '#409eff' : '#36cfc9' }" /></div>
                <div class="bubble-wrapper"><div class="bubble-content">{{ msg.content }}</div><div class="msg-actions" v-if="msg.role === 'ai' && msg.content"><el-icon class="action-icon" @click="saveNote(msg.content)"><DocumentAdd /></el-icon></div></div>
              </div>
            </div>
            <div class="chat-input-area"><el-input v-model="inputMessage" placeholder="输入你的想法..." :disabled="isThinking" @keyup.enter="sendChat(null)"><template #append><el-button @click="sendChat(null)" :loading="isThinking"><el-icon><Position /></el-icon></el-button></template></el-input></div>
          </div>
        </el-tab-pane>
        <el-tab-pane label="我的笔记" name="note">
          <div class="note-card" v-for="note in noteList" :key="note.id">
            <div class="note-header"><span class="note-time">{{ note.createTime?.replace('T', ' ') }}</span><el-button link type="danger" size="small" @click="handleDeleteNote(note.id)"><el-icon><Delete /></el-icon></el-button></div>
            <div class="note-quote">“{{ note.selectedText.substring(0, 30) }}...”</div>
            <div class="note-content">{{ note.content }}</div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

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
}

/* === Themes === */
/* Default: Elegant paper-like reading experience */
.theme-default { background-color: #fdfcf8; color: #2c2925; }
.theme-default .read-header { background: rgba(253, 252, 248, 0.85); border-bottom: 1px solid rgba(0,0,0,0.04); color: #2c2925; }
.theme-default .text-paragraph { color: #2c2925; }

/* Eye-care (Green): Soothing pastel mint */
.theme-green { background-color: #dcedc8; color: #2e4a2d; }
.theme-green .read-header { background: rgba(220, 237, 200, 0.85); border-bottom: 1px solid rgba(46,74,45,0.1); color: #2e4a2d; }
.theme-green .text-paragraph { color: #2e4a2d; }
.theme-green .text-paragraph:hover { background-color: rgba(46, 74, 45, 0.04); }

/* Dark: Deep slate for OLED / night reading */
.theme-dark { background-color: #181a1b; color: #d0d0d0; }
.theme-dark .read-header { background: rgba(24, 26, 27, 0.85); border-bottom: 1px solid rgba(255,255,255,0.05); color: #e8e8e8; }
.theme-dark .text-paragraph { color: #d0d0d0; }
.theme-dark .chapter-title { color: #e8e8e8; }
.theme-dark .text-paragraph:hover { background-color: rgba(255, 255, 255, 0.03); }
.theme-dark .book-title { color: #e8e8e8; }

/* High-contrast: Maximum readability */
.theme-high-contrast { background-color: #000000; color: #ffffff; }
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
.sidebar-toggle:hover { 
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.12); color: #0066cc; 
  transform: translateY(-2px) scale(1.05); border-color: rgba(0,102,204,0.2);
}
.theme-dark .sidebar-toggle:hover { color: #409eff; }
.toggle-text { font-size: 10px; margin-top: 2px; font-weight: 600; opacity: 0.8; }

.ai-toggle { bottom: 100px; right: 40px; }
.add-to-shelf-toggle { bottom: 164px; right: 40px; }
.chapter-tts-toggle { bottom: 228px; right: 40px; }
.add-to-shelf-toggle.is-added { color: #34c759; border-color: rgba(52, 199, 89, 0.3); }
.my-comments-toggle { bottom: 100px; left: 40px; }
.share-book-toggle { bottom: 164px; left: 40px; }

/* === Settings Panel === */
.setting-group { margin-bottom: 24px; border-bottom: 1px solid rgba(0,0,0,0.05); padding-bottom: 20px; }
.setting-group:last-child { border-bottom: none; }
.setting-label { font-weight: 600; margin-bottom: 14px; color: #1d1d1f; display: flex; justify-content: space-between; align-items: center; }
.desc { font-size: 12px; color: #86868b; margin-top: 6px; line-height: 1.4; }
.theme-options { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
.theme-btn { text-align: center; padding: 12px; border-radius: 8px; cursor: pointer; border: 2px solid transparent; font-size: 14px; font-weight: 500; transition: all 0.2s; }
.theme-btn.active { border-color: #0066cc; position: relative; box-shadow: 0 2px 8px rgba(0,102,204,0.15); }
.theme-btn.active::after { content: '✔'; position: absolute; top: 4px; right: 8px; color: #0066cc; font-size: 12px; }
.t-default { background: #fdfcf8; color: #2c2925; border: 1px solid rgba(0,0,0,0.05); }
.t-green { background: #dcedc8; color: #2e4a2d; border: 1px solid rgba(0,0,0,0.05); }
.t-dark { background: #181a1b; color: #d0d0d0; border: 1px solid rgba(255,255,255,0.1); }
.t-high { background: #000; color: #fff; font-weight: bold; border: 1px solid #555; }

/* === Catalog === */
.catalog-list { padding: 8px 12px; }
.catalog-item { padding: 14px 16px; border-bottom: 1px solid rgba(0,0,0,0.03); cursor: pointer; font-size: 15px; transition: all 0.2s ease; border-radius: 8px; }
.catalog-item:hover { background: rgba(0,0,0,0.02); color: #0066cc; transform: translateX(4px); }
.catalog-item.active { color: #0066cc; font-weight: 600; background: rgba(0,102,204,0.06); }

/* === Book Info Dialog === */
.book-info-content { display: flex; flex-direction: column; align-items: center; padding: 20px; }
.book-cover-large { width: 140px; height: 190px; object-fit: cover; border-radius: 8px; margin-bottom: 24px; box-shadow: 0 8px 24px rgba(0,0,0,0.15); }
.info-title { font-size: 24px; margin-bottom: 16px; font-family: 'Noto Serif SC', serif; color: #1d1d1f; font-weight: 700; }
.info-meta { color: #86868b; margin-bottom: 16px; display: flex; align-items: center; gap: 8px; font-size: 15px; }
.info-desc { margin-top: 20px; width: 100%; text-align: left; }
.info-desc h4 { margin-bottom: 10px; color: #1d1d1f; font-size: 16px; }
.info-desc p { color: #515154; line-height: 1.8; font-size: 15px; }

.share-dialog-body { min-height: 180px; }
.share-preview-card {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 14px;
  border-radius: 14px;
  background: rgba(0, 102, 204, 0.05);
  border: 1px solid rgba(0, 102, 204, 0.12);
}
.share-preview-card.is-paragraph {
  align-items: flex-start;
}
.share-preview-cover {
  width: 64px;
  height: 88px;
  object-fit: cover;
  border-radius: 10px;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.12);
}
.share-preview-main {
  flex: 1;
  min-width: 0;
}
.share-preview-title {
  font-size: 16px;
  font-weight: 700;
  color: #1d1d1f;
  margin-bottom: 6px;
}
.share-preview-meta,
.share-position-meta {
  font-size: 13px;
  color: #86868b;
}
.share-preview-quote {
  margin-top: 10px;
  font-size: 14px;
  line-height: 1.8;
  color: #515154;
  display: -webkit-box;
  -webkit-line-clamp: 5;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.share-dialog-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
  margin-top: 16px;
}

.audio-player-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.audio-player-heading {
  font-size: 20px;
  font-weight: 700;
  color: #1d1d1f;
}

.audio-player-subheading {
  font-size: 13px;
  color: #86868b;
}

.audio-player-element {
  width: 100%;
}

.audio-player-time {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #86868b;
}

.audio-player-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

/* === Comments Drawer === */
.comment-drawer .el-drawer__body { display: flex; flex-direction: column; padding: 0; }
.paragraph-quote { padding: 16px; background: rgba(0,0,0,0.02); border-bottom: 1px solid rgba(0,0,0,0.05); font-style: italic; color: #515154; font-size: 14px; line-height: 1.6; max-height: 120px; overflow-y: auto; }
.comment-list { flex: 1; overflow-y: auto; padding: 16px; }
.comment-list::-webkit-scrollbar { width: 6px; }
.comment-list::-webkit-scrollbar-thumb { background: rgba(0,0,0,0.1); border-radius: 3px; }
.comment-item { display: flex; gap: 16px; margin-bottom: 24px; border-bottom: 1px solid rgba(0,0,0,0.03); padding-bottom: 20px; }
.comment-body { flex: 1; }
.comment-header { display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 13px; align-items: center; }
.comment-user { font-weight: 700; color: #1d1d1f; }
.comment-ops { display: flex; gap: 16px; align-items: center; }
.op-icon { cursor: pointer; font-size: 16px; color: #86868b; transition: color 0.2s; }
.op-icon:hover { color: #ff3b30; }
.like-box { display: flex; align-items: center; gap: 4px; cursor: pointer; color: #86868b; transition: color 0.2s; }
.like-box:hover, .like-box .is-liked { color: #ff3b30; }
.is-liked { color: #ff3b30; }
.like-count { font-size: 13px; font-weight: 500; }
.comment-time { color: #a1a1a6; font-size: 12px; display: block; margin-top: 8px; }
.comment-content { font-size: 15px; line-height: 1.6; color: #1d1d1f; }
.comment-input-area { padding: 16px; border-top: 1px solid rgba(0,0,0,0.05); background: rgba(255,255,255,0.9); backdrop-filter: blur(10px); }

/* === My Comments === */
.my-comment-list { padding: 12px; }
.my-comment-item { padding: 16px; border: 1px solid rgba(0,0,0,0.04); cursor: pointer; transition: all 0.2s; border-radius: 12px; margin-bottom: 12px; box-shadow: 0 4px 12px rgba(0,0,0,0.01); }
.my-comment-item:hover { background: rgba(0,0,0,0.01); transform: translateY(-2px); box-shadow: 0 8px 20px rgba(0,0,0,0.04); }
.my-comment-pos { font-size: 13px; color: #0066cc; margin-bottom: 8px; font-weight: 600; }
.my-comment-quote { font-size: 13px; color: #86868b; background: rgba(0,0,0,0.03); padding: 8px 12px; margin-bottom: 10px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; border-radius: 6px; }
.my-comment-content { font-size: 15px; line-height: 1.6; color: #1d1d1f; }
.my-comment-time { font-size: 12px; color: #a1a1a6; text-align: right; margin-top: 10px; }

/* === AI Context Menu === */
.ai-menu { position: absolute; background: rgba(30, 30, 30, 0.95); backdrop-filter: blur(16px); color: #ffffff; border-radius: 12px; padding: 6px; display: flex; gap: 6px; box-shadow: 0 8px 24px rgba(0, 0, 0, 0.2); z-index: 999; animation: fadeInMenu 0.2s cubic-bezier(0.25, 0.8, 0.25, 1); border: 1px solid rgba(255,255,255,0.1); }
.menu-item { display: flex; flex-direction: column; align-items: center; padding: 10px 14px; cursor: pointer; border-radius: 8px; font-size: 13px; font-weight: 500; transition: all 0.2s; }
.menu-item:hover { background: rgba(255,255,255,0.15); }
@keyframes fadeInMenu { from { opacity: 0; transform: translateY(8px) scale(0.95); } to { opacity: 1; transform: translateY(0) scale(1); } }

/* === AI Chat === */
.chat-layout { display: flex; flex-direction: column; height: calc(100vh - 110px); }
.chat-history-box { flex: 1; overflow-y: auto; padding: 20px; background-color: rgba(0,0,0,0.01); }
.chat-row { display: flex; margin-bottom: 24px; align-items: flex-start; }
.row-left { flex-direction: row; }
.row-right { flex-direction: row-reverse; }
.avatar-wrapper { flex-shrink: 0; margin: 0 12px; }
.bubble-wrapper { max-width: 80%; }
.bubble-content { padding: 12px 18px; border-radius: 16px; font-size: 15px; line-height: 1.6; white-space: pre-wrap; word-break: break-all; box-shadow: 0 2px 8px rgba(0,0,0,0.04); }
.row-left .bubble-content { background: #ffffff; color: #1d1d1f; border: 1px solid rgba(0,0,0,0.05); border-top-left-radius: 4px; }
.row-right .bubble-content { background: #0066cc; color: #ffffff; border-top-right-radius: 4px; }
.chat-input-area { padding: 16px; background: rgba(255,255,255,0.9); backdrop-filter: blur(10px); border-top: 1px solid rgba(0,0,0,0.05); }
.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; color: #86868b; gap: 16px; }

/* === Notes === */
.note-card { background: #ffffff; border: 1px solid rgba(0,0,0,0.05); border-radius: 12px; padding: 16px; margin-bottom: 16px; box-shadow: 0 4px 12px rgba(0,0,0,0.02); }
.note-header { display: flex; justify-content: space-between; margin-bottom: 12px; }
.note-time { font-size: 12px; color: #a1a1a6; }
.note-quote { font-size: 13px; color: #515154; background: rgba(0,0,0,0.03); padding: 8px 12px; border-radius: 6px; margin-bottom: 12px; border-left: 4px solid #0066cc; }
.note-content { font-size: 15px; color: #1d1d1f; line-height: 1.6; white-space: pre-wrap; }

/* === Chapter Nav === */
.chapter-nav { display: flex; justify-content: center; gap: 32px; margin-top: 64px; padding: 24px 0; border-top: 1px solid rgba(0,0,0,0.05); }
.chapter-nav .el-button { border-radius: 20px; padding: 10px 32px; font-weight: 500; font-size: 15px; }

.empty-tip { text-align: center; color: #a1a1a6; padding: 80px 0; font-size: 16px; font-weight: 500; }
.clickable-user { cursor: pointer; transition: opacity 0.2s; }
.clickable-user:hover { opacity: 0.8; }
</style>