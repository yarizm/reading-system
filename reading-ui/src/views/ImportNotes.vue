<template>
  <div class="page-glass-container">
    <div class="import-page">
      <h2>📥 导入笔记</h2>

      <div class="upload-section">
        <el-upload
          drag
          :auto-upload="false"
          :on-change="handleFileChange"
          :file-list="fileList"
          :limit="1"
          accept=".txt,.csv,.json,.md"
        >
          <el-icon style="font-size: 48px; color: #409eff"><Upload /></el-icon>
          <div>拖拽文件到此处，或<em>点击上传</em></div>
          <template #tip>
            <div style="color: #999; font-size: 13px">支持 Kindle Clippings.txt、微信读书导出、CSV、JSON 格式</div>
          </template>
        </el-upload>

        <div class="format-select" style="margin-top: 16px">
          <el-radio-group v-model="selectedFormat">
            <el-radio-button value="kindle">Kindle</el-radio-button>
            <el-radio-button value="weread">微信读书</el-radio-button>
            <el-radio-button value="csv">CSV</el-radio-button>
            <el-radio-button value="json">JSON</el-radio-button>
          </el-radio-group>
        </div>

        <el-button type="primary" style="margin-top: 16px" :loading="importing" @click="doImport">
          开始导入
        </el-button>
      </div>

      <div v-if="importResult" class="result-section" style="margin-top: 20px">
        <el-alert
          :title="'导入完成：成功 ' + importResult.imported + ' 条，跳过 ' + importResult.skipped + ' 条'"
          :type="importResult.errors && importResult.errors.length > 0 ? 'warning' : 'success'"
          show-icon
        />
        <div v-if="importResult.errors && importResult.errors.length > 0" style="margin-top: 10px">
          <div v-for="(err, i) in importResult.errors" :key="i" style="color: #e74c3c; font-size: 13px">{{ err }}</div>
        </div>
        <el-button type="primary" style="margin-top: 16px" @click="$router.push('/notes')">查看笔记</el-button>
      </div>

      <div class="format-guide" style="margin-top: 30px">
        <h3>格式说明</h3>
        <el-collapse>
          <el-collapse-item title="Kindle Clippings.txt" name="kindle">
            <p>从 Kindle 设备导出的 My Clippings.txt 文件，直接上传即可。</p>
          </el-collapse-item>
          <el-collapse-item title="微信读书导出" name="weread">
            <p>微信读书导出的 Markdown 格式文件，以 ## 书名为标题，> 为引用选文。</p>
          </el-collapse-item>
          <el-collapse-item title="CSV 格式" name="csv">
            <p>CSV 文件，表头为 book_title, selected_text, content。至少需要 book_title 和 selected_text 两列。</p>
          </el-collapse-item>
          <el-collapse-item title="JSON 格式" name="json">
            <p>JSON 数组，每项包含 bookTitle、selectedText、content 字段。</p>
          </el-collapse-item>
        </el-collapse>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Upload } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '../utils/request'

const fileList = ref([])
const selectedFile = ref(null)
const selectedFormat = ref('kindle')
const importing = ref(false)
const importResult = ref(null)

const handleFileChange = (file) => {
  selectedFile.value = file.raw
}

const doImport = async () => {
  if (!selectedFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  importing.value = true
  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    formData.append('format', selectedFormat.value)
    const res = await request.post('/api/import/upload', formData)
    if (res.data.code === '200') {
      importResult.value = res.data.data
      ElMessage.success('导入完成')
    } else {
      ElMessage.error(res.data.msg || '导入失败')
    }
  } catch (e) {
    ElMessage.error('导入失败')
  } finally {
    importing.value = false
  }
}
</script>

<style scoped>
.import-page { max-width: 700px; margin: 0 auto; padding: 20px; }
.import-page h2 { margin-bottom: 20px; }
.upload-section { background: #fff; border-radius: 12px; padding: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.result-section { background: #fff; border-radius: 12px; padding: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.format-guide { background: #fff; border-radius: 12px; padding: 24px; box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.format-guide h3 { margin: 0 0 12px; }
</style>
