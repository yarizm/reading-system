<template>
  <div class="notes-page">
    <!-- 顶部栏 -->
    <div class="notes-header">
      <van-nav-bar title="我的笔记" left-arrow @click-left="$router.back()">
        <template #right>
          <van-button size="small" type="primary" @click="$router.push('/import-notes')">导入</van-button>
        </template>
      </van-nav-bar>
      <van-search v-model="searchKeyword" placeholder="搜索笔记..." @search="currentPage = 1; loadNotes()" />
    </div>

    <!-- 标签筛选 -->
    <div class="tag-filter">
      <div class="tag-chips">
        <van-tag
          :type="selectedTagId === null ? 'primary' : 'default'"
          size="medium"
          @click="selectedTagId = null; currentPage = 1; loadNotes()"
        >
          全部
        </van-tag>
        <van-tag
          v-for="tag in tagList"
          :key="tag.id"
          :type="selectedTagId === tag.id ? 'primary' : 'default'"
          size="medium"
          @click="selectedTagId = tag.id; currentPage = 1; loadNotes()"
          :style="selectedTagId === tag.id ? { background: tag.color } : {}"
        >
          {{ tag.name }}
        </van-tag>
      </div>
    </div>

    <!-- 笔记列表 -->
    <div class="note-list">
      <van-empty v-if="noteList.length === 0" description="暂无笔记" />

      <div v-for="note in noteList" :key="note.id" class="note-card">
        <div class="note-card-header">
          <span class="note-book" v-if="note.bookTitle">📚 {{ note.bookTitle }}</span>
          <span class="note-time">{{ formatTime(note.createTime) }}</span>
        </div>
        <div class="note-quote" v-if="note.selectedText">
          "{{ note.selectedText.substring(0, 60) }}{{ note.selectedText.length > 60 ? '...' : '' }}"
        </div>
        <div class="note-content markdown-body" v-html="renderMarkdown(note.content)"></div>
        <div class="note-footer">
          <div class="note-tags">
            <van-tag
              v-for="tag in note.tags"
              :key="tag.id"
              :color="tag.color"
              size="small"
              style="margin-right: 4px"
            >
              {{ tag.name }}
            </van-tag>
          </div>
          <div class="note-actions">
            <van-button
              plain size="mini"
              :type="reviewedNoteIds.has(note.id) ? 'warning' : 'success'"
              @click="addToReview(note.id)"
            >
              {{ reviewedNoteIds.has(note.id) ? '取消回顾' : '📖 回顾' }}
            </van-button>
            <van-button plain size="mini" type="danger" @click="deleteNote(note.id)">删除</van-button>
          </div>
        </div>
      </div>

      <van-button
        v-if="noteList.length < total"
        block
        plain
        type="primary"
        size="small"
        style="margin: 10px 0"
        @click="currentPage++; loadNotes()"
      >
        加载更多
      </van-button>
    </div>


  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { showToast, showConfirmDialog } from 'vant'
import { renderMarkdown } from '../utils/markdown'
import request from '../utils/request'

const tagList = ref([])
const noteList = ref([])
const selectedTagId = ref(null)
const searchKeyword = ref('')
const currentPage = ref(1)
const pageSize = 20
const total = ref(0)



const formatTime = (time) => {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 16)
}

const loadTags = async () => {
  try {
    const res = await request.get('/api/tag/list')
    if (res.data.code === '200') {
      tagList.value = res.data.data || []
    }
  } catch (e) {
    console.error(e)
  }
}

const loadNotes = async () => {
  try {
    const params = {
      page: currentPage.value,
      size: pageSize
    }
    if (selectedTagId.value) params.tagId = selectedTagId.value
    if (searchKeyword.value) params.keyword = searchKeyword.value

    const res = await request.get('/api/sysNote/globalList', { params })
    if (res.data.code === '200') {
      if (currentPage.value === 1) {
        noteList.value = res.data.data?.records || []
      } else {
        noteList.value.push(...(res.data.data?.records || []))
      }
      total.value = res.data.data?.total || 0
    }
  } catch (e) {
    console.error(e)
  }
}

const reviewedNoteIds = ref(new Set())

const loadReviewedIds = async () => {
  try {
    const res = await request.get('/api/review/reviewed-note-ids')
    if (res.data.code === '200') {
      reviewedNoteIds.value = new Set(res.data.data || [])
    }
  } catch (e) { console.error(e) }
}

const addToReview = async (noteId) => {
  try {
    if (reviewedNoteIds.value.has(noteId)) {
      await request.delete(`/api/review/remove/${noteId}`)
      reviewedNoteIds.value.delete(noteId)
      showToast('已取消回顾')
    } else {
      await request.post('/api/review/add', { noteId })
      reviewedNoteIds.value.add(noteId)
      showToast('已加入回顾')
    }
  } catch (e) { showToast('操作失败') }
}

const deleteNote = async (id) => {
  try {
    await showConfirmDialog({ title: '确定删除这条笔记？' })
    const res = await request.delete(`/api/sysNote/${id}`)
    if (res.data.code === '200') {
      showToast('已删除')
      currentPage.value = 1
      loadNotes()
      loadTags()
    }
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}


onMounted(() => {
  loadTags()
  loadNotes()
  loadReviewedIds()
})
</script>

<style scoped>
.notes-page { min-height: 100vh; background: #f5f5f5; }
.tag-filter { padding: 10px 15px; background: #fff; }
.tag-chips { display: flex; gap: 8px; overflow-x: auto; white-space: nowrap; }
.note-list { padding: 10px 15px; }
.note-card {
  background: #fff;
  border-radius: 8px;
  padding: 14px;
  margin-bottom: 10px;
}
.note-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.note-book { font-size: 13px; color: #409eff; }
.note-time { font-size: 12px; color: #999; }
.note-quote {
  font-size: 13px;
  color: #888;
  font-style: italic;
  border-left: 3px solid #ddd;
  padding-left: 10px;
  margin-bottom: 6px;
}
.note-content { font-size: 14px; color: #333; line-height: 1.5; margin-bottom: 8px; }
.note-content :deep(p) { margin: 0 0 8px; }
.note-content :deep(*:last-child) { margin-bottom: 0; }
.note-content :deep(ul),
.note-content :deep(ol) { padding-left: 20px; margin: 4px 0; }
.note-content :deep(li) { margin-bottom: 2px; }
.note-content :deep(code) {
  background: #f5f5f5; padding: 1px 4px; border-radius: 3px; font-size: 13px;
}
.note-content :deep(pre) {
  background: #f5f5f5; padding: 10px; border-radius: 6px; overflow-x: auto; margin: 6px 0;
}
.note-content :deep(pre code) { background: none; padding: 0; }
.note-content :deep(blockquote) {
  border-left: 3px solid #ddd; padding-left: 10px; color: #666; margin: 6px 0;
}
.note-content :deep(h1),
.note-content :deep(h2),
.note-content :deep(h3) { font-size: 15px; margin: 8px 0 4px; }
.note-content :deep(strong) { font-weight: 600; }
.note-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.note-tags { display: flex; flex-wrap: wrap; gap: 4px; }
.note-actions { display: flex; gap: 8px; }
</style>
