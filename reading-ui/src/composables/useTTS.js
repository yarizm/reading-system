import { ref, reactive, computed, nextTick } from 'vue'
import request from '../utils/request'
import { ElMessage } from 'element-plus'
import { withFileAccessToken } from '../utils/authHeaders'

export function useTTS() {
  const currentAudio = ref(null)
  const currentChapterAudio = ref(null)
  const isGeneratingTts = ref(false)
  const isChapterPlaying = ref(false)
  const audioPlayerVisible = ref(false)
  const audioShareDialogVisible = ref(false)
  const isAudioLoading = ref(false)
  const isAudioPlaying = ref(false)
  const audioCurrentTime = ref(0)
  const audioDuration = ref(0)
  
  const audioPlayback = reactive({
    audioUrl: '',
    title: '',
    sourceType: 'paragraph',
    bookId: null,
    chapterId: null,
    chapterIndex: null,
    paragraphIndex: null
  })

  const playableAudioUrl = computed(() => withFileAccessToken(audioPlayback.audioUrl))

  const audioSourceLabel = computed(() => {
    if (audioPlayback.sourceType === 'chapter' && audioPlayback.chapterIndex !== null) {
      return `第 ${audioPlayback.chapterIndex + 1} 章`
    }
    if (audioPlayback.sourceType === 'paragraph' && audioPlayback.paragraphIndex !== null) {
      return `第 ${audioPlayback.paragraphIndex + 1} 段`
    }
    return '朗读音频'
  })

  const getRequestErrorMessage = (error, fallback) => {
    const data = error?.response?.data
    return data?.msg || data?.detail || error?.message || fallback
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
      const res = await request.post('/api/ai/audio/generate', payload)
      if (res.data.code !== '200' || !res.data.data?.audioUrl) {
        ElMessage.error(res.data.msg || '当前听书服务不可用，请稍后再试')
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
      ElMessage.error(getRequestErrorMessage(error, '当前听书服务不可用，请稍后再试'))
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
    const ext = audioPlayback.audioUrl.split('.').pop()?.split('?')[0] || 'mp3'
    const link = document.createElement('a')
    link.href = playableAudioUrl.value
    link.download = `${(audioPlayback.title || '朗读音频').replace(/[\\/:*?"<>|]/g, '_')}.${ext}`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }

  return {
    currentAudio, isGeneratingTts, isChapterPlaying, audioPlayerVisible, audioShareDialogVisible,
    isAudioLoading, isAudioPlaying, audioCurrentTime, audioDuration, audioPlayback,
    playableAudioUrl, audioSourceLabel,
    stopAudioPlayback, handleAudioLoadedMetadata, handleAudioTimeUpdate, handleAudioPlay, handleAudioPause, handleAudioEnded,
    openAudioPlayer, closeAudioPlayer, toggleDialogAudioPlayback, formatAudioTime, downloadCurrentAudio
  }
}
