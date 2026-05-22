import { ref, nextTick } from 'vue'
import request from '../utils/request'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { getAuthHeaders } from '../utils/authHeaders'
import { ElMessage } from 'element-plus'

export function useAI(bookId, userInfo, bookInfo, selectedText) {
  const activeTab = ref('ai')
  const noteList = ref([])
  const menuVisible = ref(false)
  const menuStyle = ref({ top: '0px', left: '0px' })
  const drawerVisible = ref(false)
  const drawerWidth = ref(400)
  const drawerDirection = ref('rtl')
  const isResizing = ref(false)
  const chatList = ref([])
  const inputMessage = ref('')
  const isThinking = ref(false)
  const aiTitle = ref('AI 助手')
  const currentConversationId = ref('')

  const scrollToBottom = () => {
    nextTick(() => {
      const box = document.querySelector('.chat-history-box')
      if (box) box.scrollTop = box.scrollHeight
    })
  }

  const sendChat = async (contextText = null, modeOverride = null, displayMsg = null) => {
    const instruction = modeOverride || inputMessage.value
    const textToAnalyze = contextText || selectedText.value || '用户没有提供具体文本，请直接回答用户的问题'
    const messageToShow = displayMsg || inputMessage.value

    if (!instruction || !instruction.trim() || isThinking.value) return

    let userId = userInfo.value?.id
    if (!userId) { ElMessage.warning('请先登录'); return }

    chatList.value.push({ role: 'user', content: messageToShow })
    inputMessage.value = ''
    isThinking.value = true
    scrollToBottom()

    const aiMsgIndex = chatList.value.push({ role: 'ai', content: '' }) - 1

    try {
      await fetchEventSource('/api/difyreading/analyze', {
        method: 'POST',
        headers: {
          ...getAuthHeaders(),
          'Content-Type': 'application/json',
          'Accept': 'text/event-stream'
        },
        body: JSON.stringify({
          text: textToAnalyze,
          mode: instruction,
          conversationId: currentConversationId.value,
          bookName: bookInfo.value.title,
          bookId: Number(bookId)
        }),
        onmessage(event) {
          const dataJson = JSON.parse(event.data)
          if (dataJson.event === 'message') {
            const newText = dataJson.answer || ''
            chatList.value[aiMsgIndex].content += newText
            scrollToBottom()
            if (dataJson.conversation_id) {
              currentConversationId.value = dataJson.conversation_id
            }
          }
          if (dataJson.event === 'error') {
            chatList.value[aiMsgIndex].content += '\n[解析出错]'
          }
        },
        onclose() {
          isThinking.value = false
          scrollToBottom()
        },
        onerror(err) {
          console.error("流式输出异常:", err)
          if (!chatList.value[aiMsgIndex].content) {
            chatList.value[aiMsgIndex].content += '\n[网络异常，连接中断]'
          }
          isThinking.value = false
          throw err
        }
      })
    } catch (error) {
      console.error(error)
      isThinking.value = false
      scrollToBottom()
    }
  }

  const fetchNotes = async () => {
    if (!userInfo.value?.id) return
    try {
      const res = await request.get(`/api/sysNote/list/${bookId}`, { params: { userId: userInfo.value.id } })
      if (res.data.code === '200') noteList.value = res.data.data
    } catch (e) { console.error(e) }
  }

  const saveNote = async (msgContent) => {
    if (!userInfo.value?.id) return ElMessage.warning('请先登录')
    const quote = selectedText.value || (msgContent.substring(0, 15) + '...')
    try {
      await request.post('/api/sysNote/add', {
        userId: userInfo.value.id, bookId, selectedText: quote, content: msgContent
      })
      ElMessage.success('已保存笔记')
      fetchNotes()
    } catch (e) { ElMessage.error('保存失败') }
  }

  const handleDeleteNote = async (id) => {
    await request.delete(`/api/sysNote/${id}`)
    fetchNotes()
  }

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

  const toggleDrawerDirection = () => {
    drawerDirection.value = drawerDirection.value === 'rtl' ? 'ltr' : 'rtl'
  }

  const startResize = (e) => {
    isResizing.value = true
    document.addEventListener('mousemove', handleResize)
    document.addEventListener('mouseup', stopResize)
    e.preventDefault()
  }

  const handleResize = (e) => {
    if (!isResizing.value) return
    const viewportWidth = window.innerWidth
    let newWidth
    if (drawerDirection.value === 'rtl') {
      newWidth = viewportWidth - e.clientX
    } else {
      newWidth = e.clientX
    }
    newWidth = Math.min(800, Math.max(300, newWidth))
    drawerWidth.value = newWidth
  }

  const stopResize = () => {
    isResizing.value = false
    document.removeEventListener('mousemove', handleResize)
    document.removeEventListener('mouseup', stopResize)
  }

  return {
    activeTab, noteList, menuVisible, menuStyle, drawerVisible, drawerWidth, drawerDirection, isResizing,
    chatList, inputMessage, isThinking, aiTitle, currentConversationId,
    sendChat, fetchNotes, saveNote, handleDeleteNote, handleMouseUp, toggleSidebar, toggleDrawerDirection, startResize
  }
}
