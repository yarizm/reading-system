import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

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
        component: () => import('../views/Shelf.vue')
    },
    {
        path: '/admin',
        name: 'Admin',
        component: () => import('../views/Admin.vue')
    },
    {
        path: '/profile',
        name: 'Profile',
        component: () => import('../views/Profile.vue')
    },
    {
        path: '/book/:id',
        name: 'BookDetail',
        component: () => import('../views/BookDetail.vue')
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
        component: () => import('../views/MyBooks.vue')
    },
    {
        path: '/friends',
        name: 'Friends',
        component: () => import('../views/Friends.vue')
    },
    {
        path: '/chat/:friendId',
        name: 'Chat',
        component: () => import('../views/Chat.vue')
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

const publicRoutes = ['Home', 'Login', 'BookDetail']

router.beforeEach(async (to, from, next) => {
    const authStore = useAuthStore()

    if (to.name === 'Login') {
        next()
        return
    }

    if (!authStore.isLoggedIn) {
        if (publicRoutes.includes(to.name)) {
            next()
        } else {
            next({ name: 'Login' })
        }
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

export default router