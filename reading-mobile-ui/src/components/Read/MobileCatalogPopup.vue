<template>
  <van-popup
    :show="show"
    @update:show="$emit('update:show', $event)"
    position="left"
    :style="{ width: '80%', height: '100%' }"
    @open="onOpen"
  >
    <van-nav-bar title="目录" />
    <div class="catalog-list" @scroll="onScroll" ref="listRef">
      <div class="catalog-phantom" :style="{ height: totalHeight + 'px' }"></div>
      <div class="catalog-content" :style="{ transform: `translate3d(0, ${offsetY}px, 0)` }">
        <div
          v-for="item in visibleData"
          :key="item.chapter.id"
          :id="'catalog-item-' + item.index"
          class="catalog-item"
          :class="{ active: item.index === chapterIndex }"
          @click="jump(item.index)"
        >
          {{ item.chapter.title }}
        </div>
      </div>
    </div>
  </van-popup>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'

const props = defineProps({
  show: Boolean,
  catalog: { type: Array, default: () => [] },
  chapterIndex: Number
})

const emit = defineEmits(['update:show', 'jump-to'])

const itemHeight = 45
const visibleCount = ref(30) // measured dynamically on open; 30 as fallback

const listRef = ref(null)
const scrollTop = ref(0)

const totalHeight = computed(() => props.catalog.length * itemHeight)

const startIndex = computed(() => Math.max(0, Math.floor(scrollTop.value / itemHeight) - 5))
const endIndex = computed(() => Math.min(props.catalog.length, startIndex.value + visibleCount.value + 10))

const visibleData = computed(() => {
  return props.catalog.slice(startIndex.value, endIndex.value).map((c, i) => ({
    chapter: c,
    index: startIndex.value + i
  }))
})

const offsetY = computed(() => startIndex.value * itemHeight)

const onScroll = (e) => {
  scrollTop.value = e.currentTarget.scrollTop
}

const onOpen = () => {
  nextTick(() => {
    setTimeout(() => {
      if (listRef.value) {
        visibleCount.value = Math.ceil(listRef.value.clientHeight / itemHeight) + 5
        const targetScroll = Math.max(0, props.chapterIndex * itemHeight - listRef.value.clientHeight / 2 + itemHeight / 2)
        listRef.value.scrollTop = targetScroll
        scrollTop.value = targetScroll
      }
    }, 100)
  })
}

const jump = (index) => emit('jump-to', index)
</script>

<style scoped>
.catalog-list {
  padding: 0;
  height: calc(100% - 46px);
  overflow-y: auto;
  position: relative;
  -webkit-overflow-scrolling: touch;
}
.catalog-phantom {
  position: absolute;
  left: 0;
  top: 0;
  right: 0;
  z-index: -1;
}
.catalog-content {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
}
.catalog-item {
  height: 45px;
  line-height: 45px;
  padding: 0 22px;
  font-size: 15px;
  border-bottom: 1px solid #f5f5f5;
  color: #333;
  box-sizing: border-box;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.theme-dark .catalog-item {
  color: #eee;
  border-bottom-color: #333;
}
.catalog-item.active {
  color: #1989fa;
  font-weight: bold;
}
</style>
