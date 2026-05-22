<script setup>
// 这里不需要写逻辑了，逻辑都在 views 里
</script>

<template>
  <router-view v-slot="{ Component, route }">
    <transition name="fade-slide" mode="out-in">
      <component :is="Component" :key="route.path" />
    </transition>
  </router-view>
</template>

<style>
/* 页面切换动画：Cinematic 电影级景深过渡 */
.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.55s cubic-bezier(0.16, 1, 0.3, 1) !important;
}

.fade-slide-enter-from {
  opacity: 0;
  transform: scale(0.96) translateY(20px);
  filter: blur(12px);
}

.fade-slide-leave-to {
  opacity: 0;
  transform: scale(1.04) translateY(-20px);
  filter: blur(12px);
}
/* 全局样式统一由 style.css 管理 */
</style>
