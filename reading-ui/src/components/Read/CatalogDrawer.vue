<template>
  <el-drawer :model-value="show" @update:model-value="$emit('update:show', $event)" title="目录" direction="rtl" size="300px" @open="onOpen">
    <div class="catalog-list">
      <div v-for="(chapter, index) in catalog" :key="chapter.id" :id="'catalog-item-' + index" class="catalog-item" :class="{ active: index === chapterIndex }" @click="jump(index)">
        {{ chapter.title }}
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { nextTick } from 'vue'

const props = defineProps({
  show: Boolean,
  catalog: {
    type: Array,
    default: () => []
  },
  chapterIndex: Number
})

const emit = defineEmits(['update:show', 'jump-to'])

const onOpen = () => {
  nextTick(() => {
    setTimeout(() => {
      const activeEl = document.getElementById(`catalog-item-${props.chapterIndex}`)
      if (activeEl) {
        activeEl.scrollIntoView({ behavior: 'auto', block: 'center' })
      }
    }, 350)
  })
}

const jump = (index) => {
  emit('jump-to', index)
}
</script>

<style scoped>
.catalog-list {
  padding: 10px;
}
.catalog-item {
  padding: 12px 16px;
  cursor: pointer;
  border-radius: 8px;
  margin-bottom: 4px;
  transition: all 0.2s ease;
  font-size: 14px;
}
.catalog-item:hover {
  background-color: rgba(0, 0, 0, 0.04);
}
.catalog-item.active {
  color: #0066cc;
  font-weight: bold;
  background-color: rgba(0, 102, 204, 0.08);
}
.theme-dark .catalog-item:hover {
  background-color: rgba(255, 255, 255, 0.05);
}
.theme-dark .catalog-item.active {
  color: #409eff;
  background-color: rgba(64, 158, 255, 0.1);
}
</style>
