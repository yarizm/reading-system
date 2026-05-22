import { ref } from 'vue'
import request from '../utils/request'
import { showToast, showSuccessToast, showFailToast, showConfirmDialog } from 'vant'

export function useMobileComments(bookId, userInfo) {
  const showParagraphDrawer = ref(false)
  const paragraphComments = ref([])
  const newParagraphComment = ref('')
  const isLoadingComments = ref(false)
  const isSubmittingComment = ref(false)

  const fetchParagraphComments = async (chapterIndex, paragraphIndex) => {
    isLoadingComments.value = true
    try {
      const res = await request.get(`/api/paragraphComment/list/${bookId}/${chapterIndex}/${paragraphIndex}`, {
        params: { currentUserId: userInfo.value?.id }
      })
      if (res.data.code === '200') {
        paragraphComments.value = res.data.data
      } else {
        paragraphComments.value = []
      }
    } catch (error) {
      paragraphComments.value = []
      showFailToast('获取评论失败')
    } finally {
      isLoadingComments.value = false
    }
  }

  const submitParagraphComment = async (chapterIndex, quoteText) => {
    if (!userInfo.value?.id) return showToast('请先登录')
    if (!newParagraphComment.value.trim()) return showToast('评论内容不能为空')

    isSubmittingComment.value = true
    try {
      const res = await request.post('/api/paragraphComment/add', {
        userId: userInfo.value.id,
        bookId: bookId,
        chapterIndex: chapterIndex,
        paragraphIndex: -1, // updated by caller if needed
        content: newParagraphComment.value,
        quote: quoteText.substring(0, 50) + '...'
      })
      if (res.data.code === '200') {
        showSuccessToast('评论成功')
        newParagraphComment.value = ''
        fetchParagraphComments(chapterIndex, -1)
      } else {
        showFailToast(res.data.msg || '评论失败')
      }
    } catch (error) {
      showFailToast('评论提交失败')
    } finally {
      isSubmittingComment.value = false
    }
  }

  const deleteComment = (commentId, chapterIndex) => {
    showConfirmDialog({
      title: '提示',
      message: '确定删除这条评论吗？'
    }).then(async () => {
      await request.delete(`/api/paragraphComment/${commentId}`, {
        params: { userId: userInfo.value.id }
      })
      showSuccessToast('已删除')
      fetchParagraphComments(chapterIndex, -1)
    }).catch(() => {})
  }

  const toggleLike = async (comment) => {
    if (!userInfo.value?.id) return showToast('请先登录')
    try {
      const res = await request.post('/api/paragraphComment/like', {
        commentId: comment.id,
        userId: userInfo.value.id
      })
      if (res.data.code === '200') {
        comment.likeCount = res.data.data.likeCount
        comment.isLiked = res.data.data.isLiked
      }
    } catch (e) { showFailToast('操作失败') }
  }

  return {
    showParagraphDrawer, paragraphComments, newParagraphComment, isLoadingComments, isSubmittingComment,
    fetchParagraphComments, submitParagraphComment, deleteComment, toggleLike
  }
}
