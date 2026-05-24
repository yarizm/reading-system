<template>
  <div class="mobile-audio-player-bar mobile-glass-panel" v-if="show">
    <div class="audio-info">
      <van-icon name="music-o" class="music-icon" :class="{ rotating: isAudioPlaying }" />
      <div class="audio-text">
        <div class="audio-title van-ellipsis">{{ playback.title || '朗读中...' }}</div>
        <div class="audio-time">{{ formatTime(currentTime) }} / {{ formatTime(duration) }}</div>
      </div>
    </div>
    <div class="audio-controls">
      <van-icon :name="isAudioPlaying ? 'pause-circle-o' : 'play-circle-o'" @click="$emit('toggle-playback')" />
      <van-icon name="share-o" @click="$emit('open-share')" />
      <van-icon name="cross" @click="$emit('closed')" />
    </div>
    <audio
      ref="audioRef"
      style="display:none;"
      :src="playableUrl || undefined"
      @loadedmetadata="$emit('loadedmetadata')"
      @timeupdate="$emit('timeupdate')"
      @play="$emit('play')"
      @pause="$emit('pause')"
      @ended="$emit('ended')"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'

const props = defineProps({
  show: Boolean,
  isAudioLoading: Boolean,
  playback: Object,
  sourceLabel: String,
  playableUrl: String,
  currentTime: Number,
  duration: Number,
  isAudioPlaying: Boolean,
  formatTime: Function
})

const emit = defineEmits([
  'update:show', 'closed', 'loadedmetadata', 'timeupdate', 
  'play', 'pause', 'ended', 'toggle-playback', 'download', 'open-share',
  'register-audio-ref'
])

const audioRef = ref(null)

onMounted(() => {
  emit('register-audio-ref', audioRef)
})
</script>

<style scoped>
.mobile-audio-player-bar {
  position: fixed;
  bottom: 12px;
  left: 12px;
  right: 12px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  z-index: 110;
  border-radius: 16px;
}

.audio-info {
  display: flex;
  align-items: center;
  flex: 1;
  overflow: hidden;
  gap: 12px;
}

.music-icon {
  font-size: 28px;
  color: #a36b46;
}

.rotating {
  animation: rotate 4s linear infinite;
}

@keyframes rotate {
  100% {
    transform: rotate(360deg);
  }
}

.audio-text {
  flex: 1;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.audio-title {
  font-size: 14px;
  font-weight: 700;
  color: #2e2520;
}

.audio-time {
  font-size: 11px;
  color: #8b6f52;
}

.audio-controls {
  display: flex;
  align-items: center;
  gap: 16px;
  font-size: 24px;
  color: #6b5e53;
}

.audio-controls .van-icon:active {
  opacity: 0.7;
}
</style>
