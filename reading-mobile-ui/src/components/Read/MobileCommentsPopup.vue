<template>
  <van-popup
    :show="show"
    @update:show="$emit('update:show', $event)"
    position="bottom"
    :style="{ height: '70%' }"
    round
  >
    <div class="comments-header">
      <div class="comments-title">段落评论</div>
      <van-icon name="cross" class="close-btn" @click="$emit('update:show', false)" />
    </div>

    <div class="paragraph-quote" v-if="selectedParagraphIndex >= 0 && lines[selectedParagraphIndex]">
      “{{ lines[selectedParagraphIndex] }}”
    </div>

    <div class="comments-list">
      <van-loading v-if="isLoading" type="spinner" style="text-align: center; margin-top: 20px" />
      <van-empty v-else-if="comments.length === 0" description="暂无评论" />
      <div v-else v-for="comment in comments" :key="comment.id" class="comment-item">
        <van-image round width="36" height="36" :src="comment.avatar || 'https://fastly.jsdelivr.net/npm/@vant/assets/cat.jpeg'" />
        <div class="comment-main">
          <div class="comment-meta">
            <span class="nickname">{{ comment.nickname }}</span>
            <div class="comment-actions">
              <van-icon v-if="canDelete(comment)" name="delete-o" style="margin-right: 15px;" @click="$emit('delete', comment.id)" />
              <div class="like-box" :class="{ liked: comment.isLiked }" @click="$emit('like', comment)">
                <van-icon :name="comment.isLiked ? 'good-job' : 'good-job-o'" />
                <span>{{ comment.likeCount || 0 }}</span>
              </div>
            </div>
          </div>
          <div class="comment-content">{{ comment.content }}</div>
          <div class="comment-time">{{ comment.createTime?.replace('T', ' ') }}</div>
        </div>
      </div>
    </div>

    <div class="comment-input-box">
      <van-field
        :model-value="newComment"
        @update:model-value="$emit('update:newComment', $event)"
        placeholder="写下你的想法..."
        autosize
        type="textarea"
        rows="1"
      >
        <template #button>
          <van-button size="small" type="primary" :loading="isSubmitting" @click="$emit('submit')">发布</van-button>
        </template>
      </van-field>
    </div>
  </van-popup>
</template>

<script setup>
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

const emit = defineEmits(['update:show', 'update:newComment', 'submit', 'delete', 'like'])

const canDelete = (comment) => props.userInfo?.id === comment.userId || props.userInfo?.role === 1
</script>

<style scoped>
.comments-header { display: flex; justify-content: space-between; padding: 15px; border-bottom: 1px solid #f5f5f5; }
.theme-dark .comments-header { border-bottom-color: #333; }
.comments-title { font-size: 16px; font-weight: bold; }
.close-btn { font-size: 20px; color: #999; }

.paragraph-quote { padding: 12px; background: #f9f9f9; border-left: 3px solid #1989fa; font-size: 13px; color: #666; font-style: italic; margin-bottom: 10px; }
.theme-dark .paragraph-quote { background: #1e1e1e; color: #aaa; border-left-color: #1989fa; }

.comments-list { flex: 1; overflow-y: auto; padding: 10px 15px; height: calc(100% - 135px); }
.comment-item { display: flex; margin-bottom: 20px; }
.comment-main { margin-left: 10px; flex: 1; }
.comment-meta { display: flex; justify-content: space-between; align-items: center; margin-bottom: 5px; }
.nickname { font-size: 14px; font-weight: bold; color: #333; }
.theme-dark .nickname { color: #eee; }
.comment-actions { display: flex; align-items: center; color: #999; }
.like-box { display: flex; align-items: center; gap: 4px; }
.like-box.liked { color: #ee0a24; }
.comment-content { font-size: 14px; line-height: 1.5; color: #444; margin-bottom: 5px; }
.theme-dark .comment-content { color: #ccc; }
.comment-time { font-size: 12px; color: #aaa; }

.comment-input-box { position: absolute; bottom: 0; left: 0; right: 0; background: #fff; padding: 5px 0; border-top: 1px solid #f5f5f5; }
.theme-dark .comment-input-box { background: #1a1a1a; border-top-color: #333; }
</style>
