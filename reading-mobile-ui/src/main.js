import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import axios from 'axios'
import { createPinia } from 'pinia'

// Vant 组件
import {
  Button, NavBar, Tabbar, TabbarItem, Search, Swipe, SwipeItem,
  Card, Tag, Icon, Image as VanImage, List, PullRefresh, Cell, CellGroup,
  Tab, Tabs, Field, Form, Toast, Dialog, ActionSheet, Popup, Skeleton,
  Loading, Empty, Badge, Divider, Slider, Switch, Rate, Progress,
  FloatingBubble, BackTop, Notify, ImagePreview, Uploader, DropdownMenu,
  DropdownItem, SwipeCell, Collapse, CollapseItem, Grid, GridItem,
  ShareSheet, TextEllipsis, Sticky, ConfigProvider, Picker, Popover,
  ActionBar, ActionBarIcon, ActionBarButton
} from 'vant'
import 'vant/lib/index.css'
import '@vant/touch-emulator'

import './styles/index.css'

axios.interceptors.request.use((config) => {
  const userStr = localStorage.getItem('user')
  if (userStr) {
    try {
      const token = JSON.parse(userStr).token
      if (token) {
        config.headers.Authorization = `Bearer ${token}`
      }
    } catch (e) {
      localStorage.removeItem('user')
    }
  }
  return config
})

// 全局响应拦截器，捕获 401 登录过期并跳转
axios.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('user')
      // 避免重复跳转
      if (!window.location.pathname.includes('/login')) {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

const app = createApp(App)
const pinia = createPinia()

// 注册 Vant 组件
const vantComponents = [
  Button, NavBar, Tabbar, TabbarItem, Search, Swipe, SwipeItem,
  Card, Tag, Icon, VanImage, List, PullRefresh, Cell, CellGroup,
  Tab, Tabs, Field, Form, Toast, Dialog, ActionSheet, Popup, Skeleton,
  Loading, Empty, Badge, Divider, Slider, Switch, Rate, Progress,
  FloatingBubble, BackTop, Notify, ImagePreview, Uploader, DropdownMenu,
  DropdownItem, SwipeCell, Collapse, CollapseItem, Grid, GridItem,
  ShareSheet, TextEllipsis, Sticky, ConfigProvider, Picker, Popover,
  ActionBar, ActionBarIcon, ActionBarButton
]
vantComponents.forEach(c => app.use(c))

app.use(pinia)
app.use(router)
app.mount('#app')
