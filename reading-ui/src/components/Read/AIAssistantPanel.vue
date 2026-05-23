<template>
  <el-drawer 
    :model-value="show" 
    @update:model-value="$emit('update:show', $event)" 
    :direction="drawerDirection" 
    :size="drawerWidth + 'px'" 
    :modal="false" 
    class="ai-drawer"
  >
    <template #header>
      <div class="drawer-title-bar">
        <span>{{ aiTitle }}</span>
        <el-button link size="small" @click="$emit('toggle-direction')" :title="drawerDirection === 'rtl' ? '切换到左侧' : '切换到右侧'">
          <el-icon><DArrowLeft v-if="drawerDirection === 'rtl'" /><DArrowRight v-else /></el-icon>
        </el-button>
      </div>
    </template>
    
    <div class="resize-handle" :class="drawerDirection === 'rtl' ? 'resize-left' : 'resize-right'" @mousedown="$emit('start-resize', $event)"></div>
    
    <el-tabs :model-value="activeTab" @update:model-value="$emit('update:activeTab', $event)" stretch>
      <el-tab-pane label="AI 助手" name="ai">
        <div class="chat-layout">
          <div class="chat-history-box">
            <div v-if="chatList.length === 0" class="empty-state">
              <el-icon :size="40" color="#ddd"><Service /></el-icon>
              <p>你好，我是你的智能书童。</p>
            </div>
            <div v-for="(msg, index) in chatList" :key="index" class="chat-row" :class="msg.role === 'user' ? 'row-right' : 'row-left'">
              <div class="avatar-wrapper">
                <el-avatar :size="32" :icon="msg.role === 'user' ? UserFilled : Service" :style="{ background: msg.role === 'user' ? '#409eff' : '#36cfc9' }" />
              </div>
              <div class="bubble-wrapper">
                <div class="bubble-content markdown-body" v-html="renderMarkdown(msg.content)"></div>
                <div class="msg-actions" v-if="msg.role === 'ai' && msg.content">
                  <el-icon class="action-icon" @click="$emit('save-note', msg.content)"><DocumentAdd /></el-icon>
                </div>
              </div>
            </div>
          </div>
          <div class="chat-input-area">
            <el-input 
              :model-value="inputMessage" 
              @update:model-value="$emit('update:inputMessage', $event)" 
              placeholder="输入你的想法..." 
              :disabled="isThinking" 
              @keyup.enter="$emit('send-chat', null)"
            >
              <template #append>
                <el-button @click="$emit('send-chat', null)" :loading="isThinking">
                  <el-icon><Position /></el-icon>
                </el-button>
              </template>
            </el-input>
          </div>
        </div>
      </el-tab-pane>
      
      <el-tab-pane label="我的笔记" name="note">
        <div class="note-list-container">
          <el-empty v-if="noteList.length === 0" description="暂无笔记" />
          <div class="note-card" v-for="note in noteList" :key="note.id">
            <div class="note-header">
              <span class="note-time">{{ note.createTime?.replace('T', ' ') }}</span>
              <el-button link type="danger" size="small" @click="$emit('delete-note', note.id)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <div class="note-quote">“{{ note.selectedText.substring(0, 30) }}...”</div>
            <div class="note-content">{{ note.content }}</div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </el-drawer>
</template>

<script setup>
import { DArrowLeft, DArrowRight, Service, UserFilled, DocumentAdd, Position, Delete } from '@element-plus/icons-vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

marked.setOptions({ breaks: true, gfm: true })
const renderMarkdown = (text) => DOMPurify.sanitize(marked.parse(text || ''))

const props = defineProps({
  show: Boolean,
  aiTitle: String,
  drawerDirection: String,
  drawerWidth: Number,
  activeTab: String,
  chatList: Array,
  inputMessage: String,
  isThinking: Boolean,
  noteList: Array
})

const emit = defineEmits([
  'update:show', 'update:activeTab', 'update:inputMessage', 
  'toggle-direction', 'start-resize', 'send-chat', 'save-note', 'delete-note'
])
</script>

<style scoped>
.ai-drawer :deep(.el-drawer__header) {
  margin-bottom: 0;
  padding: 15px 20px;
  border-bottom: 1px solid rgba(0,0,0,0.05);
}
.theme-dark .ai-drawer :deep(.el-drawer__header) {
  border-bottom-color: rgba(255,255,255,0.05);
  color: #e8e8e8;
}

.drawer-title-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: bold;
}

.resize-handle {
  position: absolute;
  top: 0;
  bottom: 0;
  width: 5px;
  cursor: ew-resize;
  z-index: 100;
  background-color: transparent;
  transition: background-color 0.2s;
}
.resize-handle:hover, .resize-handle:active {
  background-color: #409eff;
}
.resize-left { right: 0; }
.resize-right { left: 0; }

.chat-layout {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
}

.chat-history-box {
  flex: 1;
  overflow-y: auto;
  padding: 15px;
}

.empty-state {
  text-align: center;
  color: #999;
  margin-top: 50px;
}

.chat-row {
  display: flex;
  margin-bottom: 20px;
}
.row-left { flex-direction: row; }
.row-right { flex-direction: row-reverse; }

.avatar-wrapper {
  margin: 0 10px;
}

.bubble-wrapper {
  max-width: 75%;
  position: relative;
}

.bubble-content {
  padding: 10px 15px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.5;
  word-break: break-word;
}
.row-left .bubble-content {
  background-color: #f4f4f5;
  color: #333;
  border-top-left-radius: 2px;
}
.theme-dark .row-left .bubble-content {
  background-color: #2b2b2b;
  color: #d0d0d0;
}
.row-right .bubble-content {
  background-color: #e1f3d8;
  color: #333;
  border-top-right-radius: 2px;
}
.theme-dark .row-right .bubble-content {
  background-color: #1e3a1e;
  color: #d0d0d0;
}

.msg-actions {
  text-align: right;
  margin-top: 5px;
}
.action-icon {
  cursor: pointer;
  color: #999;
}
.action-icon:hover { color: #409eff; }

.chat-input-area {
  padding: 10px;
  border-top: 1px solid rgba(0,0,0,0.05);
}
.theme-dark .chat-input-area {
  border-top-color: rgba(255,255,255,0.05);
}

.note-list-container {
  height: calc(100vh - 120px);
  overflow-y: auto;
  padding: 15px;
}

.note-card {
  background: white;
  border-radius: 8px;
  padding: 15px;
  margin-bottom: 15px;
  box-shadow: 0 2px 12px rgba(0,0,0,0.04);
  border: 1px solid rgba(0,0,0,0.05);
}
.theme-dark .note-card {
  background: #1e1e1e;
  border-color: rgba(255,255,255,0.05);
}

.note-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.note-time {
  font-size: 12px;
  color: #999;
}

.note-quote {
  font-style: italic;
  color: #666;
  font-size: 13px;
  padding-left: 10px;
  border-left: 3px solid #ddd;
  margin-bottom: 10px;
}
.theme-dark .note-quote { color: #aaa; border-left-color: #444; }

.note-content {
  font-size: 14px;
  color: #333;
  line-height: 1.5;
}
.theme-dark .note-content { color: #ccc; }
</style>
