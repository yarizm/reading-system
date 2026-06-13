import { ref, nextTick } from 'vue'
import request from '../utils/request'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { getAuthHeaders } from '../utils/authHeaders'
import { parseSseJsonEvent } from '../utils/sseEvents'
import { showToast, showFailToast, showSuccessToast } from 'vant'

export function useMobileAI(bookId, userInfo, bookInfo, selectedText) {
  const activeAiTab = ref('ai')
  const noteList = ref([])
  const showAiDrawer = ref(false)
  const chatList = ref([])
  const inputMessage = ref('')
  const isThinking = ref(false)
  const currentConversationId = ref('')
  const tagList = ref([])

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
    if (!userId) { showToast('请先登录'); return }

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
          const dataJson = parseSseJsonEvent(event, 'AI SSE')
          if (!dataJson) return
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
    if (!userInfo.value?.id) return showToast('请先登录')
    const quote = selectedText.value || (msgContent.substring(0, 15) + '...')
    try {
      await request.post('/api/sysNote/add', {
        userId: userInfo.value.id, bookId, selectedText: quote, content: msgContent
      })
      showSuccessToast('已保存笔记')
      fetchNotes()
    } catch (e) { showFailToast('保存失败') }
  }

  const handleDeleteNote = async (id) => {
    await request.delete(`/api/sysNote/${id}`)
    fetchNotes()
  }

  const fetchTags = async () => {
    try {
      const res = await request.get('/api/tag/list')
      if (res.data.code === '200') {
        tagList.value = res.data.data || []
      }
    } catch (e) {
      console.error(e)
    }
  }

  const bindNoteTag = async (noteId, tagId) => {
    try {
      const existingTags = noteList.value.find(n => n.id === noteId)?.tags || []
      const tagIds = [...existingTags.map(t => t.id), tagId]
      await request.post('/api/tag/bind', { noteId, tagIds })
      fetchNotes()
    } catch (e) {
      console.error(e)
    }
  }

  const unbindNoteTag = async (noteId, tagId) => {
    try {
      await request.delete('/api/tag/unbind', { data: { noteId, tagIds: [tagId] } })
      fetchNotes()
    } catch (e) {
      console.error(e)
    }
  }

  return {
    activeAiTab, noteList, showAiDrawer,
    chatList, inputMessage, isThinking, currentConversationId,
    sendChat, fetchNotes, saveNote, handleDeleteNote,
    tagList, fetchTags, bindNoteTag, unbindNoteTag
  }
}
