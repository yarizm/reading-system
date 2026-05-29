<template>
  <div>
    <!-- Book Share Dialog -->
    <el-dialog :model-value="showBookShare" @update:model-value="$emit('update:showBookShare', $event)" title="分享书籍给好友" width="430px">
      <div v-loading="isLoadingFriends" class="share-dialog-body">
        <el-empty v-if="!isLoadingFriends && friendOptions.length === 0" description="你还没有好友，先去好友中心添加吧" :image-size="72" />
        <template v-else>
          <div class="share-preview-card">
            <img :src="bookInfo.coverUrl || 'https://via.placeholder.com/150'" class="share-preview-cover"  alt=""/>
            <div class="share-preview-main">
              <div class="share-preview-title">{{ bookInfo.title }}</div>
              <div class="share-preview-meta">{{ bookInfo.author || '未知作者' }}</div>
            </div>
          </div>
          <div class="share-dialog-form">
            <el-select :model-value="bookShareFriendId" @update:model-value="$emit('update:bookShareFriendId', $event)" placeholder="请选择好友" filterable clearable style="width: 100%">
              <el-option
                  v-for="friend in friendOptions"
                  :key="friend.friendUserId"
                  :label="friend.nickname || friend.username"
                  :value="friend.friendUserId"
              />
            </el-select>
            <div style="margin-top: 15px; position: relative;">
              <el-input
                  :model-value="bookShareMessage"
                  @update:model-value="$emit('update:bookShareMessage', $event)"
                  type="textarea"
                  :rows="3"
                  maxlength="200"
                  show-word-limit
                  placeholder="给好友捎一句话（可选）"
              />
              <el-button size="small" type="success" plain 
                         style="position: absolute; right: 8px; bottom: 8px;"
                         @click="generateShareMessage('book')" :loading="isGeneratingMessage">
                <el-icon><MagicStick /></el-icon> AI 帮写
              </el-button>
            </div>
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="$emit('update:showBookShare', false)">取消</el-button>
        <el-button type="primary" :loading="isSubmittingBookShare" :disabled="friendOptions.length === 0" @click="$emit('submit-book')">确认分享</el-button>
      </template>
    </el-dialog>

    <!-- Paragraph Share Dialog -->
    <el-dialog :model-value="showParagraphShare" @update:model-value="$emit('update:showParagraphShare', $event)" title="分享段落给好友" width="460px">
      <div v-loading="isLoadingFriends" class="share-dialog-body">
        <el-empty v-if="!isLoadingFriends && friendOptions.length === 0" description="你还没有好友，先去好友中心添加吧" :image-size="72" />
        <template v-else>
          <div class="share-preview-card is-paragraph">
            <div class="share-preview-main">
              <div class="share-preview-title">{{ bookInfo.title }}</div>
              <div class="share-position-meta">第 {{ chapterIndex + 1 }} 章 · 第 {{ paragraphShareIndex + 1 }} 段</div>
              <div class="share-preview-quote">“{{ selectedParagraphText }}”</div>
            </div>
          </div>
          <div class="share-dialog-form">
            <el-select :model-value="paragraphShareFriendId" @update:model-value="$emit('update:paragraphShareFriendId', $event)" placeholder="请选择好友" filterable clearable style="width: 100%">
              <el-option
                  v-for="friend in friendOptions"
                  :key="friend.friendUserId"
                  :label="friend.nickname || friend.username"
                  :value="friend.friendUserId"
              />
            </el-select>
            <div style="margin-top: 15px; position: relative;">
              <el-input
                  :model-value="paragraphShareMessage"
                  @update:model-value="$emit('update:paragraphShareMessage', $event)"
                  type="textarea"
                  :rows="3"
                  maxlength="200"
                  show-word-limit
                  placeholder="可以补充你为什么想分享这段（可选）"
              />
              <el-button size="small" type="success" plain 
                         style="position: absolute; right: 8px; bottom: 8px;"
                         @click="generateShareMessage('paragraph')" :loading="isGeneratingMessage">
                <el-icon><MagicStick /></el-icon> AI 帮写
              </el-button>
            </div>
          </div>
        </template>
      </div>
      <template #footer>
        <el-button @click="$emit('update:showParagraphShare', false)">取消</el-button>
        <el-button type="primary" :loading="isSubmittingParagraphShare" :disabled="friendOptions.length === 0" @click="$emit('submit-paragraph')">确认分享</el-button>
      </template>
    </el-dialog>

    <!-- Audio Share Dialog -->
    <el-dialog :model-value="showAudioShare" @update:model-value="$emit('update:showAudioShare', $event)" title="分享音频给好友" width="430px">
      <div class="share-dialog-body">
        <div class="share-preview-card is-paragraph">
          <div class="share-preview-main">
            <div class="share-preview-title">{{ audioPlayback?.title || '朗读音频' }}</div>
            <div class="share-position-meta">{{ audioSourceLabel }}</div>
          </div>
        </div>
        <div class="share-dialog-form">
          <el-select :model-value="audioShareFriendId" @update:model-value="$emit('update:audioShareFriendId', $event)" placeholder="请选择好友" filterable clearable style="width: 100%">
            <el-option
                v-for="friend in friendOptions"
                :key="friend.friendUserId"
                :label="friend.nickname || friend.username"
                :value="friend.friendUserId"
            />
          </el-select>
          <div style="margin-top: 15px; position: relative;">
            <el-input
                :model-value="audioShareMessage"
                @update:model-value="$emit('update:audioShareMessage', $event)"
                type="textarea"
                :rows="3"
                maxlength="200"
                show-word-limit
                placeholder="给好友捎一句话（可选）"
            />
            <el-button size="small" type="success" plain 
                       style="position: absolute; right: 8px; bottom: 8px;"
                       @click="generateShareMessage('audio')" :loading="isGeneratingMessage">
              <el-icon><MagicStick /></el-icon> AI 帮写
            </el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="$emit('update:showAudioShare', false)">取消</el-button>
        <el-button type="primary" :loading="isSubmittingAudioShare" :disabled="friendOptions.length === 0" @click="$emit('submit-audio')">确认分享</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
const props = defineProps({
  showBookShare: Boolean,
  showParagraphShare: Boolean,
  showAudioShare: Boolean,
  isLoadingFriends: Boolean,
  friendOptions: Array,
  bookInfo: Object,
  
  bookShareFriendId: Number,
  bookShareMessage: String,
  isSubmittingBookShare: Boolean,
  
  paragraphShareFriendId: Number,
  paragraphShareMessage: String,
  paragraphShareIndex: Number,
  chapterIndex: Number,
  selectedParagraphText: String,
  isSubmittingParagraphShare: Boolean,
  
  audioShareFriendId: Number,
  audioShareMessage: String,
  audioPlayback: Object,
  audioSourceLabel: String,
  isSubmittingAudioShare: Boolean
})

const emit = defineEmits([
  'update:showBookShare', 'update:bookShareFriendId', 'update:bookShareMessage', 'submit-book',
  'update:showParagraphShare', 'update:paragraphShareFriendId', 'update:paragraphShareMessage', 'submit-paragraph',
  'update:showAudioShare', 'update:audioShareFriendId', 'update:audioShareMessage', 'submit-audio'
])

import { ref } from 'vue'
import request from '../../utils/request'
import { ElMessage } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'

const isGeneratingMessage = ref(false)

const generateShareMessage = async (type) => {
  let content = ''
  let bookTitle = props.bookInfo?.title || '未知书籍'
  if (type === 'paragraph') {
    content = props.selectedParagraphText
  } else if (type === 'audio') {
    content = props.audioPlayback?.title || '音频片段'
  }
  
  isGeneratingMessage.value = true
  try {
    const res = await request.post('/api/social/ai/draft-share', { type, content, bookTitle })
    if (res.data && res.data.result) {
      if (type === 'book') emit('update:bookShareMessage', res.data.result)
      if (type === 'paragraph') emit('update:paragraphShareMessage', res.data.result)
      if (type === 'audio') emit('update:audioShareMessage', res.data.result)
      ElMessage.success('推荐语已生成')
    }
  } catch (err) {
    ElMessage.error('AI 生成失败')
  } finally {
    isGeneratingMessage.value = false
  }
}
</script>

<style scoped>
.share-dialog-body {
  padding: 0 10px;
}

.share-preview-card {
  display: flex;
  background: rgba(0, 0, 0, 0.02);
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 20px;
  border: 1px solid rgba(0,0,0,0.05);
}
.theme-dark .share-preview-card {
  background: rgba(255, 255, 255, 0.05);
  border-color: rgba(255,255,255,0.05);
}

.share-preview-card.is-paragraph {
  flex-direction: column;
}

.share-preview-cover {
  width: 60px;
  height: 80px;
  object-fit: cover;
  border-radius: 4px;
  margin-right: 15px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.1);
}

.share-preview-main {
  flex: 1;
}

.share-preview-title {
  font-weight: bold;
  font-size: 16px;
  color: #333;
  margin-bottom: 6px;
}
.theme-dark .share-preview-title { color: #e8e8e8; }

.share-preview-meta {
  font-size: 13px;
  color: #666;
}
.theme-dark .share-preview-meta { color: #aaa; }

.share-position-meta {
  font-size: 12px;
  color: #999;
  margin-bottom: 8px;
}

.share-preview-quote {
  font-style: italic;
  font-size: 13px;
  color: #666;
  border-left: 3px solid #ddd;
  padding-left: 8px;
  line-height: 1.5;
}
.theme-dark .share-preview-quote {
  color: #aaa;
  border-left-color: #444;
}

.share-dialog-form {
  margin-top: 10px;
}
</style>
