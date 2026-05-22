import { ref } from 'vue'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

export function useComments(bookId, userInfo) {
  const showParagraphCommentsDrawer = ref(false)
  const selectedParagraphIndex = ref(-1)
  const paragraphComments = ref([])
  const newParagraphComment = ref('')
  const isLoadingComments = ref(false)
  const isSubmittingComment = ref(false)

  const showMyCommentsDrawer = ref(false)
  const myCommentsList = ref([])

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
      ElMessage.error('获取评论失败')
    } finally {
      isLoadingComments.value = false
    }
  }

  const submitParagraphComment = async (chapterIndex, quoteText) => {
    if (!userInfo.value?.id) return ElMessage.warning('请先登录')
    if (!newParagraphComment.value.trim()) return ElMessage.warning('评论内容不能为空')

    isSubmittingComment.value = true
    try {
      const res = await request.post('/api/paragraphComment/add', {
        userId: userInfo.value.id,
        bookId: bookId,
        chapterIndex: chapterIndex,
        paragraphIndex: selectedParagraphIndex.value,
        content: newParagraphComment.value,
        quote: quoteText.substring(0, 50) + '...'
      })
      if (res.data.code === '200') {
        ElMessage.success('评论成功')
        newParagraphComment.value = ''
        fetchParagraphComments(chapterIndex, selectedParagraphIndex.value)
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

  const deleteComment = (commentId, chapterIndex) => {
    ElMessageBox.confirm('确定删除这条评论吗？', '提示', { type: 'warning' })
      .then(async () => {
        await request.delete(`/api/paragraphComment/${commentId}`, {
          params: { userId: userInfo.value.id }
        })
        ElMessage.success('已删除')
        fetchParagraphComments(chapterIndex, selectedParagraphIndex.value)
        if (showMyCommentsDrawer.value) fetchMyComments()
      }).catch(() => {})
  }

  const toggleLike = async (comment) => {
    if (!userInfo.value?.id) return ElMessage.warning('请先登录')
    try {
      const res = await request.post('/api/paragraphComment/like', {
        commentId: comment.id,
        userId: userInfo.value.id
      })
      if (res.data.code === '200') {
        comment.likeCount = res.data.data.likeCount
        comment.isLiked = res.data.data.isLiked
      }
    } catch (e) { ElMessage.error('操作失败') }
  }

  const openMyComments = async () => {
    showMyCommentsDrawer.value = true
    fetchMyComments()
  }

  const fetchMyComments = async () => {
    if (!userInfo.value?.id) return
    const res = await request.get(`/api/paragraphComment/my/${bookId}/${userInfo.value.id}`)
    if (res.data.code === '200') myCommentsList.value = res.data.data
  }

  return {
    showParagraphCommentsDrawer, selectedParagraphIndex, paragraphComments, newParagraphComment, isLoadingComments, isSubmittingComment,
    showMyCommentsDrawer, myCommentsList,
    fetchParagraphComments, submitParagraphComment, deleteComment, toggleLike, openMyComments, fetchMyComments
  }
}
