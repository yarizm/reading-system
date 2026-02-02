import { createApp } from 'vue'
import App from './App.vue'

// 1. 引入 Element Plus 及其样式
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
// 引入图标库
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

// 2. 引入路由配置 (稍后创建)
import router from './router'

// 3. 引入全局样式 (可选)
import './style.css'

const app = createApp(App)

// 注册所有图标
for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
    app.component(key, component)
}

app.use(router)
app.use(ElementPlus)
app.mount('#app')