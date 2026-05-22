<template>
  <van-popup
    :show="show"
    @update:show="$emit('update:show', $event)"
    position="left"
    :style="{ width: '80%', height: '100%' }"
    @open="onOpen"
  >
    <van-nav-bar title="目录" />
    <div class="catalog-list">
      <div
        v-for="(chapter, index) in catalog"
        :key="chapter.id"
        :id="'catalog-item-' + index"
        class="catalog-item"
        :class="{ active: index === chapterIndex }"
        @click="jump(index)"
      >
        {{ chapter.title }}
      </div>
    </div>
  </van-popup>
</template>

<script setup>
import { nextTick } from 'vue'

const props = defineProps({
  show: Boolean,
  catalog: { type: Array, default: () => [] },
  chapterIndex: Number
})

const emit = defineEmits(['update:show', 'jump-to'])

const onOpen = () => {
  nextTick(() => {
    setTimeout(() => {
      const activeEl = document.getElementById(`catalog-item-${props.chapterIndex}`)
      if (activeEl) activeEl.scrollIntoView({ behavior: 'auto', block: 'center' })
    }, 350)
  })
}

const jump = (index) => emit('jump-to', index)
</script>

<style scoped>
.catalog-list {
  padding: 10px;
  height: calc(100% - 46px);
  overflow-y: auto;
}
.catalog-item {
  padding: 12px;
  font-size: 15px;
  border-bottom: 1px solid #f5f5f5;
  color: #333;
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
