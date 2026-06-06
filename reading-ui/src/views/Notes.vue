<template>
  <div class="page-glass-container notes-container-page">
    <div class="page-header">
      <div class="header-left">
        <el-button plain round class="back-btn glass-btn" @click="router.push('/')">
          <el-icon><ArrowLeft /></el-icon> 返回首页
        </el-button>
        <el-divider direction="vertical" />
        <div class="header-title-box">
          <h2>📝 我的笔记</h2>
        </div>
      </div>
      <div style="display: flex; align-items: center; gap: 10px">
        <el-button @click="$router.push('/import-notes')">📥 导入笔记</el-button>
        <el-input
          v-model="searchKeyword"
          placeholder="搜索笔记..."
          clearable
          style="width: 300px"
          @keyup.enter="loadNotes"
        >
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
    </div>

    <div class="notes-page">
      <div class="notes-body">
        <!-- 左侧筛选面板 -->
        <div class="filter-panel">
          <div class="filter-section">
            <h4>标签筛选</h4>
            <div class="tag-list">
              <div
                class="tag-item"
                :class="{ active: selectedTagId === null }"
                @click="selectedTagId = null; loadNotes()"
              >
                <span class="tag-dot" style="background: #409eff"></span>
                全部
              </div>
              <div
                v-for="tag in tagList"
                :key="tag.id"
                class="tag-item"
                :class="{ active: selectedTagId === tag.id }"
                @click="selectedTagId = tag.id; loadNotes()"
              >
                <span class="tag-dot" :style="{ background: tag.color }"></span>
                {{ tag.name }}
                <span class="tag-count">{{ tag.noteCount || 0 }}</span>
              </div>
            </div>
          </div>

          <div class="filter-section">
            <h4>时间筛选</h4>
            <el-select v-model="timeFilter" style="width: 100%" @change="loadNotes">
              <el-option label="全部时间" value="" />
              <el-option label="近7天" value="7d" />
              <el-option label="近30天" value="30d" />
              <el-option label="近90天" value="90d" />
            </el-select>
          </div>
        </div>

        <!-- 右侧笔记列表 -->
        <div class="note-list">
          <el-empty v-if="noteList.length === 0" description="暂无笔记" />

          <div v-for="note in noteList" :key="note.id" class="note-card">
            <div class="note-card-header">
              <span class="note-book" v-if="note.bookTitle">
                📚 {{ note.bookTitle }}
              </span>
              <span class="note-time">{{ formatTime(note.createTime) }}</span>
            </div>
            <div class="note-quote" v-if="note.selectedText">
              "{{ note.selectedText.substring(0, 80) }}{{ note.selectedText.length > 80 ? '...' : '' }}"
            </div>
            <div class="note-content markdown-body" v-html="renderMarkdown(note.content)"></div>
            <div class="note-footer">
              <div class="note-tags">
                <el-tag
                  v-for="tag in note.tags"
                  :key="tag.id"
                  :color="tag.color"
                  size="small"
                  style="color: #fff; border: none; margin-right: 4px"
                >
                  {{ tag.name }}
                </el-tag>
              </div>
              <div class="note-actions">
                <el-button
                  link size="small"
                  :type="reviewedNoteIds.has(note.id) ? 'warning' : 'success'"
                  @click="addToReview(note.id)"
                >
                  {{ reviewedNoteIds.has(note.id) ? '📖 取消回顾' : '📖 加入回顾' }}
                </el-button>
                <el-button link size="small" @click="viewRelations(note)">
                  🔗 关联
                </el-button>
                <el-button link type="danger" size="small" @click="deleteNote(note.id)">
                  删除
                </el-button>
              </div>
            </div>
          </div>

          <!-- 分页 -->
          <div class="pagination" v-if="total > pageSize">
            <el-pagination
              v-model:current-page="currentPage"
              :page-size="pageSize"
              :total="total"
              layout="prev, pager, next"
              @current-change="loadNotes"
            />
          </div>
        </div>
      </div>

      <!-- 关联笔记对话框 -->
      <el-dialog v-model="showRelations" title="关联笔记" width="500px" append-to-body>
        <div v-if="relationList.length === 0" style="text-align: center; color: #999">
          暂无关联笔记
        </div>
        <div v-for="rel in relationList" :key="rel.relationId" class="relation-item">
          <div class="note-quote">"{{ rel.relatedNote.selectedText?.substring(0, 50) }}..."</div>
          <div class="note-content">{{ rel.relatedNote.content?.substring(0, 100) }}</div>
          <div class="note-tags" style="margin-top: 4px">
            <el-tag
              v-for="tag in rel.relatedNote.tags"
              :key="tag.id"
              :color="tag.color"
              size="small"
              style="color: #fff; border: none; margin-right: 4px"
            >
              {{ tag.name }}
            </el-tag>
          </div>
        </div>
      </el-dialog>

    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search, ArrowLeft } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { renderMarkdown } from '../utils/markdown'
import request from '../utils/request'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const userInfo = computed(() => authStore.user || {})

const tagList = ref([])
const noteList = ref([])
const selectedTagId = ref(null)
const searchKeyword = ref('')
const timeFilter = ref('')
const currentPage = ref(1)
const pageSize = 20
const total = ref(0)

const showRelations = ref(false)
const relationList = ref([])
const reviewedNoteIds = ref(new Set())

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
    console.error('加载标签失败', e)
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
    if (timeFilter.value) {
      const now = new Date()
      const days = parseInt(timeFilter.value)
      const start = new Date(now.getTime() - days * 86400000)
      params.startDate = start.toISOString().substring(0, 10)
    }

    const res = await request.get('/api/sysNote/globalList', { params })
    if (res.data.code === '200') {
      noteList.value = res.data.data?.records || []
      total.value = res.data.data?.total || 0
    }
  } catch (e) {
    console.error('加载笔记失败', e)
  }
}

const deleteNote = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除这条笔记？', '提示', { type: 'warning' })
    const res = await request.delete(`/api/sysNote/${id}`)
    if (res.data.code === '200') {
      ElMessage.success('已删除')
      loadNotes()
      loadTags()
    }
  } catch (e) {
    if (e !== 'cancel') console.error(e)
  }
}

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
      ElMessage.success('已取消回顾')
    } else {
      await request.post('/api/review/add', { noteId })
      reviewedNoteIds.value.add(noteId)
      ElMessage.success('已加入回顾')
    }
  } catch (e) { ElMessage.error('操作失败') }
}

const viewRelations = async (note) => {
  try {
    const res = await request.get(`/api/noteRelation/list/${note.id}`)
    if (res.data.code === '200') {
      relationList.value = res.data.data || []
      showRelations.value = true
    }
  } catch (e) {
    console.error(e)
  }
}


onMounted(() => {
  loadTags()
  loadNotes()
  loadReviewedIds()
})
</script>

<style scoped>
.notes-container-page { padding: 0 24px 18px; }
.notes-container-page :deep(.page-header) {
  position: sticky;
  top: 0;
  z-index: 1000;
  margin: 0 -24px 24px;
  padding: 18px 32px 16px;
  background: #fff;
  border-bottom: 1px solid var(--border-color);
  box-shadow: 0 4px 20px -2px rgba(0, 0, 0, 0.05);
}
.notes-page {
  max-width: 1200px;
  margin: 0 auto;
}
.notes-body {
  display: flex;
  align-items: flex-start;
  gap: 20px;
}
.filter-panel {
  width: 220px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
  height: fit-content;
  position: sticky;
  top: 100px;
}
.filter-section { margin-bottom: 20px; }
.filter-section h4 { margin: 0 0 10px 0; font-size: 14px; color: #333; }
.tag-list { display: flex; flex-direction: column; gap: 6px; }
.tag-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: #666;
  transition: all 0.2s;
}
.tag-item:hover { background: #f5f5f5; }
.tag-item.active { background: #ecf5ff; color: #409eff; font-weight: 500; }
.tag-dot { width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0; }
.tag-count { margin-left: auto; font-size: 12px; color: #999; }
.note-list { flex: 1; min-width: 0; }
.note-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.06);
}
.note-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.note-book { font-size: 13px; color: #409eff; }
.note-time { font-size: 12px; color: #999; }
.note-quote {
  font-size: 13px;
  color: #888;
  font-style: italic;
  border-left: 3px solid #ddd;
  padding-left: 10px;
  margin-bottom: 8px;
}
.note-content {
  font-size: 14px;
  color: #333;
  line-height: 1.6;
  margin-bottom: 8px;
}
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
.pagination { margin-top: 20px; display: flex; justify-content: center; }
.relation-item {
  padding: 12px;
  border: 1px solid #eee;
  border-radius: 6px;
  margin-bottom: 8px;
}
</style>
