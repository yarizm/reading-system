<template>
  <div class="import-page">
    <van-nav-bar title="导入笔记" left-arrow @click-left="$router.back()" />

    <div class="content">
      <van-cell-group title="选择格式">
        <van-radio-group v-model="selectedFormat" direction="horizontal" style="padding: 10px 16px;">
          <van-radio name="kindle">Kindle</van-radio>
          <van-radio name="weread">微信读书</van-radio>
          <van-radio name="csv">CSV</van-radio>
          <van-radio name="json">JSON</van-radio>
        </van-radio-group>
      </van-cell-group>

      <van-cell-group title="上传文件" style="margin-top: 10px;">
        <van-cell>
          <input type="file" accept=".txt,.csv,.json,.md" @change="handleFile" style="width: 100%;" />
        </van-cell>
      </van-cell-group>

      <van-button type="primary" block style="margin: 16px;" :loading="importing" @click="doImport">
        开始导入
      </van-button>

      <div v-if="importResult" class="result-card">
        <van-cell title="导入成功" :value="importResult.imported + ' 条'" icon="success" />
        <van-cell title="跳过" :value="importResult.skipped + ' 条'" icon="warning-o" />
        <van-button type="primary" plain block style="margin-top: 10px;" @click="$router.push('/notes')">查看笔记</van-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { showToast } from 'vant'
import request from '../utils/request'

const selectedFile = ref(null)
const selectedFormat = ref('kindle')
const importing = ref(false)
const importResult = ref(null)

const handleFile = (e) => { selectedFile.value = e.target.files[0] }

const doImport = async () => {
  if (!selectedFile.value) { showToast('请先选择文件'); return }
  importing.value = true
  try {
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    formData.append('format', selectedFormat.value)
    const res = await request.post('/api/import/upload', formData)
    if (res.data.code === '200') {
      importResult.value = res.data.data
      showToast('导入完成')
    } else { showToast(res.data.msg || '导入失败') }
  } catch (e) { showToast('导入失败') }
  finally { importing.value = false }
}
</script>

<style scoped>
.import-page { min-height: 100vh; background: #f5f5f5; }
.content { padding: 10px; }
.result-card { background: #fff; border-radius: 8px; padding: 10px; margin-top: 10px; }
</style>
