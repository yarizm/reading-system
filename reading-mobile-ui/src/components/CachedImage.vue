<script setup>
import { ref, watch } from 'vue'
import { getCachedImage } from '../utils/imageCache'

const props = defineProps({
  src: String,
  defaultSrc: String,
  round: Boolean,
  width: [String, Number],
  height: [String, Number],
  customStyle: Object,
  customClass: String
})

const displaySrc = ref(props.defaultSrc || '')

watch(() => props.src, async (newVal) => {
  const srcAtCall = newVal
  const cachedUrl = newVal ? await getCachedImage(newVal) : props.defaultSrc
  if (props.src === srcAtCall) {
    displaySrc.value = cachedUrl
  }
}, { immediate: true })
const formatSize = (size) => {
  if (!size && size !== 0) return undefined
  if (typeof size === 'number') return size + 'px'
  if (typeof size === 'string' && /^\d+$/.test(size)) return size + 'px'
  return size
}
</script>

<template>
  <img 
    :src="displaySrc" 
    :class="customClass"
    :style="[
      customStyle,
      round ? { borderRadius: '50%' } : {},
      width ? { width: formatSize(width) } : {},
      height ? { height: formatSize(height) } : {},
      { objectFit: 'cover' }
    ]"
    v-bind="$attrs"
  />
</template>
