<template>
  <van-popup
    :show="show"
    @update:show="$emit('update:show', $event)"
    position="bottom"
    round
    :style="{ padding: '15px 0 20px' }"
  >
    <div class="share-header">
      <div class="share-title">分享给好友</div>
    </div>
    
    <div class="share-content">
      <van-loading v-if="isLoadingFriends" type="spinner" class="loading-wrap" />
      <van-empty v-else-if="friendOptions.length === 0" description="暂无好友，先去添加吧" />
      <div v-else>
        <!-- Preview Box depending on shareMode -->
        <div class="share-preview" v-if="shareMode === 'book'">
          <van-image width="50" height="70" :src="bookInfo.coverUrl || 'https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg'" />
          <div class="preview-info">
            <div class="preview-title">{{ bookInfo.title }}</div>
            <div class="preview-author">{{ bookInfo.author }}</div>
          </div>
        </div>
        <div class="share-preview" v-else-if="shareMode === 'paragraph'">
          <div class="preview-info">
            <div class="preview-title">{{ bookInfo.title }}</div>
            <div class="preview-quote">“{{ selectedParagraphText }}”</div>
          </div>
        </div>
        <div class="share-preview" v-else-if="shareMode === 'audio'">
           <div class="preview-info">
            <div class="preview-title">{{ audioTitle || '朗读音频' }}</div>
            <div class="preview-author">{{ audioSourceLabel }}</div>
          </div>
        </div>

        <div style="padding: 0 15px; margin-top: 15px;">
          <div class="friend-select-title">选择好友</div>
          <div class="friend-list-h">
            <div 
              class="friend-item" 
              v-for="friend in friendOptions" 
              :key="friend.friendUserId"
              :class="{ selected: selectedFriendId === friend.friendUserId }"
              @click="$emit('update:selectedFriendId', friend.friendUserId)"
            >
              <van-image round width="40" height="40" :src="friend.avatar || 'https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg'" />
              <div class="friend-name van-ellipsis">{{ friend.nickname || friend.username }}</div>
            </div>
          </div>
          
          <div style="position: relative;">
            <van-field
              :model-value="shareMessage"
              @update:model-value="$emit('update:shareMessage', $event)"
              type="textarea"
              rows="3"
              placeholder="捎一句话（可选）"
              class="msg-input"
            />
            <van-button 
              size="mini" 
              plain 
              type="success" 
              style="position: absolute; right: 8px; bottom: 8px; height: 22px; padding: 0 6px;"
              @click="generateAiShareMessage"
              :loading="isGenerating"
            >
              ✨ AI 帮写
            </van-button>
          </div>
          
          <van-button 
            block 
            type="primary" 
            round 
            :loading="isSubmitting" 
            style="margin-top: 20px"
            @click="$emit('submit')"
          >
            发送
          </van-button>
        </div>
      </div>
    </div>
  </van-popup>
</template>

<script setup>
const props = defineProps({
  show: Boolean,
  shareMode: String, // 'book', 'paragraph', 'audio'
  isLoadingFriends: Boolean,
  friendOptions: Array,
  bookInfo: Object,
  selectedParagraphText: String,
  audioTitle: String,
  audioSourceLabel: String,
  
  selectedFriendId: Number,
  shareMessage: String,
  isSubmitting: Boolean
})

const emit = defineEmits(['update:show', 'update:selectedFriendId', 'update:shareMessage', 'submit'])

import { ref } from 'vue'
import request from '../../utils/request'
import { showSuccessToast, showFailToast } from 'vant'

const isGenerating = ref(false)

const generateAiShareMessage = async () => {
  let content = ''
  let bookTitle = props.bookInfo?.title || '未知书籍'
  if (props.shareMode === 'paragraph') {
    content = props.selectedParagraphText || ''
  } else if (props.shareMode === 'audio') {
    content = props.audioTitle || '音频片段'
  }
  
  isGenerating.value = true
  try {
    const res = await request.post('/api/social/ai/draft-share', {
      type: props.shareMode,
      content,
      bookTitle
    })
    if (res.data && res.data.result) {
      emit('update:shareMessage', res.data.result)
      showSuccessToast('推荐语已生成')
    } else {
      showFailToast('生成失败')
    }
  } catch (err) {
    showFailToast('AI 生成请求失败')
  } finally {
    isGenerating.value = false
  }
}
</script>

<style scoped>
.share-header { text-align: center; margin-bottom: 15px; }
.share-title { font-size: 16px; font-weight: bold; }
.loading-wrap { text-align: center; margin: 20px 0; }

.share-preview { display: flex; background: #f9f9f9; padding: 12px; margin: 0 15px; border-radius: 8px; }
.theme-dark .share-preview { background: #1e1e1e; }
.preview-info { margin-left: 12px; flex: 1; }
.preview-title { font-weight: bold; font-size: 15px; margin-bottom: 4px; }
.preview-author { font-size: 13px; color: #666; }
.preview-quote { font-style: italic; font-size: 13px; color: #666; line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }

.friend-select-title { font-size: 14px; margin-bottom: 10px; color: #666; }
.friend-list-h { display: flex; overflow-x: auto; padding-bottom: 10px; gap: 15px; }
.friend-item { text-align: center; width: 50px; opacity: 0.6; transition: all 0.2s; }
.friend-item.selected { opacity: 1; transform: scale(1.05); }
.friend-item.selected .van-image { border: 2px solid #1989fa; }
.friend-name { font-size: 12px; margin-top: 5px; }

.msg-input { background: #f5f5f5; border-radius: 8px; padding: 10px; }
.theme-dark .msg-input { background: #111; }
</style>
