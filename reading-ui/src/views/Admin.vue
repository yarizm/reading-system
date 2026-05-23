<template>
  <div class="admin-container page-glass-container">
    <div class="page-header">
      <div class="header-left">
        <el-button plain round class="back-btn glass-btn" @click="goHome">
          <el-icon><ArrowLeft /></el-icon> 返回首页
        </el-button>
        <el-divider direction="vertical" />
        <div class="header-title-box">
          <h2>后台管理系统</h2>
        </div>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="premium-tabs">

      <el-tab-pane label="图书管理" name="book">
        <div class="toolbar">
          <el-button type="primary" @click="openAddBook">上传新书</el-button>
        </div>

        <el-table :data="bookList" class="premium-table">
          <el-table-column prop="id" label="ID" width="60" align="center" />
          <el-table-column prop="title" label="书名" align="center" />
          <el-table-column prop="author" label="作者" align="center" />
          <el-table-column prop="category" label="分类" width="80" align="center" />
          <el-table-column label="来源" width="140" align="center">
            <template #default="scope">
              <div v-if="scope.row.uploaderId" style="display: flex; align-items: center; justify-content: center;">
                <el-tag type="warning" effect="plain" size="small">
                  <div style="display: flex; align-items: center; gap: 4px; white-space: nowrap;">
                    <el-icon><User/></el-icon><span>{{ scope.row.uploaderNickname || '用户提供' }}</span>
                  </div>
                </el-tag>
              </div>
              <el-tag v-else type="success" effect="plain" size="small">官方发布</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="封面" width="80" align="center">
            <template #default="scope">
              <img v-if="scope.row.coverUrl" :src="scope.row.coverUrl" style="width: 40px; height: 40px; object-fit: cover; border-radius: 4px" alt=""/>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" align="center">
            <template #default="scope">
              <el-button plain size="small" type="primary" @click="openEditBook(scope.row)">编辑</el-button>
              <el-button plain size="small" type="danger" @click="deleteBook(scope.row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination layout="prev, pager, next" v-model:current-page="currentBookPage" :total="totalBooks" @current-change="loadBooks" style="margin-top: 15px;" />
      </el-tab-pane>

      <el-tab-pane label="用户管理" name="user">
        <el-table :data="userList" class="premium-table">
          <el-table-column prop="id" label="ID" width="60" align="center" />
          <el-table-column prop="username" label="用户名" align="center" />
          <el-table-column prop="nickname" label="昵称" align="center" />
          <el-table-column prop="role" label="角色" width="100" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.role === 1 ? 'danger' : 'success'" effect="plain">
                {{ scope.row.role === 1 ? '管理员' : '普通用户' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="isBanned" label="状态" width="100" align="center">
            <template #default="scope">
              <el-tag :type="scope.row.isBanned === 1 ? 'danger' : 'success'" effect="plain">
                <div style="display: flex; align-items: center; gap: 4px; white-space: nowrap;">
                  <el-icon v-if="scope.row.isBanned === 1"><CircleClose /></el-icon>
                  <el-icon v-else><CircleCheck /></el-icon>
                  <span>{{ scope.row.isBanned === 1 ? '已封禁' : '正常' }}</span>
                </div>
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="280" fixed="right" align="center">
            <template #default="scope">
              <el-button plain size="small" type="primary" @click="openEditUser(scope.row)">编辑</el-button>
              <el-button plain size="small" type="info" @click="openUserRecords(scope.row.id)" :disabled="scope.row.id === 1">发言</el-button>
              <el-button
                  plain size="small"
                  type="warning"
                  @click="toggleBan(scope.row)"
                  :disabled="scope.row.id === 1"
              >{{ scope.row.isBanned === 1 ? '解封' : '封禁' }}</el-button>
              <el-button
                  plain size="small"
                  type="danger"
                  @click="deleteUser(scope.row.id)"
                  :disabled="scope.row.role === 1 || scope.row.id === 1"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination layout="prev, pager, next" v-model:current-page="currentUserPage" :total="totalUsers" @current-change="loadUsers" style="margin-top: 15px;" />
      </el-tab-pane>

      <el-tab-pane label="书籍审核" name="review">
        <el-empty v-if="reviewList.length === 0" description="暂无待审核的请求" />
        <el-table v-else :data="reviewList" class="premium-table">
          <el-table-column prop="id" label="请求ID" width="80" align="center" />
          <el-table-column prop="bookTitle" label="书名" align="center" />
          <el-table-column prop="uploaderNickname" label="申请人" width="120" align="center" />
          <el-table-column label="请求类型" width="100" align="center">
            <template #default="scope">
              <el-tag :type="reqTypeTag(scope.row.requestType)" effect="plain" size="small">
                {{ reqTypeText(scope.row.requestType) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="封面" width="80" align="center">
            <template #default="scope">
              <img v-if="scope.row.bookCoverUrl" :src="scope.row.bookCoverUrl" style="width: 36px; height: 48px; object-fit: cover; border-radius: 3px" alt=""/>
              <span v-else style="color: #c4b9ab; font-size: 12px">无</span>
            </template>
          </el-table-column>
          <el-table-column label="发起时间" width="180" align="center">
            <template #default="scope">
              <span style="font-size: 13px; color: #7a6e63">{{ new Date(scope.row.createTime).toLocaleString() }}</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right" align="center">
            <template #default="scope">
              <el-button plain size="small" type="info" @click="openReviewDetail(scope.row)">详情</el-button>
              <el-button plain size="small" type="success" @click="reviewRequest(scope.row.id, 'approve')">通过</el-button>
              <el-button plain size="small" type="danger" @click="openRejectDialog(scope.row)">驳回</el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

    </el-tabs>

    <el-dialog v-model="showBookDialog" :title="bookForm.id ? '编辑图书' : '上传图书'">
      <el-form :model="bookForm" label-width="80px">
        <el-form-item label="书名"><el-input v-model="bookForm.title"/></el-form-item>
        <el-form-item label="作者"><el-input v-model="bookForm.author"/></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="bookForm.category" style="width: 100%">
            <el-option value="科幻" label="科幻"/>
            <el-option value="文学" label="文学"/>
            <el-option value="历史" label="历史"/>
            <el-option value="技术" label="技术"/>
            <el-option value="悬疑" label="悬疑"/>
          </el-select>
        </el-form-item>

        <el-form-item label="简介">
          <el-input
              v-model="bookForm.description"
              type="textarea"
              :rows="4"
              placeholder="请输入书籍的内容简介..."
          />
        </el-form-item>
        <el-form-item label="封面图">
          <el-upload
              action="/api/sysBook/upload"
              :headers="uploadHeaders"
              :on-success="(res) => bookForm.coverUrl = res.data"
              :show-file-list="false"
              class="avatar-uploader"
          >
            <img v-if="bookForm.coverUrl" :src="bookForm.coverUrl" class="upload-img-preview" alt=""/>
            <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
            <div v-if="!bookForm.coverUrl" class="el-upload__text">点击上传</div>
          </el-upload>
        </el-form-item>

        <el-form-item label="电子书">
          <el-upload
              action="/api/sysBook/upload"
              :headers="uploadHeaders"
              :on-success="(res) => bookForm.filePath = res.data"
              :show-file-list="false"
          >
            <el-button size="small" type="primary">
              {{ bookForm.filePath ? '文件已上传 (点击替换)' : '上传 TXT' }}
            </el-button>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBookDialog = false">取消</el-button>
        <el-button @click="saveBook" type="primary">保存提交</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showUserDialog" title="修改用户信息">
      <el-form :model="userForm" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="userForm.username" disabled placeholder="用户名不可修改" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="userForm.nickname" />
        </el-form-item>
        <el-form-item label="年龄">
          <el-input-number v-model="userForm.age" :min="1" :max="120" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="userForm.role" placeholder="请选择角色" style="width: 100%" :disabled="userForm.id === currentLoggedUserId || currentLoggedUserId !== 1">
            <el-option label="普通用户" :value="0" />
            <el-option label="管理员" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="注册时间">
          <el-input :model-value="userForm.createTime ? new Date(userForm.createTime).toLocaleString() : ''" disabled />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUserDialog = false">取消</el-button>
        <el-button @click="saveUser" type="primary">确认修改</el-button>
      </template>
    </el-dialog>

    <!-- 审核详情弹窗 -->
    <el-dialog v-model="showReviewDetail" title="审核详情" width="640px">
      <template v-if="currentReview">
        <div v-if="currentReview.requestType === 'new'" class="review-detail">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="书名">{{ currentReview.bookTitle }}</el-descriptions-item>
            <el-descriptions-item label="作者">{{ currentReview.bookAuthor }}</el-descriptions-item>
            <el-descriptions-item label="申请人">{{ currentReview.uploaderNickname }}</el-descriptions-item>
            <el-descriptions-item label="分类">{{ currentReview.bookCategory || '-' }}</el-descriptions-item>
            <el-descriptions-item label="简介" :span="2">{{ currentReview.bookDescription || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>
        <div v-else-if="currentReview.requestType === 'edit'" class="review-detail">
          <h4 style="margin: 0 0 12px;">编辑对比</h4>
          <el-table :data="editDiffRows" border size="small">
            <el-table-column prop="field" label="字段" width="100" />
            <el-table-column prop="oldVal" label="当前值">
              <template #default="scope">
                <span style="color: #999;">{{ scope.row.oldVal || '-' }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="newVal" label="变更为">
              <template #default="scope">
                <span style="color: #409eff; font-weight: 500;">{{ scope.row.newVal || '-' }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <div v-else-if="currentReview.requestType === 'delist'" class="review-detail">
          <el-alert type="warning" :closable="false" show-icon style="margin-bottom: 16px;">
            申请人请求下架此书籍，审核通过后书籍将从公开列表移除。
          </el-alert>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="书名">{{ currentReview.bookTitle }}</el-descriptions-item>
            <el-descriptions-item label="作者">{{ currentReview.bookAuthor }}</el-descriptions-item>
            <el-descriptions-item label="分类">{{ currentReview.bookCategory || '-' }}</el-descriptions-item>
            <el-descriptions-item label="简介" :span="2">{{ currentReview.bookDescription || '-' }}</el-descriptions-item>
          </el-descriptions>
        </div>
      </template>
    </el-dialog>

    <!-- 驳回原因弹窗 -->
    <el-dialog v-model="showRejectDialog" title="驳回审核" width="420px">
      <el-form label-width="80px">
        <el-form-item label="驳回原因">
          <el-input v-model="rejectReason" type="textarea" :rows="3" placeholder="请输入驳回原因（必填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRejectDialog = false">取消</el-button>
        <el-button type="danger" @click="confirmReject">确认驳回</el-button>
      </template>
    </el-dialog>

    <!-- 发言记录 Drawer -->
    <el-drawer v-model="showRecordsDrawer" title="用户发言记录" size="500px">
      <el-tabs v-model="activeRecordTab">
        <el-tab-pane label="书籍评论" name="book">
          <el-empty v-if="userBookComments.length === 0" description="暂无书评" />
          <div v-else class="comment-list">
            <div v-for="c in userBookComments" :key="c.id" class="comment-item">
              <div class="comment-meta">
                <span style="font-weight: bold">《{{ c.bookName || '未知书籍' }}》</span>
                <span class="time">{{ new Date(c.createTime).toLocaleString() }}</span>
              </div>
              <div class="comment-content">
                <el-input v-if="c.isEditing" type="textarea" v-model="c.editContent" rows="3" />
                <span v-else>{{ c.content }}</span>
              </div>
              <div class="comment-actions">
                <el-button v-if="c.isEditing" type="success" size="small" @click="saveBookComment(c)">保存</el-button>
                <el-button v-else type="primary" size="small" @click="c.editContent = c.content; c.isEditing = true">编辑</el-button>
                <el-button type="danger" size="small" @click="deleteBookComment(c.id)">删除</el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>
        <el-tab-pane label="段落评论" name="paragraph">
          <el-empty v-if="userParagraphComments.length === 0" description="暂无段落评论" />
          <div v-else class="comment-list">
            <div v-for="c in userParagraphComments" :key="c.id" class="comment-item">
              <div class="comment-meta">
                <span style="font-weight: bold">《{{ c.bookTitle || '未知书籍' }}》</span> 第{{c.chapterIndex}}章 第{{c.paragraphIndex}}段
                <span class="time">{{ new Date(c.createTime).toLocaleString() }}</span>
              </div>
              <div class="comment-quote" v-if="c.quote">"{{ c.quote }}"</div>
              <div class="comment-content" style="margin-top: 8px;">
                <el-input v-if="c.isEditing" type="textarea" v-model="c.editContent" rows="3" />
                <span v-else>{{ c.content }}</span>
              </div>
              <div class="comment-actions">
                <el-button v-if="c.isEditing" type="success" size="small" @click="saveParagraphComment(c)">保存</el-button>
                <el-button v-else type="primary" size="small" @click="c.editContent = c.content; c.isEditing = true">编辑</el-button>
                <el-button type="danger" size="small" @click="deleteParagraphComment(c.id)">删除</el-button>
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

  </div>
</template>

<script setup>
import { ref, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router' // 引入路由
import request from '../utils/request'
import { getAuthHeaders } from '../utils/authHeaders'
import { ElMessage, ElMessageBox } from 'element-plus'
import {ArrowLeft, Plus, User, CircleCheck, CircleClose} from '@element-plus/icons-vue' // 引入图标
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const activeTab = ref('book')
const uploadHeaders = getAuthHeaders()

// 获取当前登录用户的ID，用于权限判断
const currentLoggedUserId = ref(authStore.user?.id || 0)

// === 数据定义 ===
const bookList = ref([])
const userList = ref([])

// 分页数据
const currentBookPage = ref(1)
const totalBooks = ref(0)
const currentUserPage = ref(1)
const totalUsers = ref(0)

// 弹窗控制
const showBookDialog = ref(false)
const showUserDialog = ref(false)

// 表单数据对象
const bookForm = ref({ id: null, title: '', author: '', category: '', coverUrl: '', filePath: '' })
const userForm = ref({ id: null, username: '', nickname: '', role: 0, age: null, createTime: null })
const reviewList = ref([])
const showReviewDetail = ref(false)
const currentReview = ref(null)
const editDiffRows = ref([])
const showRejectDialog = ref(false)
const rejectReason = ref('')
const rejectTargetId = ref(null)

// 发言记录与封禁状态
const showRecordsDrawer = ref(false)
const activeRecordTab = ref('book')
const currentRecordsUserId = ref(null)
const userBookComments = ref([])
const userParagraphComments = ref([])

// === 1. 公共方法 ===
// 返回首页
const goHome = () => {
  router.push('/')
}

// === 2. 图书管理逻辑 ===
const loadBooks = async (page=1) => {
  try {
    currentBookPage.value = page
    const res = await request.get('/api/sysBook/list', { params: { pageNum: page, pageSize: 10, isAdmin: true } })
    bookList.value = res.data.data.records
    totalBooks.value = res.data.data.total || 0
  } catch (e) {
    console.error(e)
  }
}

// 打开新增图书弹窗
const openAddBook = () => {
  bookForm.value = { id: null, title: '', author: '', category: '', coverUrl: '', filePath: '' }
  showBookDialog.value = true
}

// 打开编辑图书弹窗 (数据回显)
const openEditBook = (row) => {
  // 使用解构赋值复制对象，防止修改表单时直接影响表格显示
  bookForm.value = { ...row }
  showBookDialog.value = true
}

// 保存图书 (自动判断新增还是更新)
const saveBook = async () => {
  if (!bookForm.value.title) return ElMessage.warning('书名不能为空')

  try {
    if (bookForm.value.id) {
      // 有 ID，走更新接口
      await request.put('/api/sysBook/update', bookForm.value)
      ElMessage.success('更新成功')
    } else {
      // 无 ID，走新增接口
      await request.post('/api/sysBook/add', bookForm.value)
      ElMessage.success('添加成功')
    }
    showBookDialog.value = false
    loadBooks(currentBookPage.value)
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

const deleteBook = (id) => {
  ElMessageBox.confirm('确定删除吗？').then(async () => {
    await request.delete(`/api/sysBook/${id}`)
    ElMessage.success('删除成功')
    loadBooks(currentBookPage.value)
  })
}

// === 3. 用户管理逻辑 ===
const loadUsers = async (page = 1) => {
  try {
    currentUserPage.value = page
    const res = await request.get('/api/sysUser/list', { params: { pageNum: page, pageSize: 10 } })
    if (res.data.code === '200') {
      userList.value = res.data.data.records
      totalUsers.value = res.data.data.total || 0
    }
  } catch (e) {
    ElMessage.error('网络请求错误')
  }
}

// 打开编辑用户弹窗
const openEditUser = (row) => {
  userForm.value = { ...row } // 复制数据
  showUserDialog.value = true
}

// 保存用户修改
const saveUser = async () => {
  try {
    // 调用更新接口 (带上操作人 ID)
    const res = await request.post(`/api/sysUser/adminUpdate?operatorId=${currentLoggedUserId.value}`, userForm.value)
    if (res.data.code === '200') {
      ElMessage.success('用户信息修改成功')
      showUserDialog.value = false
      loadUsers(currentUserPage.value) // 保留当前页刷新
    } else {
      ElMessage.error(res.data.msg)
    }
  } catch (e) {
    ElMessage.error('修改失败')
  }
}

const deleteUser = (id) => {
  if (id === 1) {
    ElMessage.warning('超级管理员账号不可删除')
    return
  }
  ElMessageBox.confirm('确定删除该用户吗？此操作不可恢复。', '警告', { type: 'warning' })
      .then(async () => {
        const res = await request.delete(`/api/sysUser/${id}`)
        if (res.data.code === '200') {
          ElMessage.success('用户删除成功')
          loadUsers(currentUserPage.value)
        } else {
          ElMessage.error(res.data.msg)
        }
      })
      .catch(() => {})
}

// 封禁/解封
const toggleBan = async (row) => {
  if (row.id === 1) return ElMessage.warning('无法封禁超级管理员账号')
  const newBanStatus = row.isBanned !== 1
  const actionName = newBanStatus ? '封禁' : '解封'
  try {
    await ElMessageBox.confirm(`确定要${actionName}该用户吗？`, '提示', { type: 'warning' })
    const res = await request.post(`/api/sysUser/ban/${row.id}?banned=${newBanStatus}`)
    if (res.data.code === '200') {
      ElMessage.success(res.data.data)
      loadUsers(currentUserPage.value)
    } else {
      ElMessage.error(res.data.msg)
    }
  } catch (e) {}
}

const openUserRecords = (userId) => {
  currentRecordsUserId.value = userId
  showRecordsDrawer.value = true
  loadUserRecords()
}

const loadUserRecords = async () => {
  try {
    const res1 = await request.get(`/api/comment/user/${currentRecordsUserId.value}`)
    userBookComments.value = res1.data.data.map(c => ({ ...c, isEditing: false, editContent: '' }))
    
    const res2 = await request.get(`/api/paragraphComment/user/${currentRecordsUserId.value}`)
    userParagraphComments.value = res2.data.data.map(c => ({ ...c, isEditing: false, editContent: '' }))
  } catch (e) {
    ElMessage.error('加载记录失败')
  }
}

const saveBookComment = async (c) => {
  try {
    const res = await request.put('/api/comment/update', { id: c.id, content: c.editContent })
    if (res.data.code === '200') {
      ElMessage.success('修改成功')
      c.content = c.editContent
      c.isEditing = false
    } else ElMessage.error(res.data.msg)
  } catch (e) { ElMessage.error('网络请求失败') }
}

const deleteBookComment = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除该条评论吗？', '提示', { type: 'warning' })
    const res = await request.delete(`/api/comment/${id}`)
    if (res.data.code === '200') {
        ElMessage.success('删除成功')
        loadUserRecords()
    } else ElMessage.error(res.data.msg)
  } catch (e) {}
}

const saveParagraphComment = async (c) => {
  try {
    const res = await request.put('/api/paragraphComment/update', { id: c.id, content: c.editContent })
    if (res.data.code === '200') {
      ElMessage.success('修改成功')
      c.content = c.editContent
      c.isEditing = false
    } else ElMessage.error(res.data.msg)
  } catch (e) { ElMessage.error('网络请求失败') }
}

const deleteParagraphComment = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除该条评论吗？', '提示', { type: 'warning' })
    const userStr = localStorage.getItem('user')
    const uid = userStr ? JSON.parse(userStr).id : 0
    const res = await request.delete(`/api/paragraphComment/${id}?userId=${uid}`)
    if (res.data.code === '200') {
        ElMessage.success('删除成功')
        loadUserRecords()
    } else ElMessage.error(res.data.msg)
  } catch (e) {}
}

// === 4. 书籍审核逻辑 ===
const reqTypeText = (t) => ({ new: '新书公开', edit: '编辑审核', delist: '下架审核' }[t] || t)
const reqTypeTag = (t) => ({ new: 'success', edit: 'warning', delist: 'danger' }[t] || 'info')

const loadReviewList = async () => {
  try {
    const res = await request.get('/api/sysBook/reviewRequests/pending')
    if (res.data.code === '200') {
      reviewList.value = res.data.data
    }
  } catch (e) {
    console.error('加载审核列表失败', e)
  }
}

const reviewRequest = async (id, action) => {
  const label = action === 'approve' ? '通过' : '驳回'
  try {
    await ElMessageBox.confirm(`确定${label}该请求吗？`, '审核确认', {
      confirmButtonText: label,
      cancelButtonText: '取消',
      type: action === 'approve' ? 'success' : 'warning'
    })
    const res = await request.post(`/api/sysBook/reviewRequest/${id}`, { action })
    if (res.data.code === '200') {
      ElMessage.success(res.data.data || `已${label}`)
      loadReviewList()
    } else {
      ElMessage.error(res.data.msg)
    }
  } catch (e) {
    // 用户取消
  }
}

const openReviewDetail = (row) => {
  currentReview.value = row
  editDiffRows.value = []
  if (row.requestType === 'edit' && row.newBookData) {
    let newData = {}
    try {
      newData = JSON.parse(row.newBookData)
    } catch (e) {
      console.error('Failed to parse newBookData:', e)
    }
    // 将 SQL JOIN 的下划线字段映射到驼峰
    const oldBook = {
      title: row.bookTitle,
      author: row.bookAuthor,
      description: row.bookDescription,
      category: row.bookCategory,
      coverUrl: row.bookCoverUrl,
      filePath: row.bookFilePath
    }
    const fieldMap = { title: '书名', author: '作者', description: '简介', category: '分类', tags: '标签', coverUrl: '封面', filePath: '文件' }
    for (const [key, label] of Object.entries(fieldMap)) {
      const oldVal = oldBook[key] || ''
      const newVal = newData[key] ?? ''
      if (newVal !== undefined && newVal !== oldVal) {
        editDiffRows.value.push({ field: label, oldVal, newVal })
      }
    }
  }
  showReviewDetail.value = true
}

const openRejectDialog = (row) => {
  rejectTargetId.value = row.id
  rejectReason.value = ''
  showRejectDialog.value = true
}

const confirmReject = async () => {
  if (!rejectReason.value.trim()) {
    ElMessage.warning('请输入驳回原因')
    return
  }
  try {
    const res = await request.post(`/api/sysBook/reviewRequest/${rejectTargetId.value}`, {
      action: 'reject',
      rejectReason: rejectReason.value.trim()
    })
    if (res.data.code === '200') {
      ElMessage.success('已驳回')
      showRejectDialog.value = false
      loadReviewList()
    } else {
      ElMessage.error(res.data.msg)
    }
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

watch(activeTab, (newTab) => {
  if (newTab === 'user') loadUsers()
  else if (newTab === 'book') loadBooks()
  else if (newTab === 'review') loadReviewList()
})

onMounted(() => {
  loadBooks()
  loadUsers()
})
</script>

<style scoped>
.admin-container {
  padding: 18px 24px;
}



.toolbar { margin-bottom: 18px; }

/* === 表格微调 === */

/* === Tab 微调 === */

/* === 上传图片 === */
.avatar-uploader {
  border: 1px dashed #d4c8ba;
  border-radius: 4px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  width: 100px;
  height: 140px;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: border-color 0.2s;
}
.avatar-uploader:hover {
  border-color: #8b6f52;
}
.upload-img-preview {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.avatar-uploader-icon {
  font-size: 24px;
  color: #b5a99c;
}
.back-btn:hover { color: #8b6f52; }

/* === Premium Tabs === */
:deep(.premium-tabs) {
  margin-top: 10px;
}
:deep(.premium-tabs .el-tabs__header) {
  border-bottom: 2px solid rgba(255, 255, 255, 0.4);
  margin-bottom: 24px;
}
:deep(.premium-tabs .el-tabs__nav-wrap::after) {
  display: none;
}
:deep(.premium-tabs .el-tabs__item) {
  font-size: 16px;
  font-weight: 500;
  color: #8c827a;
  transition: all 0.3s ease;
  padding: 0 24px;
  height: 48px;
  line-height: 48px;
}
:deep(.premium-tabs .el-tabs__item.is-active) {
  color: #5c4b40;
}
:deep(.premium-tabs .el-tabs__item:hover) {
  color: #7a6e63;
}
:deep(.premium-tabs .el-tabs__active-bar) {
  background-color: #8b6f52;
  height: 3px;
  border-radius: 3px;
}

/* === Premium Table === */
:deep(.premium-table) {
  background: transparent !important;
  border-radius: 12px;
  overflow: hidden;
  --el-table-border-color: rgba(255, 255, 255, 0.2);
  --el-table-row-hover-bg-color: rgba(255, 255, 255, 0.6);
}
:deep(.premium-table .el-table__inner-wrapper::before) {
  display: none; /* Remove bottom line */
}
:deep(.premium-table th.el-table__cell) {
  background: rgba(255, 255, 255, 0.4) !important;
  backdrop-filter: blur(10px);
  color: #5c4b40;
  font-weight: 600;
  text-transform: uppercase;
  font-size: 13px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.5) !important;
  padding: 12px 0;
}
:deep(.premium-table tr) {
  background: rgba(255, 255, 255, 0.2) !important;
  transition: all 0.3s ease;
}
:deep(.premium-table td.el-table__cell) {
  border-bottom: 1px solid rgba(255, 255, 255, 0.4) !important;
  padding: 16px 0;
  color: #3e322a;
}
:deep(.premium-table .el-table__row:hover > td.el-table__cell) {
  background: rgba(255, 255, 255, 0.6) !important;
}

/* === Premium Tags Override === */
:deep(.el-tag) {
  border-width: 1px;
  font-weight: 600;
}
:deep(.el-tag--success) {
  color: #5c855c;
  background-color: rgba(92, 133, 92, 0.1);
  border-color: rgba(92, 133, 92, 0.3);
}
:deep(.el-tag--danger) {
  color: #b35546;
  background-color: rgba(179, 85, 70, 0.1);
  border-color: rgba(179, 85, 70, 0.3);
}
:deep(.el-tag--warning) {
  color: #b37f46;
  background-color: rgba(179, 127, 70, 0.1);
  border-color: rgba(179, 127, 70, 0.3);
}
:deep(.el-tag--info) {
  color: #6a5a4f;
  background-color: rgba(106, 90, 79, 0.1);
  border-color: rgba(106, 90, 79, 0.3);
}

/* === Premium Buttons Override === */
:deep(.el-button.is-plain) {
  font-weight: 500;
  border-radius: 6px;
  transition: all 0.2s;
}
:deep(.el-button--primary.is-plain) {
  color: #7b6248;
  background: rgba(139, 111, 82, 0.05);
  border-color: rgba(139, 111, 82, 0.3);
}
:deep(.el-button--primary.is-plain:hover) {
  color: #5a4531;
  background: rgba(139, 111, 82, 0.15);
  border-color: rgba(139, 111, 82, 0.5);
}
:deep(.el-button--danger.is-plain) {
  color: #b35546;
  background: rgba(212, 107, 90, 0.05);
  border-color: rgba(212, 107, 90, 0.3);
}
:deep(.el-button--danger.is-plain:hover) {
  color: #8f4033;
  background: rgba(212, 107, 90, 0.15);
  border-color: rgba(212, 107, 90, 0.5);
}
:deep(.el-button--warning.is-plain) {
  color: #b37f46;
  background: rgba(212, 154, 90, 0.05);
  border-color: rgba(212, 154, 90, 0.3);
}
:deep(.el-button--warning.is-plain:hover) {
  color: #8c6133;
  background: rgba(212, 154, 90, 0.15);
  border-color: rgba(212, 154, 90, 0.5);
}
:deep(.el-button--success.is-plain) {
  color: #5c855c;
  background: rgba(123, 156, 123, 0.05);
  border-color: rgba(123, 156, 123, 0.3);
}
:deep(.el-button--success.is-plain:hover) {
  color: #436343;
  background: rgba(123, 156, 123, 0.15);
  border-color: rgba(123, 156, 123, 0.5);
}
:deep(.el-button--info.is-plain) {
  color: #6a5a4f;
  background: rgba(122, 110, 99, 0.05);
  border-color: rgba(122, 110, 99, 0.3);
}
:deep(.el-button--info.is-plain:hover) {
  color: #4a3e35;
  background: rgba(122, 110, 99, 0.15);
  border-color: rgba(122, 110, 99, 0.5);
}

:deep(.el-dialog) {
  background: rgba(255, 255, 255, 0.85) !important;
  backdrop-filter: blur(30px) !important;
  border-radius: 16px !important;
}
.header-title-box {
  display: flex;
  flex-direction: column;
}
.header-title-box h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #2e2520;
}

/* === 发言记录样式 === */
.comment-list { padding: 10px; }
.comment-item { margin-bottom: 20px; border-bottom: 1px solid #eee; padding-bottom: 15px; }
.comment-meta { display: flex; justify-content: space-between; font-size: 13px; color: #4a3e35; margin-bottom: 8px;}
.comment-quote { background: #f5f5f5; padding: 6px 10px; font-size: 13px; color: #888; border-left: 3px solid #ccc; }
.comment-content { font-size: 14px; color: #2a211c; margin-bottom: 10px; }
.comment-actions { text-align: right; }

/* === 审核详情 === */
.review-detail { padding: 8px 0; }
.review-detail h4 { color: #2e2520; font-size: 15px; }
</style>
