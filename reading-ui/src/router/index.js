import { createRouter, createWebHistory } from 'vue-router'

// 定义路由规则
const routes = [
    {
        path: '/',
        name: 'Home',
        // 懒加载组件 (稍后创建)
        component: () => import('../views/Home.vue')
    },
    {
        path: '/login',
        name: 'Login',
        component: () => import('../views/Login.vue')
    },
    {
        path: '/read/:id', // :id 是动态参数
        name: 'Read',
        component: () => import('../views/Read.vue')
    },
    {
        path: '/shelf',
        name: 'Shelf',
        component: () => import('../views/Shelf.vue')
    },
    {
        path: '/admin',
        name: 'Admin',
        component: () => import('../views/Admin.vue'),
        // === 新增：独享路由守卫 ===
        beforeEnter: (to, from, next) => {
            // 1. 获取用户信息
            const userStr = localStorage.getItem('user')
            const user = userStr ? JSON.parse(userStr) : null

            // 2. 判断逻辑
            if (user && user.role === 1) {
                // 是管理员，放行
                next()
            } else {
                // 不是管理员，或者没登录
                // 可以弹窗提示，或者直接踢回首页
                alert('权限不足：只有管理员可以访问此页面')
                next('/') // 跳转回首页
            }
        }
    },
    {
        path: '/profile',
        name: 'Profile',
        component: () => import('../views/Profile.vue') // 简单的个人信息修改页
    },
    // 在 routes 数组里添加：
    {
        path: '/book/:id',
        name: 'BookDetail',
        component: () => import('../views/BookDetail.vue')
    },
]

// 创建路由实例
const router = createRouter({
    history: createWebHistory(),
    routes
})

export default router