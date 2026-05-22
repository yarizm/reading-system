import { ref } from 'vue'
import request from '../utils/request'
import { ElMessage } from 'element-plus'

export function useShare(bookId, userInfo) {
  const shareFriendOptions = ref([])
  const isLoadingShareFriends = ref(false)

  const showBookShareDialog = ref(false)
  const bookShareFriendId = ref(null)
  const bookShareMessage = ref('')
  const isSubmittingBookShare = ref(false)

  const showParagraphShareDialog = ref(false)
  const paragraphShareFriendId = ref(null)
  const paragraphShareMessage = ref('')
  const paragraphShareIndex = ref(-1)
  const isSubmittingParagraphShare = ref(false)

  const audioShareFriendId = ref(null)
  const audioShareMessage = ref('')
  const isSubmittingAudioShare = ref(false)

  const ensureReady = () => {
    if (!userInfo.value?.id) {
      ElMessage.warning('请先登录')
      return false
    }
    return true
  }

  const loadShareFriends = async () => {
    isLoadingShareFriends.value = true
    try {
      const res = await request.get(`/api/friend/list/${userInfo.value.id}`)
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
    if (!ensureReady()) return
    bookShareFriendId.value = null
    bookShareMessage.value = ''
    const loaded = await loadShareFriends()
    if (!loaded) return
    showBookShareDialog.value = true
  }

  const openParagraphShareDialog = async (index) => {
    if (!ensureReady()) return
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
      const res = await request.post('/api/bookShare/send', {
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

  const submitParagraphShare = async (chapterIndex, selectedParagraphText) => {
    if (!paragraphShareFriendId.value) {
      ElMessage.warning('请选择要分享的好友')
      return
    }
    if (!selectedParagraphText) {
      ElMessage.warning('当前段落内容为空，暂时无法分享')
      return
    }
    isSubmittingParagraphShare.value = true
    try {
      const res = await request.post('/api/paragraphShare/send', {
        senderId: userInfo.value.id,
        receiverId: paragraphShareFriendId.value,
        bookId: Number(bookId),
        chapterIndex: chapterIndex,
        paragraphIndex: paragraphShareIndex.value,
        quote: selectedParagraphText,
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

  const submitAudioShare = async (audioPlayback) => {
    if (!audioShareFriendId.value) {
      ElMessage.warning('请选择要分享的好友')
      return
    }
    if (!audioPlayback?.audioUrl) {
      ElMessage.warning('当前没有可分享的音频')
      return
    }
    isSubmittingAudioShare.value = true
    try {
      const res = await request.post('/api/audioShare/send', {
        senderId: userInfo.value.id,
        receiverId: audioShareFriendId.value,
        bookId: Number(bookId),
        chapterIndex: audioPlayback.chapterIndex,
        paragraphIndex: audioPlayback.paragraphIndex,
        audioUrl: audioPlayback.audioUrl,
        title: audioPlayback.title,
        message: audioShareMessage.value.trim()
      })
      if (res.data.code === '200') {
        ElMessage.success('音频已分享给好友')
      } else {
        ElMessage.warning(res.data.msg || '音频分享失败')
      }
    } catch (error) {
      ElMessage.error('音频分享失败')
    } finally {
      isSubmittingAudioShare.value = false
    }
  }

  return {
    shareFriendOptions, isLoadingShareFriends,
    showBookShareDialog, bookShareFriendId, bookShareMessage, isSubmittingBookShare,
    showParagraphShareDialog, paragraphShareFriendId, paragraphShareMessage, paragraphShareIndex, isSubmittingParagraphShare,
    audioShareFriendId, audioShareMessage, isSubmittingAudioShare,
    loadShareFriends, openBookShareDialog, openParagraphShareDialog,
    submitBookShare, submitParagraphShare, submitAudioShare
  }
}
