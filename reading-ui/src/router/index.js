import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import NProgress from 'nprogress'
import 'nprogress/nprogress.css'

// 进度条配置
NProgress.configure({ showSpinner: false, speed: 400, minimum: 0.15 })

// 定义路由规则
const routes = [
    {
        path: '/',
        name: 'Home',
        component: () => import('../views/Home.vue')
    },
    {
        path: '/login',
        name: 'Login',
        component: () => import('../views/Login.vue')
    },
    {
        path: '/read/:id',
        name: 'Read',
        component: () => import('../views/Read.vue')
    },
    {
        path: '/shelf',
        name: 'Shelf',
        component: () => import('../views/Shelf.vue'),
        meta: { requireAuth: true }
    },
    {
        path: '/admin',
        name: 'Admin',
        component: () => import('../views/Admin.vue'),
        meta: { requireAuth: true }
    },
    {
        path: '/profile',
        name: 'Profile',
        component: () => import('../views/Profile.vue'),
        meta: { requireAuth: true }
    },
    {
        path: '/book/:id',
        name: 'BookDetail',
        component: () => import('../views/BookDetail.vue')
    },
    {
        path: '/insights',
        name: 'Insights',
        component: () => import('../views/Insights.vue')
    },
    {
        path: '/shelf/import/:shareCode',
        name: 'ImportBooklist',
        component: () => import('../views/ImportBooklist.vue')
    },
    {
        path: '/user/:id',
        name: 'UserProfile',
        component: () => import('../views/UserProfile.vue')
    },
    {
        path: '/my-books',
        name: 'MyBooks',
        component: () => import('../views/MyBooks.vue'),
        meta: { requireAuth: true }
    },
    {
        path: '/friends',
        name: 'Friends',
        component: () => import('../views/Friends.vue'),
        meta: { requireAuth: true }
    },
    {
        path: '/chat/:friendId',
        name: 'Chat',
        component: () => import('../views/Chat.vue'),
        meta: { requireAuth: true }
    },
]

const router = createRouter({
    history: createWebHistory(),
    routes,
    scrollBehavior(to, from, savedPosition) {
        if (savedPosition) {
            return savedPosition
        }
        return { top: 0 }
    }
})

router.beforeEach(async (to, from, next) => {
    // 开启进度条
    if (to.path !== from.path) {
        NProgress.start()
    }
    const authStore = useAuthStore()

    if (to.meta.requireAuth && !authStore.isLoggedIn) {
        next({ name: 'Login' })
        return
    }

    if (to.name === 'Admin') {
        await authStore.fetchAndVerify()
        if (authStore.isAdmin) {
            next()
        } else {
            ElMessage.error('权限不足：只有管理员可以访问此页面')
            next('/')
        }
        return
    }

    next()
})

router.afterEach(() => {
    // 关闭进度条
    NProgress.done()
})

export default router
