import { ref } from 'vue'
import request from '../utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

export function useShelf(bookId, userInfo) {
  const isAddedToShelf = ref(false)
  const isCheckingShelf = ref(false)

  const checkShelfStatus = async () => {
    if (!userInfo.value?.id) return
    isCheckingShelf.value = true
    try {
      const res = await request.get(`/api/bookshelf/list/${userInfo.value.id}`)
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
    if (!userInfo.value?.id) return ElMessage.warning('请先登录')

    if (isAddedToShelf.value) {
      ElMessageBox.confirm('确定要取消收藏吗？阅读进度将不再保存。', '提示', { type: 'warning' })
        .then(async () => {
          try {
            await request.delete('/api/bookshelf/removeByBook', {
              params: { userId: userInfo.value.id, bookId: bookId }
            })
            ElMessage.success('已取消收藏')
            isAddedToShelf.value = false
          } catch(e) { ElMessage.error('操作失败') }
        }).catch(() => {})
    } else {
      try {
        const res = await request.post('/api/bookshelf/add', {
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

  return { isAddedToShelf, isCheckingShelf, checkShelfStatus, toggleShelf }
}
