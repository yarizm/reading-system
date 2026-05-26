<template>
  <div class="mobile-agent-guide" v-if="authStore.isLoggedIn">
    <!-- 悬浮按钮 -->
    <div class="agent-toggle" @click="show = true">
      <van-icon name="chat" size="24" color="#fff" />
    </div>

    <!-- 向导面板 -->
    <van-popup
      v-model:show="show"
      position="bottom"
      :style="{ height: '85%' }"
      round
    >
      <div class="drawer-header">
        <div class="drawer-title">系统智能向导</div>
        <van-icon name="cross" class="close-btn" @click="show = false" />
      </div>

      <div class="chat-layout">
        <div class="chat-history-box" id="mobile-guide-chat-box">
          <van-empty v-if="chatList.length === 0" description="您好，我是系统向导，有什么可以帮您？">
            <div class="quick-questions">
              <van-button size="small" round type="primary" plain @click="sendChat('我这周读了多少内容？')">本周阅读统计</van-button>
              <van-button size="small" round type="primary" plain @click="sendChat('怎么创建阅读计划？')">如何制定计划</van-button>
            </div>
          </van-empty>

          <div
            v-for="(msg, index) in chatList"
            :key="index"
            class="chat-row"
            :class="msg.role === 'user' ? 'row-right' : 'row-left'"
          >
            <div class="bubble-wrapper">
              <div class="bubble-content markdown-body" v-html="renderMarkdown(msg.content.replace(/\[ACTION:.*?\]/g, ''))"></div>
              
              <div class="action-cards" v-if="msg.actions && msg.actions.length > 0">
                <van-button 
                  v-for="(action, aIdx) in msg.actions" 
                  :key="aIdx" 
                  type="primary" 
                  size="small" 
                  plain
                  block
                  class="action-btn"
                  @click="executeAction(action)"
                >
                  {{ action.label }}
                </van-button>
              </div>
            </div>
          </div>
        </div>

        <div class="chat-input-area">
          <van-field
            v-model="inputMessage"
            center
            clearable
            placeholder="有什么可以帮您？"
            :disabled="isThinking"
          >
            <template #button>
              <van-button size="small" type="primary" :loading="isThinking" @click="sendChat(null)">发送</van-button>
            </template>
          </van-field>
        </div>
      </div>
    </van-popup>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { getAuthHeaders } from '../utils/authHeaders'
import { useAuthStore } from '../stores/auth'
import { showToast } from 'vant'

marked.setOptions({ breaks: true, gfm: true })
const renderMarkdown = (text) => DOMPurify.sanitize(marked.parse(text || ''))

const authStore = useAuthStore()
const router = useRouter()

const show = ref(false)
const chatList = ref([])
const inputMessage = ref('')
const isThinking = ref(false)
const currentConversationId = ref('')

const scrollToBottom = () => {
  nextTick(() => {
    const box = document.getElementById('mobile-guide-chat-box')
    if (box) {
      box.scrollTop = box.scrollHeight
    }
  })
}

const parseActions = (text) => {
  const actions = []
  const regex = /\[ACTION:([^:]+):([^:]+):([^\]]+)\]/g
  let match
  while ((match = regex.exec(text)) !== null) {
    actions.push({ type: match[1], payload: match[2], label: match[3] })
  }
  return actions
}

const executeAction = (action) => {
  if (action.type === 'navigate') {
    router.push(action.payload)
    show.value = false
  } else if (action.type === 'api_call') {
    showToast('触发操作: ' + action.label)
  }
}

const sendChat = async (textOverride = null) => {
  const msg = textOverride || inputMessage.value
  if (!msg || !msg.trim() || isThinking.value) return

  chatList.value.push({ role: 'user', content: msg })
  inputMessage.value = ''
  isThinking.value = true
  scrollToBottom()

  const aiMsgIndex = chatList.value.push({ role: 'ai', content: '', actions: [] }) - 1

  try {
    await fetchEventSource('/api/guide/chat', {
      method: 'POST',
      headers: {
        ...getAuthHeaders(),
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream'
      },
      body: JSON.stringify({
        query: msg,
        conversationId: currentConversationId.value
      }),
      onmessage(event) {
        const dataJson = JSON.parse(event.data)
        if (dataJson.event === 'message') {
          chatList.value[aiMsgIndex].content += (dataJson.answer || '')
          chatList.value[aiMsgIndex].actions = parseActions(chatList.value[aiMsgIndex].content)
          scrollToBottom()
          if (dataJson.conversation_id) {
            currentConversationId.value = dataJson.conversation_id
          }
        } else if (dataJson.event === 'message_end' || dataJson.event === 'workflow_finished') {
          isThinking.value = false
          scrollToBottom()
        }
      },
      onclose() {
        isThinking.value = false
        scrollToBottom()
      },
      onerror(err) {
        console.error(err)
        isThinking.value = false
        throw err
      }
    })
  } catch (e) {
    isThinking.value = false
  }
}
</script>

<style scoped>
.agent-toggle {
  position: fixed;
  bottom: 80px;
  right: 20px;
  width: 48px;
  height: 48px;
  border-radius: 24px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  box-shadow: 0 4px 12px rgba(118, 75, 162, 0.4);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 99;
}

.drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 15px;
  border-bottom: 1px solid #f5f5f5;
}
.theme-dark .drawer-header { border-bottom-color: #333; }
.drawer-title { font-weight: bold; font-size: 16px; }
.close-btn { font-size: 20px; color: #999; padding: 5px; }

.chat-layout { display: flex; flex-direction: column; height: calc(85vh - 60px); }
.chat-history-box { flex: 1; overflow-y: auto; padding: 15px; background: #f9f9f9; }
.theme-dark .chat-history-box { background: #111; }

.quick-questions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
  margin-top: 15px;
}

.chat-row { display: flex; margin-bottom: 15px; }
.row-left { justify-content: flex-start; }
.row-right { justify-content: flex-end; }
.bubble-wrapper { max-width: 85%; }
.bubble-content { padding: 10px 14px; border-radius: 12px; font-size: 14px; line-height: 1.5; word-break: break-word; }
.row-left .bubble-content { background-color: #fff; color: #333; box-shadow: 0 2px 8px rgba(0,0,0,0.04); }
.theme-dark .row-left .bubble-content { background-color: #2b2b2b; color: #eee; }
.row-right .bubble-content { background-color: #667eea; color: #fff; }

.action-cards {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.action-btn { margin-top: 5px; }

:deep(.markdown-body) {
  font-size: 14px;
  line-height: 1.6;
}
</style>
