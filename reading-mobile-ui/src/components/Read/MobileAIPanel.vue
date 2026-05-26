<template>
  <van-popup
    :show="show"
    @update:show="$emit('update:show', $event)"
    position="bottom"
    :style="{ height: '85%' }"
    class="ai-drawer"
  >
    <div class="drawer-header">
      <div class="drawer-title">智能书童</div>
      <van-icon name="cross" class="close-btn" @click="$emit('update:show', false)" />
    </div>

    <van-tabs :active="activeTab" sticky @update:active="$emit('update:activeTab', $event)">
      <van-tab title="AI 助手" name="ai">
        <div class="chat-layout">
          <div class="chat-history-box" id="mobile-chat-box">
            <van-empty v-if="chatList.length === 0" description="你好，我是你的智能书童" />
            <div
              v-for="(msg, index) in chatList"
              :key="index"
              class="chat-row"
              :class="msg.role === 'user' ? 'row-right' : 'row-left'"
            >
              <div class="bubble-wrapper">
                <div class="bubble-content markdown-body" v-html="renderMarkdown(msg.content)"></div>
                <div class="msg-actions" v-if="msg.role === 'ai' && msg.content">
                  <span class="action-text" @click="$emit('save-note', msg.content)">保存为笔记</span>
                </div>
              </div>
            </div>
          </div>
          
          <!-- 快捷操作栏 -->
          <div class="quick-actions" style="padding: 10px; display: flex; gap: 8px; flex-wrap: nowrap; overflow-x: auto; border-top: 1px solid rgba(0,0,0,0.05);">
            <van-button size="mini" round type="primary" plain @click="$emit('send-chat', null, '帮我总结目前的笔记', '帮我总结目前的笔记')" style="flex-shrink: 0;">总结笔记</van-button>
            <van-button size="mini" round type="primary" plain @click="$emit('send-chat', null, '基于我的阅读进度，向我提问复习', '基于进度复习提问')" style="flex-shrink: 0;">复习提问</van-button>
            <van-button size="mini" round type="primary" plain @click="$emit('send-chat', null, '我遇到了瓶颈，能否根据我前几章的笔记给我些启发？', '笔记启发思考')" style="flex-shrink: 0;">笔记启发</van-button>
          </div>

          <div class="chat-input-area">
            <van-field
              :model-value="inputMessage"
              @update:model-value="$emit('update:inputMessage', $event)"
              center
              clearable
              placeholder="输入你的想法..."
              :disabled="isThinking"
            >
              <template #button>
                <van-button size="small" type="primary" :loading="isThinking" @click="$emit('send-chat', null)">发送</van-button>
              </template>
            </van-field>
          </div>
        </div>
      </van-tab>

      <van-tab title="我的笔记" name="note">
        <div class="note-list-container">
          <MobileNoteAiToolbar :bookId="bookId" v-if="bookId" />
          
          <van-empty v-if="noteList.length === 0" description="暂无笔记" />
          <div class="note-card" v-for="note in noteList" :key="note.id">
            <div class="note-header">
              <span class="note-time">{{ note.createTime?.replace('T', ' ') }}</span>
              <van-icon name="delete-o" color="#ee0a24" size="18" @click="$emit('delete-note', note.id)" />
            </div>
            <div class="note-quote">“{{ note.selectedText.substring(0, 30) }}...”</div>
            <div class="note-content markdown-body" v-html="renderMarkdown(note.content)"></div>
          </div>
        </div>
      </van-tab>
    </van-tabs>
  </van-popup>
</template>

<script setup>
import { ref } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import MobileNoteAiToolbar from '../MobileNoteAiToolbar.vue'

marked.setOptions({ breaks: true, gfm: true })
const renderMarkdown = (text) => DOMPurify.sanitize(marked.parse(text || ''))

const props = defineProps({
  show: Boolean,
  activeTab: String,
  chatList: Array,
  inputMessage: String,
  isThinking: Boolean,
  noteList: Array,
  bookId: {
    type: [Number, String],
    required: false
  }
})

const emit = defineEmits([
  'update:show', 'update:activeTab', 'update:inputMessage',
  'send-chat', 'save-note', 'delete-note'
])
</script>

<style scoped>
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

.chat-layout { display: flex; flex-direction: column; height: calc(85vh - 100px); }
.chat-history-box { flex: 1; overflow-y: auto; padding: 15px; }
.chat-row { display: flex; margin-bottom: 15px; }
.row-left { justify-content: flex-start; }
.row-right { justify-content: flex-end; }
.bubble-wrapper { max-width: 85%; }
.bubble-content { padding: 10px; border-radius: 8px; font-size: 14px; line-height: 1.5; word-break: break-word; }
.row-left .bubble-content { background-color: #f2f3f5; color: #333; }
.theme-dark .row-left .bubble-content { background-color: #2b2b2b; color: #eee; }
.row-right .bubble-content { background-color: #e1f3d8; color: #333; }
.theme-dark .row-right .bubble-content { background-color: #1e3a1e; color: #eee; }
.msg-actions { text-align: right; margin-top: 5px; }
.action-text { font-size: 12px; color: #1989fa; cursor: pointer; }

.note-list-container { height: calc(85vh - 100px); overflow-y: auto; padding: 15px; background: #f7f8fa; }
.theme-dark .note-list-container { background: #111; }
.note-card { background: #fff; border-radius: 8px; padding: 15px; margin-bottom: 15px; }
.theme-dark .note-card { background: #1e1e1e; }
.note-header { display: flex; justify-content: space-between; margin-bottom: 8px; }
.note-time { font-size: 12px; color: #999; }
.note-quote { font-style: italic; color: #666; font-size: 13px; border-left: 3px solid #ccc; padding-left: 8px; margin-bottom: 8px; }
.note-content { font-size: 13px; color: #333; }

:deep(.markdown-body) {
  font-size: 14px;
  line-height: 1.6;
  word-wrap: break-word;
}
:deep(.markdown-body p) {
  margin-bottom: 8px;
}
:deep(.markdown-body h1), :deep(.markdown-body h2), :deep(.markdown-body h3), :deep(.markdown-body h4) {
  font-weight: 600;
  margin-top: 12px;
  margin-bottom: 8px;
}
:deep(.markdown-body h1) { font-size: 18px; }
:deep(.markdown-body h2) { font-size: 16px; }
:deep(.markdown-body h3) { font-size: 15px; }
:deep(.markdown-body ul), :deep(.markdown-body ol) {
  padding-left: 20px;
  margin-bottom: 8px;
}
:deep(.markdown-body ul) { list-style-type: disc; }
:deep(.markdown-body ol) { list-style-type: decimal; }
:deep(.markdown-body li) {
  margin-bottom: 4px;
}
:deep(.markdown-body pre) {
  background-color: rgba(0, 0, 0, 0.05);
  padding: 8px;
  border-radius: 4px;
  overflow-x: auto;
  font-family: monospace;
}
:deep(.markdown-body code) {
  background-color: rgba(0, 0, 0, 0.05);
  padding: 2px 4px;
  border-radius: 3px;
  font-family: monospace;
}
:deep(.markdown-body pre code) {
  background-color: transparent;
  padding: 0;
}
:deep(.markdown-body blockquote) {
  border-left: 3px solid #ccc;
  padding-left: 8px;
  color: #666;
  margin: 0 0 8px 0;
}
:deep(.markdown-body strong) {
  font-weight: bold;
}
</style>
