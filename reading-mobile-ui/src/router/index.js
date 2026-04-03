import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { hideTabBar: true }
  },
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue')
  },
  {
    path: '/shelf',
    name: 'Shelf',
    component: () => import('../views/Shelf.vue'),
    meta: { requireAuth: true }
  },
  {
    path: '/friends',
    name: 'Friends',
    component: () => import('../views/Friends.vue'),
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
    component: () => import('../views/BookDetail.vue'),
    meta: { hideTabBar: true }
  },
  {
    path: '/read/:id',
    name: 'Read',
    component: () => import('../views/Read.vue'),
    meta: { hideTabBar: true }
  },
  {
    path: '/chat/:friendId',
    name: 'Chat',
    component: () => import('../views/Chat.vue'),
    meta: { hideTabBar: true, requireAuth: true }
  },
  {
    path: '/user/:id',
    name: 'UserProfile',
    component: () => import('../views/UserProfile.vue'),
    meta: { hideTabBar: true }
  },
  {
    path: '/shelf/import/:shareCode',
    name: 'ImportBooklist',
    component: () => import('../views/ImportBooklist.vue'),
    meta: { hideTabBar: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition
    return { top: 0 }
  }
})

// Auth guard
router.beforeEach((to, from, next) => {
  if (to.meta.requireAuth) {
    const user = localStorage.getItem('user')
    if (!user) {
      next('/login')
      return
    }
  }
  next()
})

export default router
