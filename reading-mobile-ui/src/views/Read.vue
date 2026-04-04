<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick, reactive, watch, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showSuccessToast, showFailToast, showConfirmDialog, showDialog } from 'vant'
import axios from 'axios'
import { fetchEventSource } from '@microsoft/fetch-event-source'

const route = useRoute()
const router = useRouter()
const bookId = route.params.id

const content = ref('')
const bookInfo = ref({ title: '阅读', author: '' })
const lines = ref([])
const currentLine = ref(0)
const userInfo = ref({})
const catalog = ref([])
const chapterIndex = ref(0)
const currentChapterTitle = ref('')
const isLoading = ref(false)
const isAddedToShelf = ref(false)

// Drawers & popups
const showCatalog = ref(false)
const showSettings = ref(false)
const showAiDrawer = ref(false)
const showToolbar = ref(false)

// AI Chat
const chatList = ref([])
const inputMessage = ref('')
const isThinking = ref(false)
const currentConversationId = ref('')

// Notes
const noteList = ref([])
const activeAiTab = ref('ai')

// TTS
const isChapterPlaying = ref(false)
let speechUtterance = null

// Reading settings
const readingConfig = reactive({
  fontSize: 18, lineHeight: 1.8, theme: 'default', voice: 'cherry'
})

// Selected text for AI
const selectedText = ref('')
const showAiActions = ref(false)

// Paragraph comments
const showParagraphDrawer = ref(false)
const selectedParagraphIndex = ref(-1)
const paragraphComments = ref([])
const newParagraphComment = ref('')

onMounted(async () => {
  const u = localStorage.getItem('user')
  if (u) userInfo.value = JSON.parse(u)
  const saved = localStorage.getItem('readingConfig')
  if (saved) Object.assign(readingConfig, JSON.parse(saved))

  await loadBookInfo()
  await loadCatalog()
  await loadProgress()
  await loadCurrentChapter()
  checkShelf()
  fetchNotes()
  window.addEventListener('beforeunload', saveProgress)
  document.addEventListener('selectionchange', handleTextSelect)
})

onBeforeUnmount(() => {
  if (window.speechSynthesis) window.speechSynthesis.cancel()
  window.removeEventListener('beforeunload', saveProgress)
  document.removeEventListener('selectionchange', handleTextSelect)
  saveProgress()
})

watch(readingConfig, (v) => localStorage.setItem('readingConfig', JSON.stringify(v)), { deep: true })

const loadBookInfo = async () => {
  try {
    const res = await axios.get(`/api/sysBook/${bookId}`)
    if (res.data.code === '200') bookInfo.value = res.data.data
  } catch (e) { showFailToast('加载书籍失败') }
}

const loadCatalog = async () => {
  const res = await axios.get(`/api/sysBook/catalog/${bookId}`)
  if (res.data.code === '200') {
    catalog.value = res.data.data
    if (catalog.value.length === 0) {
      await axios.post(`/api/sysBook/analyze/${bookId}`)
      const r2 = await axios.get(`/api/sysBook/catalog/${bookId}`)
      catalog.value = r2.data.data
    }
  }
}

const loadProgress = async () => {
  if (!userInfo.value.id) return
  try {
    const res = await axios.get('/api/bookshelf/detail', { params: { userId: userInfo.value.id, bookId } })
    if (res.data.data) {
      chapterIndex.value = res.data.data.currentChapterIndex || 0
      currentLine.value = res.data.data.progressIndex || 0
    }
  } catch (e) {}
}

const loadCurrentChapter = async () => {
  if (catalog.value.length === 0) return
  isLoading.value = true
  if (window.speechSynthesis) { window.speechSynthesis.cancel(); isChapterPlaying.value = false }

  const ch = catalog.value[chapterIndex.value]
  currentChapterTitle.value = ch.title
  try {
    const res = await axios.get(`/api/sysBook/chapter/${ch.id}`)
    if (res.data.code === '200') {
      const text = res.data.data.content || ''
      lines.value = text.split(/\r?\n/).filter(l => l.trim())
      nextTick(() => {
        if (currentLine.value > 0) {
          const el = document.getElementById(`m-line-${currentLine.value}`)
          if (el) el.scrollIntoView({ behavior: 'auto', block: 'center' })
        } else {
          window.scrollTo(0, 0)
        }
      })
    }
  } catch (e) { showFailToast('章节加载失败') }
  finally { isLoading.value = false }
}

const changeChapter = (offset) => {
  const n = chapterIndex.value + offset
  if (n >= 0 && n < catalog.value.length) {
    saveProgress(); chapterIndex.value = n; currentLine.value = 0; loadCurrentChapter()
  }
}

const jumpToChapter = (idx) => {
  saveProgress(); chapterIndex.value = idx; currentLine.value = 0; loadCurrentChapter(); showCatalog.value = false
}

const saveProgress = async () => {
  if (!userInfo.value.id) return
  try {
    await axios.post('/api/bookshelf/updateProgress', {
      userId: userInfo.value.id, bookId, currentChapterIndex: chapterIndex.value, progressIndex: currentLine.value
    })
  } catch (e) {}
}

const checkShelf = async () => {
  if (!userInfo.value.id) return
  const res = await axios.get(`/api/bookshelf/list/${userInfo.value.id}`)
  if (res.data.code === '200') isAddedToShelf.value = res.data.data.some(i => i.bookId === parseInt(bookId))
}

const toggleShelf = async () => {
  if (!userInfo.value.id) return showToast('请先登录')
  if (isAddedToShelf.value) {
    await showConfirmDialog({ message: '取消收藏？阅读进度将不再保存。' })
    await axios.delete('/api/bookshelf/removeByBook', { params: { userId: userInfo.value.id, bookId } })
    showSuccessToast('已取消'); isAddedToShelf.value = false
  } else {
    const r = await axios.post('/api/bookshelf/add', { userId: userInfo.value.id, bookId })
    if (r.data.code === '200') { showSuccessToast('已加入书架'); isAddedToShelf.value = true }
  }
}

// Text selection & AI
const handleTextSelect = () => {
  const sel = window.getSelection()
  const text = sel?.toString().trim()
  if (text && text.length >= 2) {
    selectedText.value = text
    showAiActions.value = true
  } else {
    showAiActions.value = false
  }
}

// Paragraph
const handleParagraphClick = (idx) => {
  if (selectedText.value) return // Don't trigger if selecting text
  selectedParagraphIndex.value = idx
  showParagraphDrawer.value = true
  fetchParagraphComments(idx)
}

const fetchParagraphComments = async (idx) => {
  try {
    const res = await axios.get(`/api/paragraphComment/list/${bookId}/${chapterIndex.value}/${idx}`)
    paragraphComments.value = res.data.data || []
  } catch (e) { console.error(e) }
}

const submitParagraphComment = async () => {
  if (!userInfo.value.id) return showToast('请先登录')
  if (!newParagraphComment.value.trim()) return showToast('请输入内容')
  try {
    await axios.post('/api/paragraphComment/add', {
      userId: userInfo.value.id, bookId, chapterIndex: chapterIndex.value, 
      paragraphIndex: selectedParagraphIndex.value, content: newParagraphComment.value, 
      quote: lines.value[selectedParagraphIndex.value].substring(0,25) + '...'
    })
    showSuccessToast('评论成功')
    newParagraphComment.value = ''
    fetchParagraphComments(selectedParagraphIndex.value)
  } catch (e) { showFailToast('评论失败') }
}

const shareParagraph = () => {
  if (navigator.clipboard) {
    const t = lines.value[selectedParagraphIndex.value]
    navigator.clipboard.writeText(`"${t}" -- 《${bookInfo.value.title}》`)
    showSuccessToast('已复制该段落内容')
  }
}

const shareBook = () => {
  if (navigator.clipboard) {
    navigator.clipboard.writeText(`推荐好书《${bookInfo.value.title}》：${window.location.href}`)
    showSuccessToast('书籍链接已复制')
  }
}

const handleAiAction = (type) => {
  showAiActions.value = false
  showAiDrawer.value = true
  activeAiTab.value = 'ai'
  let mode = '', display = ''
  if (type === 'EXPLAIN') { mode = '请用大白话详细解释这段话'; display = `【释疑】${selectedText.value}` }
  else if (type === 'SUMMARY') { mode = '请提炼核心摘要'; display = `【摘要】${selectedText.value}` }
  else if (type === 'CONTINUE') { mode = '请根据语境续写'; display = `【续写】${selectedText.value}` }
  else if (type === 'TTS') { handleTTS(); return }
  sendChat(selectedText.value, mode, display)
}

const handleTTS = async () => {
  showAiActions.value = false
  showToast('正在生成语音...')
  try {
    const res = await axios.post('/api/ai/tts', { text: selectedText.value, type: 'TTS', voice: readingConfig.voice || 'cherry' })
    if (res.data.code === '200') new Audio(res.data.data).play()
  } catch (e) { showFailToast('语音失败') }
}

const sendChat = async (ctx, modeOverride, displayMsg) => {
  const instruction = modeOverride || inputMessage.value
  const textToAnalyze = ctx || selectedText.value || '请直接回答用户的问题'
  const msgToShow = displayMsg || inputMessage.value
  if (!instruction?.trim() || isThinking.value) return
  if (!userInfo.value.id) return showToast('请先登录')

  chatList.value.push({ role: 'user', content: msgToShow })
  inputMessage.value = ''
  isThinking.value = true
  const aiIdx = chatList.value.push({ role: 'ai', content: '' }) - 1

  try {
    await fetchEventSource('/api/difyreading/analyze', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Accept': 'text/event-stream' },
      body: JSON.stringify({
        text: textToAnalyze, mode: instruction,
        conversationId: currentConversationId.value,
        bookName: bookInfo.value.title
      }),
      onmessage(event) {
        const d = JSON.parse(event.data)
        if (d.event === 'message') {
          chatList.value[aiIdx].content += d.answer || ''
          if (d.conversation_id) currentConversationId.value = d.conversation_id
        }
        if (d.event === 'error') chatList.value[aiIdx].content += '\n[出错]'
      },
      onclose() { isThinking.value = false },
      onerror(err) { isThinking.value = false; chatList.value[aiIdx].content += '\n[连接中断]'; throw err }
    })
  } catch (e) { isThinking.value = false }
}

const saveNote = async (msg) => {
  if (!userInfo.value.id) return showToast('请先登录')
  const quote = selectedText.value || msg.substring(0, 15) + '...'
  await axios.post('/api/sysNote/add', { userId: userInfo.value.id, bookId, selectedText: quote, content: msg })
  showSuccessToast('已保存笔记'); fetchNotes()
}

const fetchNotes = async () => {
  if (!userInfo.value.id) return
  const res = await axios.get(`/api/sysNote/list/${bookId}`, { params: { userId: userInfo.value.id } })
  if (res.data.code === '200') noteList.value = res.data.data
}

const deleteNote = async (id) => { await axios.delete(`/api/sysNote/${id}`); fetchNotes() }

const toggleChapterTts = () => {
  const synth = window.speechSynthesis
  if (!synth) return showFailToast('浏览器不支持语音')
  if (synth.speaking && !synth.paused && isChapterPlaying.value) { synth.pause(); isChapterPlaying.value = false; return }
  if (synth.paused && !isChapterPlaying.value) { synth.resume(); isChapterPlaying.value = true; return }
  synth.cancel()
  const fullText = lines.value.join('，')
  if (!fullText) return showToast('无内容')
  speechUtterance = new SpeechSynthesisUtterance(fullText)
  speechUtterance.rate = 1.0
  const voices = synth.getVoices()
  const zh = voices.filter(v => v.lang.includes('zh') || v.lang.includes('cmn'))
  if (zh.length) speechUtterance.voice = zh.find(v => v.name.includes('Xiaoxiao')) || zh[0]
  speechUtterance.onstart = () => isChapterPlaying.value = true
  speechUtterance.onend = () => isChapterPlaying.value = false
  speechUtterance.onerror = () => isChapterPlaying.value = false
  synth.speak(speechUtterance)
}

const goBack = async () => {
  if (window.speechSynthesis) window.speechSynthesis.cancel()
  await saveProgress(); router.push('/shelf')
}

const themeClass = computed(() => `theme-${readingConfig.theme}`)
</script>

<template>
  <div class="read-page" :class="themeClass">
    <!-- Top Bar -->
    <van-nav-bar :title="bookInfo.title" left-arrow @click-left="goBack" class="read-nav" :border="false">
      <template #right>
        <van-icon name="bars" size="20" @click="showCatalog = true" />
      </template>
    </van-nav-bar>

    <!-- Content -->
    <div class="read-body" v-loading="isLoading">
      <h2 class="chapter-title" v-if="currentChapterTitle">{{ currentChapterTitle }}</h2>
      <p
        v-for="(line, idx) in lines"
        :key="idx"
        :id="'m-line-' + idx"
        class="text-line"
        @click="handleParagraphClick(idx)"
        :style="{ fontSize: readingConfig.fontSize + 'px', lineHeight: readingConfig.lineHeight }"
      >{{ line }}</p>

      <van-empty v-if="lines.length === 0 && !isLoading" description="暂无内容" />

      <div class="chapter-nav" v-if="catalog.length > 0">
        <van-button :disabled="chapterIndex === 0" @click="changeChapter(-1)" round plain>上一章</van-button>
        <van-button :disabled="chapterIndex >= catalog.length - 1" @click="changeChapter(1)" round plain>下一章</van-button>
      </div>
    </div>

    <!-- Bottom Toolbar -->
    <div class="bottom-bar">
      <div class="bar-item" @click="showSettings = true"><van-icon name="setting-o" size="20" /><span>设置</span></div>
      <div class="bar-item" @click="showCatalog = true"><van-icon name="bars" size="20" /><span>目录</span></div>
      <div class="bar-item" @click="toggleChapterTts">
        <van-icon :name="isChapterPlaying ? 'pause-circle-o' : 'music-o'" size="20" :color="isChapterPlaying ? '#52c41a' : ''" />
        <span>听书</span>
      </div>
      <div class="bar-item" @click="showAiDrawer = true"><van-icon name="chat-o" size="20" /><span>助手</span></div>
      <div class="bar-item" @click="toggleShelf">
        <van-icon :name="isAddedToShelf ? 'star' : 'star-o'" size="20" :color="isAddedToShelf ? '#f5a623' : ''" />
        <span>收藏</span>
      </div>
      <div class="bar-item" @click="shareBook"><van-icon name="share-o" size="20" /><span>分享</span></div>
    </div>

    <!-- Inline Selection Actions -->
    <div v-show="showAiActions" class="ai-inline-actions">
      <span class="ai-btn" @click="handleAiAction('EXPLAIN')">💡释意</span>
      <span class="ai-btn" @click="handleAiAction('SUMMARY')">📝摘要</span>
      <span class="ai-btn" @click="handleAiAction('CONTINUE')">✍续写</span>
      <span class="ai-btn" @click="handleAiAction('TTS')">🔊朗读</span>
    </div>

    <!-- Paragraph Comments Drawer -->
    <van-popup v-model:show="showParagraphDrawer" position="bottom" round :style="{ height: '60%' }">
      <div class="p-comment-drawer">
        <div class="p-drawer-tools">
          <span style="font-size: 14px; font-weight: 600;">段落评论区</span>
          <div>
            <van-button size="mini" type="primary" plain @click="shareParagraph" style="margin-right: 8px;">分享段落</van-button>
            <van-icon name="cross" @click="showParagraphDrawer = false" />
          </div>
        </div>
        <div class="p-quote">"{{ lines[selectedParagraphIndex]?.substring(0, 30) }}..."</div>
        
        <div class="p-comment-list">
          <van-empty v-if="paragraphComments.length === 0" description="暂无评论，来抢沙发吧~" image-size="60" />
          <div class="p-comment-item" v-for="c in paragraphComments" :key="c.id">
            <van-image round :src="c.avatar || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" class="p-avatar" />
            <div class="p-content">
              <div class="p-name">{{ c.nickname }} <span class="p-time">{{ c.createTime?.substring(5,16) }}</span></div>
              <div class="p-text">{{ c.content }}</div>
            </div>
          </div>
        </div>

        <div class="p-input-box">
          <van-field v-model="newParagraphComment" placeholder="发条评论支持一下..." rows="1" autosize type="textarea" />
          <van-button type="primary" size="small" round style="white-space: nowrap;" @click="submitParagraphComment">发表</van-button>
        </div>
      </div>
    </van-popup>

    <!-- Catalog Popup -->
    <van-popup v-model:show="showCatalog" position="right" :style="{ width: '75%', height: '100%' }">
      <div class="catalog-popup">
        <div class="catalog-title">📖 目录</div>
        <div class="catalog-list">
          <div
            v-for="(ch, idx) in catalog"
            :key="ch.id"
            :class="['catalog-item', idx === chapterIndex ? 'active' : '']"
            @click="jumpToChapter(idx)"
          >{{ ch.title }}</div>
        </div>
      </div>
    </van-popup>

    <!-- Settings Popup -->
    <van-popup v-model:show="showSettings" position="bottom" round :style="{ maxHeight: '60%' }">
      <div class="settings-popup">
        <h3>阅读设置</h3>
        <div class="s-group">
          <div class="s-label">主题</div>
          <div class="theme-row">
            <span :class="['t-btn', 't-default', readingConfig.theme === 'default' ? 'sel' : '']" @click="readingConfig.theme='default'">默认</span>
            <span :class="['t-btn', 't-green', readingConfig.theme === 'green' ? 'sel' : '']" @click="readingConfig.theme='green'">护眼</span>
            <span :class="['t-btn', 't-dark', readingConfig.theme === 'dark' ? 'sel' : '']" @click="readingConfig.theme='dark'">暗夜</span>
            <span :class="['t-btn', 't-high', readingConfig.theme === 'high-contrast' ? 'sel' : '']" @click="readingConfig.theme='high-contrast'">适老</span>
          </div>
        </div>
        <div class="s-group">
          <div class="s-label">字号 {{ readingConfig.fontSize }}px</div>
          <van-slider v-model="readingConfig.fontSize" :min="14" :max="32" :step="1" active-color="#8b6f52" />
        </div>
        <div class="s-group">
          <div class="s-label">行距 {{ readingConfig.lineHeight }}</div>
          <van-slider v-model="readingConfig.lineHeight" :min="1.4" :max="2.5" :step="0.1" active-color="#8b6f52" />
        </div>
      </div>
    </van-popup>

    <!-- AI Drawer -->
    <van-popup v-model:show="showAiDrawer" position="bottom" round :style="{ height: '70%' }">
      <div class="ai-popup">
        <van-tabs v-model:active="activeAiTab" animated>
          <van-tab title="AI 助手" name="ai">
            <div class="chat-box" id="mobileChatBox">
              <div v-if="chatList.length === 0" class="chat-empty">
                <van-icon name="service-o" size="40" color="#ddd" />
                <p>你好，我是你的智能书童</p>
              </div>
              <div v-for="(msg, i) in chatList" :key="i" :class="['chat-row', msg.role === 'user' ? 'mine' : 'theirs']">
                <div class="chat-bubble">{{ msg.content }}</div>
                <div v-if="msg.role === 'ai' && msg.content" class="bubble-action" @click="saveNote(msg.content)">💾 保存笔记</div>
              </div>
            </div>
            <div class="chat-input">
              <van-field v-model="inputMessage" placeholder="输入问题..." :disabled="isThinking" @keypress.enter="sendChat(null)">
                <template #button>
                  <van-button size="small" type="primary" :loading="isThinking" @click="sendChat(null)">发送</van-button>
                </template>
              </van-field>
            </div>
          </van-tab>
          <van-tab title="笔记" name="note">
            <div class="note-list">
              <van-empty v-if="noteList.length === 0" description="暂无笔记" />
              <div v-for="note in noteList" :key="note.id" class="note-card m-card">
                <div class="note-header">
                  <span class="note-time">{{ note.createTime?.replace('T', ' ') }}</span>
                  <van-icon name="delete-o" color="#ee4d38" @click="deleteNote(note.id)" />
                </div>
                <div class="note-quote">"{{ note.selectedText?.substring(0, 30) }}..."</div>
                <div class="note-body">{{ note.content }}</div>
              </div>
            </div>
          </van-tab>
        </van-tabs>
      </div>
    </van-popup>
  </div>
</template>

<style scoped>
.read-page { min-height: 100vh; transition: background 0.3s, color 0.3s; }

/* Themes */
.theme-default { background: #fdfcf8; color: #2c2925; }
.theme-green { background: #dcedc8; color: #2e4a2d; }
.theme-dark { background: #181a1b; color: #d0d0d0; }
.theme-high-contrast { background: #000; color: #fff; }

.theme-dark .read-nav { background: rgba(24,26,27,0.9) !important; color: #e8e8e8; }
.theme-dark .bottom-bar { background: rgba(24,26,27,0.95) !important; color: #ccc; }

.read-nav {
  background: rgba(253,252,248,0.9) !important;
  backdrop-filter: blur(12px); -webkit-backdrop-filter: blur(12px);
}

.read-body {
  padding: 16px 18px 80px;
  max-width: 100%;
}
.chapter-title {
  text-align: center; font-size: 22px; margin-bottom: 28px;
  font-family: var(--font-serif); font-weight: 700;
}
.text-line {
  text-indent: 2em; text-align: justify; margin-bottom: 16px;
  font-family: var(--font-serif);
  user-select: text; -webkit-user-select: text;
  transition: background 0.2s;
  padding: 4px 0;
  border-radius: 4px;
}
.text-line:active { background: rgba(139,111,82,0.05); }

.chapter-nav { display: flex; justify-content: center; gap: 20px; margin-top: 40px; padding: 16px 0; }

/* Bottom Bar */
.bottom-bar {
  position: fixed; bottom: 0; left: 0; right: 0; z-index: 200;
  display: flex; justify-content: space-around; align-items: center;
  height: 56px;
  background: rgba(255,253,249,0.95); backdrop-filter: blur(12px);
  border-top: 1px solid var(--color-border-light);
  padding-bottom: var(--safe-bottom);
}
.bar-item {
  display: flex; flex-direction: column; align-items: center; gap: 2px;
  font-size: 10px; color: var(--color-text-muted); cursor: pointer;
  transition: color 0.2s;
}
.bar-item:active { color: var(--color-primary); }

/* Catalog */
.catalog-popup { padding: 20px 16px; height: 100%; overflow-y: auto; }
.catalog-title { font-size: 18px; font-weight: 700; margin-bottom: 16px; font-family: var(--font-serif); }
.catalog-item { padding: 12px 8px; border-bottom: 1px solid var(--color-border-light); font-size: 14px; border-radius: 6px; }
.catalog-item.active { color: var(--color-primary); font-weight: 600; background: rgba(139,111,82,0.06); }

/* Settings */
.settings-popup { padding: 24px 20px 40px; }
.settings-popup h3 { margin-bottom: 20px; font-family: var(--font-serif); }
.s-group { margin-bottom: 20px; }
.s-label { font-size: 14px; font-weight: 600; margin-bottom: 10px; }
.theme-row { display: grid; grid-template-columns: repeat(4, 1fr); gap: 8px; }
.t-btn { text-align: center; padding: 10px; border-radius: 8px; font-size: 13px; cursor: pointer; border: 2px solid transparent; transition: 0.2s; }
.t-btn.sel { border-color: var(--color-primary); }
.t-default { background: #fdfcf8; color: #2c2925; }
.t-green { background: #dcedc8; color: #2e4a2d; }
.t-dark { background: #181a1b; color: #d0d0d0; }
.t-high { background: #000; color: #fff; }

/* AI Popup */
.ai-popup { height: 100%; display: flex; flex-direction: column; }
.ai-popup .van-tabs { flex: 1; display: flex; flex-direction: column; }
.ai-popup :deep(.van-tabs__content) { flex: 1; overflow: hidden; display: flex; flex-direction: column; }
.ai-popup :deep(.van-tab__panel) { flex: 1; display: flex; flex-direction: column; overflow: hidden; }

.chat-box { flex: 1; overflow-y: auto; padding: 16px; }
.chat-empty { text-align: center; padding: 40px 0; color: var(--color-text-muted); }
.chat-row { margin-bottom: 16px; }
.chat-row.mine { text-align: right; }
.chat-bubble {
  display: inline-block; max-width: 85%; padding: 10px 14px;
  border-radius: 14px; font-size: 14px; line-height: 1.6;
  text-align: left; white-space: pre-wrap; word-break: break-all;
}
.mine .chat-bubble { background: var(--color-primary); color: #fff; border-bottom-right-radius: 4px; }
.theirs .chat-bubble { background: var(--color-bg-warm); color: var(--color-text); border-bottom-left-radius: 4px; }
.bubble-action { font-size: 11px; color: var(--color-text-muted); margin-top: 4px; cursor: pointer; }
.chat-input { padding: 8px 12px; border-top: 1px solid var(--color-border-light); background: var(--color-bg-card); }

.note-list { padding: 16px; overflow-y: auto; flex: 1; }
.note-header { display: flex; justify-content: space-between; margin-bottom: 8px; }
.note-time { font-size: 12px; color: var(--color-text-muted); }
.note-quote { font-size: 13px; color: var(--color-text-secondary); background: var(--color-bg-warm); padding: 6px 10px; border-radius: 6px; margin-bottom: 8px; border-left: 3px solid var(--color-primary); }
.note-body { font-size: 14px; line-height: 1.6; white-space: pre-wrap; }
.ai-inline-actions {
  position: fixed; bottom: 65px; left: 50%; transform: translateX(-50%); z-index: 300;
  background: rgba(44, 41, 37, 0.95); backdrop-filter: blur(8px);
  padding: 10px 16px; border-radius: 24px;
  display: flex; gap: 16px; box-shadow: 0 4px 12px rgba(0,0,0,0.15);
  transition: opacity 0.3s; pointer-events: auto; white-space: nowrap;
}
.theme-dark .ai-inline-actions { background: rgba(220, 220, 220, 0.95); color: #000; }
.ai-btn { color: #fff; font-size: 13px; font-weight: 600; cursor: pointer; }
.theme-dark .ai-btn { color: #333; }

/* Paragraph Comment Drawer */
.p-comment-drawer { display: flex; flex-direction: column; height: 100%; border-radius: 12px 12px 0 0; }
.p-drawer-tools { display: flex; justify-content: space-between; align-items: center; padding: 14px 16px; border-bottom: 1px solid var(--color-border-light); }
.p-quote { padding: 12px 16px; background: var(--color-bg-warm); color: var(--color-text-secondary); font-size: 13px; font-style: italic; }
.p-comment-list { flex: 1; overflow-y: auto; padding: 16px; }
.p-comment-item { display: flex; gap: 12px; margin-bottom: 16px; }
.p-avatar { width: 36px; height: 36px; flex-shrink: 0; }
.p-content { flex: 1; }
.p-name { font-size: 13px; font-weight: 600; color: var(--color-text-muted); margin-bottom: 6px; display: flex; justify-content: space-between; }
.p-text { font-size: 14px; color: var(--color-text); line-height: 1.5; }
.p-input-box { padding: 10px 16px calc(10px + var(--safe-bottom)); border-top: 1px solid var(--color-border); display: flex; align-items: flex-end; gap: 10px; background: var(--color-bg); }
</style>
