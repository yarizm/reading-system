<script setup>
import { ref } from 'vue';
// 引入微软的工业级 SSE 库
import { fetchEventSource } from '@microsoft/fetch-event-source';

const selectedText = ref('量子力学是描述微观物质的物理学理论...');
const customMode = ref('用大白话解释这段话，越通俗越好');
const aiResponse = ref('');
const isLoading = ref(false);

const startAnalysis = async () => {
  if (!selectedText.value) return;

  aiResponse.value = '';
  isLoading.value = true;

  try {
    // 使用这个库代替原生的 fetch
    await fetchEventSource('http://localhost:8090/api/difyreading/analyze', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Accept': 'text/event-stream'
      },
      body: JSON.stringify({
        text: selectedText.value,
        mode: customMode.value
      }),
      // 核心魔法：每当后端发来一条完整的 data: 事件，这里就会触发一次！
      onmessage(event) {
        // Dify 发过来的完整 JSON 已经帮我们解析好放在 event.data 里了
        const dataJson = JSON.parse(event.data);

        console.log("稳如老狗收到事件:", dataJson.event, dataJson);

        // 提取打字机文本
        if (dataJson.event === 'text_chunk') {
          // 有些版本的 Dify 文本在 data.answer，有些在 data.text，兼容一下
          const newText = dataJson.data?.text || dataJson.data?.answer || '';
          aiResponse.value += newText;
        }

        // 如果工作流执行出错
        if (dataJson.event === 'error') {
          console.error("Dify 报错:", dataJson);
          aiResponse.value += '\n[AI 解析出错，请稍后再试]';
        }
      },
      onclose() {
        console.log("数据流完美结束！");
        isLoading.value = false;
      },
      onerror(err) {
        console.error("连接中断或报错:", err);
        isLoading.value = false;
        throw err; // 抛出异常阻止库疯狂重连
      }
    });

  } catch (error) {
    console.error('请求出错:', error);
    aiResponse.value += '\n[解析发生严重错误，请检查网络或后端服务。]';
    isLoading.value = false;
  }
};
</script>

<template>
  <div class="reading-assistant">
    <!-- 1. 模拟用户选中的书籍文本 -->
    <textarea v-model="selectedText" rows="4" placeholder="请填入你要分析的段落..."></textarea>

    <!-- 2. 核心自定义体验：让用户选模式！ -->
    <div class="controls">
      <select v-model="customMode">
        <option value="用大白话解释这段话，越通俗越好">大白话解释</option>
        <option value="从学术角度深层剖析这段话，指出核心论点">学术精读</option>
        <option value="用鲁迅阴阳怪气的语气锐评这段话">鲁迅锐评</option>
        <option value="找出这段话里的所有人名、地名、专业术语，列成表格">提取实体表</option>
      </select>

      <!-- 按钮绑定点击事件，加载中禁用 -->
      <button @click="startAnalysis" :disabled="isLoading">
        {{ isLoading ? 'AI 正在思考...' : '开始 AI 解析' }}
      </button>
    </div>

    <!-- 3. AI 结果展示区（打字机效果） -->
    <div class="result-box" v-if="aiResponse">
      <h3>🤖 AI 解析结果：</h3>
      <!-- 使用 pre-wrap 保留换行符，让排版更美观 -->
      <p style="white-space: pre-wrap; line-height: 1.6;">{{ aiResponse }}</p>
    </div>
  </div>
</template>

<style scoped>
.reading-assistant { max-width: 600px; margin: 20px auto; font-family: sans-serif;}
textarea { width: 100%; padding: 10px; margin-bottom: 10px; border-radius: 4px; border: 1px solid #ccc;}
.controls { display: flex; gap: 10px; margin-bottom: 20px; }
select, button { padding: 8px 12px; border-radius: 4px; border: 1px solid #ccc; background: white;}
button { background: #007bff; color: white; border: none; cursor: pointer; }
button:disabled { background: #ccc; cursor: not-allowed; }
.result-box { padding: 15px; background: #f8f9fa; border-radius: 8px; border-left: 4px solid #007bff;}
</style>