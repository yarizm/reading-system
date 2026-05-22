<template>
  <el-drawer :model-value="show" @update:model-value="$emit('update:show', $event)" title="段落评论" direction="rtl" size="400px" class="comment-drawer">
    <div class="paragraph-quote" v-if="selectedParagraphIndex >= 0 && lines[selectedParagraphIndex]">
      “{{ lines[selectedParagraphIndex] }}”
    </div>
    <div class="comment-list" v-loading="isLoading">
      <el-empty v-if="comments.length === 0" description="暂无评论" />
      <div v-for="comment in comments" :key="comment.id" class="comment-item">
        <el-avatar :size="32" :src="comment.avatar || 'https://cube.elemecdn.com/0/88/03b0d39583f48206768a7534e55bcpng.png'" class="clickable-user" @click="$emit('go-to-user', comment.userId)"></el-avatar>
        <div class="comment-body">
          <div class="comment-header">
            <span class="comment-user clickable-user" @click="$emit('go-to-user', comment.userId)">{{ comment.nickname }}</span>
            <div class="comment-ops">
              <el-icon v-if="canDelete(comment)" class="op-icon delete-icon" @click="$emit('delete', comment.id)"><Delete /></el-icon>
              <div class="like-box" @click="$emit('like', comment)">
                <el-icon :class="{ 'is-liked': comment.isLiked }">
                  <StarFilled v-if="comment.isLiked"/><Star v-else/>
                </el-icon>
                <span class="like-count">{{ comment.likeCount || 0 }}</span>
              </div>
            </div>
          </div>
          <div class="comment-content">{{ comment.content }}</div>
          <span class="comment-time">{{ comment.createTime?.replace('T', ' ') }}</span>
        </div>
      </div>
    </div>
    <div class="comment-input-area">
      <el-input :model-value="newComment" @update:model-value="$emit('update:newComment', $event)" type="textarea" :rows="3" placeholder="写下你的想法..." />
      <div style="text-align: right; margin-top: 10px;">
        <el-button type="primary" size="small" @click="$emit('submit')" :loading="isSubmitting">发表评论</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { Delete, Star, StarFilled } from '@element-plus/icons-vue'

const props = defineProps({
  show: Boolean,
  lines: Array,
  selectedParagraphIndex: Number,
  comments: Array,
  newComment: String,
  isLoading: Boolean,
  isSubmitting: Boolean,
  userInfo: Object
})

const emit = defineEmits(['update:show', 'update:newComment', 'submit', 'delete', 'like', 'go-to-user'])

const canDelete = (comment) => {
  return props.userInfo?.id === comment.userId || props.userInfo?.role === 1
}
</script>

<style scoped>
.paragraph-quote {
  padding: 12px;
  background: rgba(0, 0, 0, 0.02);
  border-left: 4px solid #0066cc;
  margin-bottom: 16px;
  font-style: italic;
  color: #666;
  font-size: 14px;
}
.theme-dark .paragraph-quote {
  background: rgba(255, 255, 255, 0.05);
  border-left-color: #409eff;
  color: #aaa;
}

.comment-list {
  flex: 1;
  overflow-y: auto;
  padding-bottom: 120px;
}

.comment-item {
  display: flex;
  margin-bottom: 20px;
  padding-bottom: 15px;
  border-bottom: 1px solid rgba(0,0,0,0.05);
}
.theme-dark .comment-item {
  border-bottom-color: rgba(255,255,255,0.05);
}

.comment-body {
  margin-left: 12px;
  flex: 1;
}

.comment-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.comment-user {
  font-weight: bold;
  font-size: 14px;
  color: #333;
}
.theme-dark .comment-user { color: #eee; }

.comment-content {
  font-size: 14px;
  line-height: 1.5;
  color: #444;
  margin-bottom: 6px;
}
.theme-dark .comment-content { color: #ccc; }

.comment-time {
  font-size: 12px;
  color: #999;
}

.clickable-user {
  cursor: pointer;
}
.clickable-user:hover {
  opacity: 0.8;
}

.comment-ops {
  display: flex;
  align-items: center;
  gap: 12px;
}

.op-icon {
  cursor: pointer;
  color: #999;
  transition: color 0.2s;
}

.delete-icon:hover { color: #f56c6c; }

.like-box {
  display: flex;
  align-items: center;
  cursor: pointer;
  color: #999;
  transition: color 0.2s;
}
.like-box:hover { color: #0066cc; }
.like-box .el-icon.is-liked { color: #f5a623; }
.like-count {
  margin-left: 4px;
  font-size: 13px;
}

.comment-input-area {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 15px;
  background: white;
  border-top: 1px solid rgba(0,0,0,0.05);
  z-index: 10;
}
.theme-dark .comment-input-area {
  background: #1e1e1e;
  border-top-color: rgba(255,255,255,0.05);
}
</style>
