<template>
  <div class="admin-container">
    <div class="admin-header">
      <h2>后台管理系统</h2>
      <el-button type="default" @click="goHome">
        <el-icon style="margin-right: 5px"><HomeFilled /></el-icon>
        返回首页
      </el-button>
    </div>

    <el-tabs v-model="activeTab" type="border-card">

      <el-tab-pane label="图书管理" name="book">
        <div class="toolbar">
          <el-button type="primary" @click="openAddBook">上传新书</el-button>
        </div>

        <el-table :data="bookList" border stripe>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="title" label="书名" />
          <el-table-column prop="author" label="作者" />
          <el-table-column prop="category" label="分类" />
          <el-table-column label="封面" width="100">
            <template #default="scope">
              <img v-if="scope.row.coverUrl" :src="scope.row.coverUrl" style="width: 40px; height: 40px; object-fit: cover; border-radius: 4px"/>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="scope">
              <el-button type="primary" size="small" @click="openEditBook(scope.row)">编辑</el-button>
              <el-button type="danger" size="small" @click="deleteBook(scope.row.id)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination layout="prev, pager, next" :total="100" @current-change="loadBooks" />
      </el-tab-pane>

      <el-tab-pane label="用户管理" name="user">
        <el-table :data="userList" border stripe>
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="username" label="用户名" />
          <el-table-column prop="nickname" label="昵称" />
          <el-table-column prop="role" label="角色">
            <template #default="scope">
              <el-tag :type="scope.row.role === 1 ? 'danger' : 'success'">
                {{ scope.row.role === 1 ? '管理员' : '普通用户' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="scope">
              <el-button type="primary" size="small" @click="openEditUser(scope.row)">编辑</el-button>

              <el-button
                  type="danger"
                  size="small"
                  @click="deleteUser(scope.row.id)"
                  :disabled="scope.row.role === 1 || scope.row.id === 1"
              >删除</el-button>
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
              :on-success="(res) => bookForm.coverUrl = res.data"
              :show-file-list="false"
              class="avatar-uploader"
          >
            <img v-if="bookForm.coverUrl" :src="bookForm.coverUrl" class="upload-img-preview"/>
            <el-icon v-else class="avatar-uploader-icon"><Plus /></el-icon>
            <div v-if="!bookForm.coverUrl" class="el-upload__text">点击上传</div>
          </el-upload>
        </el-form-item>

        <el-form-item label="电子书">
          <el-upload
              action="/api/sysBook/upload"
              :on-success="(res) => bookForm.filePath = res.data"
              :show-file-list="false"
          >
            <el-button size="small" type="primary">
              {{ bookForm.filePath ? '文件已上传 (点击替换)' : '上传 TXT/PDF' }}
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
        <el-form-item label="角色">
          <el-select v-model="userForm.role" placeholder="请选择角色" style="width: 100%">
            <el-option label="普通用户" :value="0" />
            <el-option label="管理员" :value="1" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUserDialog = false">取消</el-button>
        <el-button @click="saveUser" type="primary">确认修改</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, onMounted, watch, reactive } from 'vue'
import { useRouter } from 'vue-router' // 引入路由
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { HomeFilled, Plus } from '@element-plus/icons-vue' // 引入图标

const router = useRouter()
const activeTab = ref('book')

// === 数据定义 ===
const bookList = ref([])
const userList = ref([])

// 弹窗控制
const showBookDialog = ref(false)
const showUserDialog = ref(false)

// 表单数据对象
const bookForm = ref({ id: null, title: '', author: '', category: '', coverUrl: '', filePath: '' })
const userForm = ref({ id: null, username: '', nickname: '', role: 0 })

// === 1. 公共方法 ===
// 返回首页
const goHome = () => {
  router.push('/')
}

// === 2. 图书管理逻辑 ===
const loadBooks = async (page=1) => {
  try {
    const res = await axios.get('/api/sysBook/list', { params: { pageNum: page, pageSize: 10 } })
    bookList.value = res.data.data.records
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
      await axios.put('/api/sysBook/update', bookForm.value)
      ElMessage.success('更新成功')
    } else {
      // 无 ID，走新增接口
      await axios.post('/api/sysBook/add', bookForm.value)
      ElMessage.success('添加成功')
    }
    showBookDialog.value = false
    loadBooks()
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

const deleteBook = (id) => {
  ElMessageBox.confirm('确定删除吗？').then(async () => {
    await axios.delete(`/api/sysBook/${id}`)
    ElMessage.success('删除成功')
    loadBooks()
  })
}

// === 3. 用户管理逻辑 ===
const loadUsers = async (page = 1) => {
  try {
    const res = await axios.get('/api/sysUser/list', { params: { pageNum: page, pageSize: 10 } })
    if (res.data.code === '200') {
      userList.value = res.data.data.records
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
    // 调用更新接口 (复用之前的 /sysUser/update 接口)
    await axios.post('/api/sysUser/update', userForm.value)
    ElMessage.success('用户信息修改成功')
    showUserDialog.value = false
    loadUsers() // 刷新列表
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
        const res = await axios.delete(`/api/sysUser/${id}`)
        if (res.data.code === '200') {
          ElMessage.success('用户删除成功')
          loadUsers()
        } else {
          ElMessage.error(res.data.msg)
        }
      })
      .catch(() => {})
}

// === 监听与初始化 ===
watch(activeTab, (newTab) => {
  if (newTab === 'user') loadUsers()
  else if (newTab === 'book') loadBooks()
})

onMounted(() => {
  loadBooks()
  loadUsers()
})
</script>

<style scoped>
.admin-container {
  padding: 18px 24px;
  max-width: 1200px;
  margin: 0 auto;
}

/* === 头部 === */
.admin-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 22px;
  border-bottom: 1px solid #e8e0d6;
  padding-bottom: 12px;
}
.admin-header h2 {
  font-family: 'Noto Serif SC', serif;
  color: #2e2520;
  font-weight: 600;
  font-size: 22px;
}

.toolbar { margin-bottom: 18px; }

/* === 表格微调 === */
:deep(.el-table) {
  border: 1px solid #e8e0d6;
  border-radius: 4px;
}
:deep(.el-table th.el-table__cell) {
  background-color: #f5f0e8;
  color: #4a3828;
  font-weight: 600;
}
:deep(.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell) {
  background-color: #fcfaf6;
}

/* === Tab 微调 === */
:deep(.el-tabs--border-card) {
  border-color: #e8e0d6;
}
:deep(.el-tabs__item.is-active) {
  color: #5a4435;
}

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
</style>