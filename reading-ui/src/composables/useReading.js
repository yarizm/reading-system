import { ref, nextTick } from 'vue'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

export function useReading(bookId, userInfo, route) {
  const bookInfo = ref({ title: '阅读', author: '', coverUrl: '', description: '', category: '' })
  const lines = ref([])
  const currentLine = ref(0)
  const catalog = ref([])
  const chapterIndex = ref(0)
  const currentChapterTitle = ref('')
  const isLoading = ref(false)
  
  // UI states
  const showCatalog = ref(false)
  const showBookInfoDialog = ref(false)
  
  let observer = null

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

  const applyRouteReadTarget = (selectedParagraphIndexRef) => {
    const target = getRouteReadTarget()
    if (target.chapterIndex === null && target.paragraphIndex === null) return

    if (target.chapterIndex !== null && catalog.value.length > 0) {
      chapterIndex.value = Math.min(Math.max(target.chapterIndex, 0), catalog.value.length - 1)
    }
    if (target.paragraphIndex !== null && selectedParagraphIndexRef) {
      const safeParagraph = Math.max(target.paragraphIndex, 0)
      currentLine.value = safeParagraph
      selectedParagraphIndexRef.value = safeParagraph
    }
  }

  const loadBookInfo = async () => {
    try {
      const infoRes = await request.get(`/api/sysBook/${bookId}`)
      if (infoRes.data.code === '200') {
        bookInfo.value = infoRes.data.data
      }
    } catch (err) {
      console.error(err)
      ElMessage.error('获取书籍信息失败')
    }
  }

  const loadCatalog = async () => {
    try {
      const res = await request.get(`/api/sysBook/catalog/${bookId}`)
      if (res.data.code === '200') {
        catalog.value = res.data.data
        if (catalog.value.length === 0) {
          ElMessage.warning('该书暂无章节信息，正在尝试自动解析...')
          await request.post(`/api/sysBook/analyze/${bookId}`)
          const retryRes = await request.get(`/api/sysBook/catalog/${bookId}`)
          catalog.value = retryRes.data.data
        }
      }
    } catch (e) {
      console.error('加载目录失败', e)
    }
  }

  const loadProgress = async () => {
    if (!userInfo.value || !userInfo.value.id) return
    try {
      const res = await request.get('/api/bookshelf/detail', {
        params: { userId: userInfo.value.id, bookId: bookId }
      })
      if (res.data.data) {
        chapterIndex.value = res.data.data.currentChapterIndex || 0
        currentLine.value = res.data.data.progressIndex || 0
      }
    } catch (e) { console.error(e) }
  }

  const saveProgress = async () => {
    if (!userInfo.value || !userInfo.value.id) return
    try {
      await request.post('/api/bookshelf/updateProgress', {
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

  const loadCurrentChapter = async (selectedParagraphIndexRef, stopAudioPlaybackCallback) => {
    if (catalog.value.length === 0) return

    isLoading.value = true
    if (selectedParagraphIndexRef) selectedParagraphIndexRef.value = -1

    if (stopAudioPlaybackCallback) stopAudioPlaybackCallback()

    const chapterId = catalog.value[chapterIndex.value].id
    currentChapterTitle.value = catalog.value[chapterIndex.value].title

    try {
      const res = await request.get(`/api/sysBook/chapter/${chapterId}`)
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
          if (target.paragraphIndex !== null && selectedParagraphIndexRef) {
            const safeParagraph = Math.max(target.paragraphIndex, 0)
            selectedParagraphIndexRef.value = safeParagraph
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

  const changeChapter = (offset, selectedParagraphIndexRef, stopAudioPlaybackCallback) => {
    const newIndex = chapterIndex.value + offset
    if (newIndex >= 0 && newIndex < catalog.value.length) {
      saveProgress()
      chapterIndex.value = newIndex
      currentLine.value = 0
      loadCurrentChapter(selectedParagraphIndexRef, stopAudioPlaybackCallback)
    }
  }

  const jumpToChapter = (index, selectedParagraphIndexRef, stopAudioPlaybackCallback) => {
    saveProgress()
    chapterIndex.value = index
    currentLine.value = 0
    loadCurrentChapter(selectedParagraphIndexRef, stopAudioPlaybackCallback)
    showCatalog.value = false
  }

  const disconnectObserver = () => {
    if (observer) observer.disconnect()
  }

  return {
    bookInfo, lines, currentLine, catalog, chapterIndex, currentChapterTitle, isLoading,
    showCatalog, showBookInfoDialog,
    loadBookInfo, loadCatalog, loadProgress, saveProgress, loadCurrentChapter,
    changeChapter, jumpToChapter, applyRouteReadTarget, scrollToLine,
    disconnectObserver
  }
}
