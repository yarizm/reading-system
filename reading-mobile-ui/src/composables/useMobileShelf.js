import { ref } from 'vue'
import request from '../utils/request'
import { showToast, showSuccessToast, showFailToast, showConfirmDialog } from 'vant'

export function useMobileShelf(bookId, userInfo) {
  const isAddedToShelf = ref(false)

  const checkShelfStatus = async () => {
    if (!userInfo.value?.id) return
    try {
      const res = await request.get(`/api/bookshelf/list/${userInfo.value.id}`)
      if (res.data.code === '200') {
        isAddedToShelf.value = res.data.data.some(item => item.bookId === parseInt(bookId))
      }
    } catch (error) {
      console.error('检查书架状态失败', error)
    }
  }

  const toggleShelf = async () => {
    if (!userInfo.value?.id) return showToast('请先登录')

    if (isAddedToShelf.value) {
      showConfirmDialog({
        title: '提示',
        message: '确定要取消收藏吗？阅读进度将不再保存。'
      }).then(async () => {
        try {
          await request.delete('/api/bookshelf/removeByBook', {
            params: { userId: userInfo.value.id, bookId: bookId }
          })
          showSuccessToast('已取消收藏')
          isAddedToShelf.value = false
        } catch(e) { showFailToast('操作失败') }
      }).catch(() => {})
    } else {
      try {
        const res = await request.post('/api/bookshelf/add', {
          userId: userInfo.value.id, bookId: bookId
        })
        if (res.data.code === '200') {
          showSuccessToast('已加入书架')
          isAddedToShelf.value = true
        } else {
          showFailToast(res.data.msg || '加入失败')
        }
      } catch (e) { showFailToast('加入书架失败') }
    }
  }

  return { isAddedToShelf, checkShelfStatus, toggleShelf }
}
