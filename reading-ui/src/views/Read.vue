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

// === 初始化 ===
onMounted(async () => {
  const userStr = localStorage.getItem('user')
  if (userStr) userInfo.value = JSON.parse(userStr)

  loadReadingSettings()

  await loadBookInfo()
  await loadCatalog()
  await loadProgress()
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
  if (currentAudio.value) {
    currentAudio.value.pause()
    currentAudio.value = null
  }
  if (currentChapterAudio.value) {
    currentChapterAudio.value.pause()
    currentChapterAudio.value = null
  }
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

  // 切换章节时停止上一个章节的播放
  if (currentChapterAudio.value) {
    currentChapterAudio.value.pause()
    currentChapterAudio.value = null
    isChapterPlaying.value = false
  }

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

// NEW: 分享段落
const shareParagraph = (index) => {
  const text = lines.value[index]
  const shareContent = `“${text.substring(0, 100)}${text.length>100?'...':''}”\n\n—— 出自《${bookInfo.value.title}》\n正在阅读：${window.location.href}`
  copyToClipboard(shareContent)
}

// NEW: 分享书籍
const shareBook = () => {
  const shareContent = `我正在智慧阅读阅读《${bookInfo.value.title}》，推荐给你！\n书籍链接：${window.location.href}`
  copyToClipboard(shareContent)
}

// NEW: 复制工具函数
const copyToClipboard = (text) => {
  if (navigator.clipboard) {
    navigator.clipboard.writeText(text).then(() => {
      ElMessage.success('分享内容已复制到剪贴板')
    }).catch(() => {
      ElMessage.error('复制失败，请手动复制')
    })
  } else {
    // 降级方案
    const textarea = document.createElement('textarea')
    textarea.value = text
    document.body.appendChild(textarea)
    textarea.select()
    document.execCommand('copy')
    document.body.removeChild(textarea)
    ElMessage.success('分享内容已复制到剪贴板')
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

const sendChat = async (textOverride = null) => {
  const message = typeof textOverride === 'string' ? textOverride : inputMessage.value
  if (!message || !message.trim() || isThinking.value) return

  let userId = userInfo.value.id
  if (!userId) { ElMessage.warning('请先登录'); return }

  chatList.value.push({ role: 'user', content: message })
  inputMessage.value = ''
  isThinking.value = true
  scrollToBottom()

  const aiMsgIndex = chatList.value.push({ role: 'ai', content: '' }) - 1

  try {
    const response = await fetch('/api/ai/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ userId, bookId, text: message })
    })

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')

    // === 核心修复：新增缓冲区变量 ===
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      const chunk = decoder.decode(value, { stream: true })
      // 1. 拼接到缓冲区
      buffer += chunk

      // 2. 按换行符切分
      const lines = buffer.split('\n')

      // 3. 关键步骤：把数组最后一行（可能是半截话）弹出来，放回缓冲区等待下一次拼接
      // 只有遇到 \n 才会切出完整的行，最后剩下的就是没 \n 的部分
      buffer = lines.pop()

      for (const line of lines) {
        if (line.startsWith('data:')) {
          // 去掉前缀，拼接到对话框
          const content = line.replace(/^data:/, '')
          chatList.value[aiMsgIndex].content += content
        }
      }
      scrollToBottom()
    }
  } catch (error) {
    chatList.value[aiMsgIndex].content += '\n[网络异常]'
    console.error(error)
  } finally {
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
  try {
    const res = await axios.get(`/api/sysNote/list/${bookId}`)
    if (res.data.code === '200') noteList.value = res.data.data
  } catch (e) { console.error(e) }
}

const handleDeleteNote = async (id) => {
  await axios.delete(`/api/sysNote/${id}`)
  fetchNotes()
}

const handleAiAction = (type) => {
  menuVisible.value = false
  toggleSidebar()
  let prompt = selectedText.value
  if (type === 'EXPLAIN') prompt = `请帮我解释：“${selectedText.value}”`
  else if (type === 'SUMMARY') prompt = `请提炼摘要：“${selectedText.value}”`
  else if (type === 'CONTINUE') prompt = `请续写：“${selectedText.value}”`
  else if (type === 'TTS') { handleTTS(); return }
  sendChat(prompt)
}

const handleTTS = async () => {
  if (currentAudio.value) { currentAudio.value.pause(); currentAudio.value = null }
  menuVisible.value = false
  ElMessage.info('正在生成语音...')
  try {
    const res = await axios.post('/api/ai/tts', { text: selectedText.value, type: 'TTS', voice: readingConfig.voice || 'cherry' })
    if (res.data.code === '200') {
      const audio = new Audio(res.data.data)
      currentAudio.value = audio
      audio.play()
    }
  } catch (e) { ElMessage.error('语音服务异常') }
}

const handleBeforeUnload = () => { saveProgress() }
const goBack = async () => {
  if (currentChapterAudio.value) {
    currentChapterAudio.value.pause()
    currentChapterAudio.value = null
  }
  await saveProgress()
  router.push('/shelf')
}

// === 整章朗读 (听本章) ===
const toggleChapterTts = async () => {
  // 1. 如果正在播放，则暂停
  if (currentChapterAudio.value && isChapterPlaying.value) {
    currentChapterAudio.value.pause()
    isChapterPlaying.value = false
    return
  }

  // 2. 如果已经加载过实例但暂停了，则继续播放
  if (currentChapterAudio.value && !isChapterPlaying.value) {
    currentChapterAudio.value.play()
    isChapterPlaying.value = true
    return
  }

  // 3. 如果没加载过，去请求后端
  if (!catalog.value || catalog.value.length === 0) return
  
  const chapterId = catalog.value[chapterIndex.value].id
  const voice = readingConfig.voice || 'cherry'
  
  isGeneratingTts.value = true
  ElMessage.info('正在请求合成，请稍候...')
  
  try {
    const res = await axios.get(`/api/ai/chapter_tts/${chapterId}?voice=${voice}`)
    if (res.data.code === '200') {
      const status = res.data.data
      if (status === 'processing') {
        // 进入轮询模式
        pollTtsStatus(chapterId, voice)
      } else {
        // 直接返回了 URL
        playChapterAudio(status)
      }
    } else {
      ElMessage.error(res.data.msg || '语音获取失败')
      isGeneratingTts.value = false
    }
  } catch (e) {
    ElMessage.error('服务请求异常')
    isGeneratingTts.value = false
  }
}

// 轮询状态
const pollTtsStatus = (chapterId, voice) => {
  const timer = setInterval(async () => {
    try {
      const res = await axios.get(`/api/ai/chapter_tts/status/${chapterId}?voice=${voice}`)
      if (res.data.code === '200') {
        const status = res.data.data
        if (status === 'processing') {
          // 继续等待
          showGeneratingTip()
        } else if (status.startsWith('http')) {
          // 合成成功
          clearInterval(timer)
          playChapterAudio(status)
        } else if (status.startsWith('error:')) {
          // 合成失败
          clearInterval(timer)
          ElMessage.error('合成失败: ' + status.substring(6))
          isGeneratingTts.value = false
        }
      }
    } catch (e) {
      clearInterval(timer)
      isGeneratingTts.value = false
    }
  }, 3000) // 每 3 秒轮询一次
}

let lastTipTime = 0
const showGeneratingTip = () => {
  const now = Date.now()
  if (now - lastTipTime > 15000) { // 每 15 秒提示一次，避免太吵
    ElMessage.info('长章节合成中，请耐心等待...')
    lastTipTime = now
  }
}

const playChapterAudio = (url) => {
  if (currentChapterAudio.value) {
    currentChapterAudio.value.pause()
  }
  
  currentChapterAudio.value = new Audio(url)
  currentChapterAudio.value.onended = () => {
    isChapterPlaying.value = false
  }
  
  currentChapterAudio.value.play().then(() => {
    isChapterPlaying.value = true
    isGeneratingTts.value = false
    ElMessage.success('语音加载成功')
  }).catch(err => {
    console.error('播放失败', err)
    ElMessage.error('音频文件加载失败，可能文件损坏')
    isGeneratingTts.value = false
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
            <el-icon class="tool-icon" @click.stop="shareParagraph(index)"><Share /></el-icon>
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
      <el-icon size="24" v-if="isGeneratingTts" class="is-loading"><Loading /></el-icon>
      <el-icon size="24" v-else-if="isChapterPlaying"><Microphone style="color: #67C23A;" /></el-icon>
      <el-icon size="24" v-else><Microphone /></el-icon>
      <span class="toggle-text">{{ isGeneratingTts ? '生成中' : (isChapterPlaying ? '暂停' : '听本章') }}</span>
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

    <div class="sidebar-toggle share-book-toggle" @click.stop="shareBook">
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
/* === 主题配色 === */
.read-container { min-height: 100vh; transition: background-color 0.3s, color 0.3s; }
.theme-default { background-color: #f5f0e6; color: #3d3632; }
.theme-default .read-header { background: #fffdf9; border-bottom: 1px solid #e8e0d6; color: #3d3632; }
.theme-default .text-paragraph { color: #3d3632; }
.theme-green { background-color: #cce8cf; color: #004d00; }
.theme-green .read-header { background: #b0dcb5; border-bottom: 1px solid #99c79e; color: #003300; }
.theme-green .text-paragraph { color: #004d00; }
.theme-green .text-paragraph:hover { background-color: rgba(0, 50, 0, 0.05); }
.theme-dark { background-color: #1a1a1a; color: #b0b0b0; }
.theme-dark .read-header { background: #2c2c2c; border-bottom: 1px solid #444; color: #ccc; }
.theme-dark .text-paragraph { color: #b0b0b0; }
.theme-dark .chapter-title { color: #ddd; }
.theme-dark .text-paragraph:hover { background-color: rgba(255, 255, 255, 0.05); }
.theme-dark .book-title { color: #ccc; }
.theme-high-contrast { background-color: #000000; color: #ffffff; }
.theme-high-contrast .read-header { background: #000; border-bottom: 2px solid #fff; color: #fff; }
.theme-high-contrast .text-paragraph { color: #fff; font-weight: bold; }
.theme-high-contrast .chapter-title { color: #fff; text-decoration: underline; }
.theme-high-contrast .book-title { color: #fff; }

/* === 顶栏 === */
.read-header { position: fixed; top: 0; left: 0; right: 0; height: 50px; display: flex; justify-content: space-between; align-items: center; padding: 0 20px; z-index: 100; box-shadow: 0 1px 3px rgba(60, 40, 20, 0.06); }
.book-title { margin-left: 15px; font-weight: 600; font-family: 'Noto Serif SC', serif; }
.read-content { padding-top: 80px; padding-bottom: 50px; max-width: 900px; margin: 0 auto; }
.chapter-title { text-align: center; font-size: 22px; margin-bottom: 36px; font-family: 'Noto Serif SC', serif; color: #2e2520; font-weight: 600; }

/* === 段落 === */
.text-paragraph { font-family: "Georgia", "Noto Serif SC", "Microsoft YaHei", serif; margin-bottom: 22px; text-indent: 2em; text-align: justify; cursor: pointer; padding: 10px 20px; border-radius: 4px; transition: background-color 0.2s; position: relative; }
.text-paragraph:hover { background-color: rgba(139, 111, 82, 0.04); }
.text-paragraph.selected-paragraph { background-color: rgba(139, 111, 82, 0.08); }

/* === 段落工具栏 === */
.paragraph-tools { position: absolute; right: 10px; top: 50%; transform: translateY(-50%); display: flex; gap: 8px; background: rgba(255,253,249,0.9); padding: 4px 8px; border-radius: 4px; box-shadow: 0 1px 6px rgba(60, 40, 20, 0.1); border: 1px solid #e8e0d6; }
.tool-icon { color: #8b6f52; cursor: pointer; font-size: 18px; transition: all 0.2s; }
.tool-icon:hover { transform: scale(1.15); color: #5a4435; }

/* === 悬浮按钮 === */
.sidebar-toggle { position: fixed; width: 54px; height: 54px; background: #fffdf9; border-radius: 14px; box-shadow: 0 2px 8px rgba(60, 40, 20, 0.1); border: 1px solid #e8e0d6; display: flex; flex-direction: column; align-items: center; justify-content: center; cursor: pointer; z-index: 900; color: #7a6e63; transition: all 0.2s; }
.sidebar-toggle:hover { box-shadow: 0 4px 12px rgba(60, 40, 20, 0.14); color: #5a4435; border-color: #c4b09a; }
.toggle-text { font-size: 11px; margin-top: 2px; font-weight: 600; }

.ai-toggle { bottom: 100px; right: 40px; }
.add-to-shelf-toggle { bottom: 164px; right: 40px; }
.chapter-tts-toggle { bottom: 228px; right: 40px; }
.add-to-shelf-toggle.is-added { color: #6a8c5a; border-color: #a3c296; }
.my-comments-toggle { bottom: 100px; left: 40px; }
.share-book-toggle { bottom: 164px; left: 40px; }

/* === 设置面板 === */
.setting-group { margin-bottom: 22px; border-bottom: 1px solid #f0ece4; padding-bottom: 14px; }
.setting-group:last-child { border-bottom: none; }
.setting-label { font-weight: 600; margin-bottom: 10px; color: #3d3632; display: flex; justify-content: space-between; align-items: center; }
.desc { font-size: 12px; color: #9b8e82; margin-top: 5px; }
.theme-options { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.theme-btn { text-align: center; padding: 10px; border-radius: 4px; cursor: pointer; border: 2px solid transparent; font-size: 13px; }
.theme-btn.active { border-color: #8b6f52; position: relative; }
.theme-btn.active::after { content: '✔'; position: absolute; top: 2px; right: 5px; color: #8b6f52; font-size: 12px; }
.t-default { background: #f5f0e6; color: #3d3632; }
.t-green { background: #cce8cf; color: #004d00; }
.t-dark { background: #1a1a1a; color: #ccc; }
.t-high { background: #000; color: #fff; font-weight: bold; border: 1px solid #ccc; }

/* === 目录 === */
.catalog-list { padding: 10px; }
.catalog-item { padding: 11px; border-bottom: 1px solid #f0ece4; cursor: pointer; font-size: 14px; transition: background 0.15s; }
.catalog-item:hover { background: #faf5ed; color: #5a4435; }
.catalog-item.active { color: #5a4435; font-weight: 600; background: #f5f0e8; }

/* === 书籍信息弹窗 === */
.book-info-content { display: flex; flex-direction: column; align-items: center; padding: 18px; }
.book-cover-large { width: 120px; height: 160px; object-fit: cover; border-radius: 4px; margin-bottom: 18px; box-shadow: 0 2px 8px rgba(60,40,20,0.1); }
.info-title { font-size: 20px; margin-bottom: 14px; font-family: 'Noto Serif SC', serif; color: #2e2520; }
.info-meta { color: #7a6e63; margin-bottom: 8px; display: flex; align-items: center; gap: 5px; }
.info-desc { margin-top: 18px; width: 100%; text-align: left; }
.info-desc h4 { margin-bottom: 8px; color: #4a3828; }
.info-desc p { color: #5a5048; line-height: 1.7; font-size: 14px; }

/* === 段落评论抽屉 === */
.comment-drawer .el-drawer__body { display: flex; flex-direction: column; padding: 0; }
.paragraph-quote { padding: 14px; background: #faf5ed; border-bottom: 1px solid #e8e0d6; font-style: italic; color: #7a6e63; font-size: 14px; line-height: 1.6; max-height: 100px; overflow-y: auto; }
.comment-list { flex: 1; overflow-y: auto; padding: 14px; }
.comment-item { display: flex; gap: 12px; margin-bottom: 18px; border-bottom: 1px solid #f5f0e8; padding-bottom: 14px; }
.comment-body { flex: 1; }
.comment-header { display: flex; justify-content: space-between; margin-bottom: 5px; font-size: 12px; align-items: center; }
.comment-user { font-weight: 600; color: #4a3828; }
.comment-ops { display: flex; gap: 10px; align-items: center; }
.op-icon { cursor: pointer; font-size: 16px; color: #b5a99c; }
.op-icon:hover { color: #a34040; }
.like-box { display: flex; align-items: center; gap: 2px; cursor: pointer; color: #b5a99c; }
.like-box:hover, .like-box .is-liked { color: #a34040; }
.is-liked { color: #a34040; }
.like-count { font-size: 12px; }
.comment-time { color: #c4b9ab; font-size: 12px; display: block; margin-top: 5px; }
.comment-content { font-size: 14px; line-height: 1.5; color: #3d3632; }
.comment-input-area { padding: 14px; border-top: 1px solid #e8e0d6; background: #fffdf9; }

/* === 我的评论 === */
.my-comment-list { padding: 10px; }
.my-comment-item { padding: 12px; border-bottom: 1px solid #f0ece4; cursor: pointer; transition: background 0.15s; border-radius: 4px; }
.my-comment-item:hover { background: #faf5ed; }
.my-comment-pos { font-size: 12px; color: #8b6f52; margin-bottom: 4px; }
.my-comment-quote { font-size: 12px; color: #9b8e82; background: #f5f0e8; padding: 4px 6px; margin-bottom: 6px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; border-radius: 3px; }
.my-comment-content { font-size: 14px; line-height: 1.4; color: #3d3632; }
.my-comment-time { font-size: 12px; color: #c4b9ab; text-align: right; margin-top: 4px; }

/* === AI 右键菜单 === */
.ai-menu { position: absolute; background: #3d2e20; color: #f0ece4; border-radius: 6px; padding: 5px; display: flex; gap: 4px; box-shadow: 0 4px 14px rgba(40, 28, 16, 0.3); z-index: 999; animation: fadeIn 0.15s ease; }
.menu-item { display: flex; flex-direction: column; align-items: center; padding: 8px 12px; cursor: pointer; border-radius: 4px; font-size: 12px; }
.menu-item:hover { background: rgba(255,255,255,0.1); }
@keyframes fadeIn { from { opacity: 0; transform: translateY(4px); } to { opacity: 1; transform: translateY(0); } }

/* === AI 聊天 === */
.chat-layout { display: flex; flex-direction: column; height: calc(100vh - 110px); }
.chat-history-box { flex: 1; overflow-y: auto; padding: 14px; background-color: #faf8f5; }
.chat-row { display: flex; margin-bottom: 18px; align-items: flex-start; }
.row-left { flex-direction: row; }
.row-right { flex-direction: row-reverse; }
.avatar-wrapper { flex-shrink: 0; margin: 0 10px; }
.bubble-wrapper { max-width: 80%; }
.bubble-content { padding: 10px 14px; border-radius: 6px; font-size: 14px; line-height: 1.6; white-space: pre-wrap; word-break: break-all; box-shadow: 0 1px 2px rgba(60,40,20,0.05); }
.row-left .bubble-content { background: #fffdf9; color: #3d3632; border: 1px solid #e8e0d6; border-top-left-radius: 2px; }
.row-right .bubble-content { background: #d4e8c4; color: #2a3a20; border-top-right-radius: 2px; }
.chat-input-area { padding: 14px; background: #fffdf9; border-top: 1px solid #e8e0d6; }
.empty-state { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100%; color: #b5a99c; gap: 10px; }
.msg-actions { display: flex; justify-content: flex-end; margin-top: 8px; opacity: 0; transform: translateY(4px); transition: all 0.25s; }
.chat-row:hover .msg-actions { opacity: 1; transform: translateY(0); }
.action-icon { cursor: pointer; font-size: 22px; color: #7a6e63; padding: 6px; border-radius: 50%; background-color: #fffdf9; box-shadow: 0 1px 4px rgba(60,40,20,0.08); transition: all 0.2s; display: flex; align-items: center; justify-content: center; }
.action-icon:hover { color: #fff; background-color: #8b6f52; box-shadow: 0 3px 10px rgba(139,111,82,0.3); transform: scale(1.1); }

/* === 笔记 === */
.note-card { background: #fffdf9; border: 1px solid #e8e0d6; border-radius: 6px; padding: 12px; margin-bottom: 12px; }
.note-header { display: flex; justify-content: space-between; margin-bottom: 8px; }
.note-time { font-size: 12px; color: #b5a99c; }
.note-quote { font-size: 12px; color: #9b8e82; background: #f5f0e8; padding: 4px 8px; border-radius: 3px; margin-bottom: 8px; border-left: 3px solid #d4c4a8; }
.note-content { font-size: 14px; color: #3d3632; line-height: 1.5; white-space: pre-wrap; }

/* === 章节导航 === */
.chapter-nav { display: flex; justify-content: center; gap: 24px; margin-top: 40px; padding: 20px 0; }
.empty-tip { text-align: center; color: #b5a99c; padding: 60px 0; font-size: 15px; }

/* === 可点击用户 === */
.clickable-user { cursor: pointer; transition: opacity 0.15s; }
.clickable-user:hover { opacity: 0.75; }
</style>