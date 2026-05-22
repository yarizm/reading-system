import { ref } from 'vue'
import request from '../utils/request'
import { showToast, showSuccessToast, showFailToast } from 'vant'

export function useMobileShare(bookId, userInfo) {
  const shareFriends = ref([])
  const isLoadingShareFriends = ref(false)
  const showSharePopup = ref(false)
  const shareMode = ref('book')
  const selectedShareFriendId = ref(null)
  const shareMessage = ref('')
  const isSubmittingShare = ref(false)
  const shareParagraphIndex = ref(-1)

  const loadShareFriends = async () => {
    if (!userInfo.value?.id) {
      showToast('请先登录')
      return false
    }
    isLoadingShareFriends.value = true
    try {
      const res = await request.get(`/api/friend/list/${userInfo.value.id}`)
      shareFriends.value = res.data.data || []
      return true
    } catch (error) {
      shareFriends.value = []
      showFailToast('获取好友列表失败，请稍后再试')
      return false
    } finally {
      isLoadingShareFriends.value = false
    }
  }

  const openSharePopup = async (mode = 'book', index = -1) => {
    shareMode.value = mode
    shareParagraphIndex.value = index
    selectedShareFriendId.value = null
    shareMessage.value = ''

    const loaded = await loadShareFriends()
    if (!loaded) return
    showSharePopup.value = true
  }

  const submitShare = async (chapterIndex, selectedParagraphText) => {
    if (!selectedShareFriendId.value) {
      showToast('请选择要分享的好友')
      return
    }

    isSubmittingShare.value = true
    try {
      if (shareMode.value === 'book') {
        const res = await request.post('/api/bookShare/send', {
          senderId: userInfo.value.id,
          receiverId: selectedShareFriendId.value,
          bookId: Number(bookId),
          message: shareMessage.value.trim()
        })
        if (res.data.code === '200') {
          showSuccessToast('书籍已分享给好友')
          showSharePopup.value = false
        } else {
          showToast(res.data.msg || '书籍分享失败')
        }
      } else if (shareMode.value === 'paragraph') {
        if (!selectedParagraphText) {
          showToast('当前段落内容为空，暂时无法分享')
          isSubmittingShare.value = false
          return
        }
        const res = await request.post('/api/paragraphShare/send', {
          senderId: userInfo.value.id,
          receiverId: selectedShareFriendId.value,
          bookId: Number(bookId),
          chapterIndex: chapterIndex,
          paragraphIndex: shareParagraphIndex.value,
          quote: selectedParagraphText,
          message: shareMessage.value.trim()
        })
        if (res.data.code === '200') {
          showSuccessToast('段落已分享给好友')
          showSharePopup.value = false
        } else {
          showToast(res.data.msg || '段落分享失败')
        }
      }
    } catch (error) {
      showFailToast('分享失败')
    } finally {
      isSubmittingShare.value = false
    }
  }

  const submitAudioShare = async (audioPlayback) => {
    if (!selectedShareFriendId.value) {
      showToast('请选择要分享的好友')
      return false
    }

    isSubmittingShare.value = true
    try {
      const res = await request.post('/api/audioShare/send', {
        senderId: userInfo.value.id,
        receiverId: selectedShareFriendId.value,
        bookId: Number(bookId),
        chapterIndex: audioPlayback.chapterIndex,
        paragraphIndex: audioPlayback.paragraphIndex,
        audioUrl: audioPlayback.audioUrl,
        title: audioPlayback.title,
        message: shareMessage.value.trim()
      })
      if (res.data.code === '200') {
        showSuccessToast('音频已分享给好友')
        return true
      } else {
        showToast(res.data.msg || '音频分享失败')
        return false
      }
    } catch (error) {
      showFailToast('音频分享失败')
      return false
    } finally {
      isSubmittingShare.value = false
    }
  }

  return {
    shareFriends, isLoadingShareFriends, showSharePopup, shareMode,
    selectedShareFriendId, shareMessage, isSubmittingShare, shareParagraphIndex,
    loadShareFriends, openSharePopup, submitShare, submitAudioShare
  }
}
