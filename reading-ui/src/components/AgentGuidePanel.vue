<template>
  <div class="agent-guide-container" :class="{ 'is-active': show }">
    <div class="agent-toggle" @click="toggleGuide">
      <el-icon :size="24" color="#fff"><MagicStick /></el-icon>
    </div>
    
    <div class="agent-panel" v-if="show">
      <div class="panel-header">
        <span class="title"><el-icon><Monitor /></el-icon> 系统智能向导</span>
        <el-button link @click="toggleGuide"><el-icon><Close /></el-icon></el-button>
      </div>
      
      <div class="chat-history-box" ref="chatBox">
        <div v-if="chatList.length === 0" class="empty-state">
          <p>您好！我是系统智能向导，您可以问我关于系统功能、阅读进度或数据洞察的问题。</p>
          <div class="quick-questions">
            <el-button size="small" round @click="sendChat('我这周读了多少内容？')">本周阅读统计</el-button>
            <el-button size="small" round @click="sendChat('怎么创建阅读计划？')">如何制定计划</el-button>
          </div>
        </div>
        
        <div v-for="(msg, index) in chatList" :key="index" class="chat-row" :class="msg.role === 'user' ? 'row-right' : 'row-left'">
          <div class="bubble-wrapper">
            <!-- 渲染普通 markdown -->
            <div class="bubble-content markdown-body" v-html="renderMarkdown(msg.content.replace(/\[ACTION:.*?\]/g, ''))"></div>
            
            <!-- 渲染 action 按钮 -->
            <div class="action-cards" v-if="msg.actions && msg.actions.length > 0">
              <el-button 
                v-for="(action, aIdx) in msg.actions" 
                :key="aIdx" 
                type="primary" 
                size="small" 
                plain
                class="action-btn"
                @click="executeAction(action)"
              >
                {{ action.label }} <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </div>
        </div>
      </div>
      
      <div class="chat-input-area">
        <el-input 
          v-model="inputMessage" 
          placeholder="有什么可以帮您？" 
          :disabled="isThinking" 
          @keyup.enter="sendChat(null)"
        >
          <template #append>
            <el-button @click="sendChat(null)" :loading="isThinking">
              <el-icon><Position /></el-icon>
            </el-button>
          </template>
        </el-input>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, nextTick } from 'vue'
import { MagicStick, Monitor, Close, Position, ArrowRight } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { getAuthHeaders } from '@/utils/authHeaders'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'

marked.setOptions({ breaks: true, gfm: true })
const renderMarkdown = (text) => DOMPurify.sanitize(marked.parse(text || ''))

const show = ref(false)
const chatList = ref([])
const inputMessage = ref('')
const isThinking = ref(false)
const currentConversationId = ref('')
const chatBox = ref(null)
const router = useRouter()

const toggleGuide = () => {
  show.value = !show.value
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatBox.value) {
      chatBox.value.scrollTop = chatBox.value.scrollHeight
    }
  })
}

// 解析文本中的 [ACTION:type:payload:label]
const parseActions = (text) => {
  const actions = []
  const regex = /\[ACTION:([^:]+):([^:]+):([^\]]+)\]/g
  let match
  while ((match = regex.exec(text)) !== null) {
    actions.push({
      type: match[1],
      payload: match[2],
      label: match[3]
    })
  }
  return actions
}

const executeAction = (action) => {
  if (action.type === 'navigate') {
    router.push(action.payload)
    toggleGuide() // 跳转后收起向导
  } else if (action.type === 'api_call') {
    // 待扩展的 api_call 逻辑
    ElMessage.info('触发了操作: ' + action.label)
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
          const newText = dataJson.answer || ''
          chatList.value[aiMsgIndex].content += newText
          chatList.value[aiMsgIndex].actions = parseActions(chatList.value[aiMsgIndex].content)
          scrollToBottom()
          if (dataJson.conversation_id) {
            currentConversationId.value = dataJson.conversation_id
          }
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
.agent-guide-container {
  position: fixed;
  bottom: 30px;
  right: 30px;
  z-index: 2000;
}

.agent-toggle {
  width: 50px;
  height: 50px;
  border-radius: 25px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  box-shadow: 0 4px 15px rgba(118, 75, 162, 0.4);
  display: flex;
  justify-content: center;
  align-items: center;
  cursor: pointer;
  transition: transform 0.3s cubic-bezier(0.175, 0.885, 0.32, 1.275);
}

.agent-toggle:hover {
  transform: scale(1.1);
}

.agent-panel {
  position: absolute;
  bottom: 70px;
  right: 0;
  width: 350px;
  height: 500px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 10px 30px rgba(0,0,0,0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(0,0,0,0.05);
}

.panel-header {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.panel-header .title {
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 8px;
}

.chat-history-box {
  flex: 1;
  overflow-y: auto;
  padding: 15px;
  background: #f9f9f9;
}

.empty-state {
  text-align: center;
  color: #666;
  font-size: 14px;
  padding: 20px 0;
}
.quick-questions {
  margin-top: 15px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

.chat-row { display: flex; margin-bottom: 15px; }
.row-left { justify-content: flex-start; }
.row-right { justify-content: flex-end; }
.bubble-wrapper { max-width: 85%; }
.bubble-content { padding: 10px 14px; border-radius: 12px; font-size: 14px; line-height: 1.5; word-break: break-word; }
.row-left .bubble-content { background-color: #fff; color: #333; box-shadow: 0 2px 8px rgba(0,0,0,0.04); border-top-left-radius: 2px; }
.row-right .bubble-content { background-color: #667eea; color: #fff; border-top-right-radius: 2px; }

.action-cards {
  margin-top: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.action-btn { width: 100%; justify-content: space-between; }

.chat-input-area {
  padding: 12px;
  background: #fff;
  border-top: 1px solid rgba(0,0,0,0.05);
}

:deep(.markdown-body p) { margin-bottom: 8px; }
:deep(.markdown-body *:last-child) { margin-bottom: 0; }
</style>
