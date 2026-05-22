<template>
  <el-dialog :model-value="show" @update:model-value="$emit('update:show', $event)" title="听书播放器" width="520px" @closed="$emit('closed')">
    <div v-loading="isAudioLoading" class="audio-player-panel">
      <div class="audio-player-heading">{{ playback.title || '朗读音频' }}</div>
      <div class="audio-player-subheading">{{ sourceLabel }}</div>
      
      <audio
        ref="audioRef"
        class="audio-player-element"
        :src="playableUrl || undefined"
        controls
        preload="metadata"
        @loadedmetadata="$emit('loadedmetadata')"
        @timeupdate="$emit('timeupdate')"
        @play="$emit('play')"
        @pause="$emit('pause')"
        @ended="$emit('ended')"
      />
      
      <div class="audio-player-time">
        <span>{{ formatTime(currentTime) }}</span>
        <span>{{ formatTime(duration) }}</span>
      </div>
      
      <div class="audio-player-actions">
        <el-button :disabled="!playback.audioUrl" @click="$emit('toggle-playback')">
          {{ isAudioPlaying ? '暂停播放' : '继续播放' }}
        </el-button>
        <el-button :disabled="!playback.audioUrl" @click="$emit('download')">保存本地</el-button>
        <el-button type="primary" :disabled="!playback.audioUrl" @click="$emit('open-share')">分享给好友</el-button>
      </div>
    </div>
  </el-dialog>
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
.audio-player-panel {
  text-align: center;
  padding: 10px 0;
}
.audio-player-heading {
  font-size: 18px;
  font-weight: 600;
  color: #333;
  margin-bottom: 5px;
}
.theme-dark .audio-player-heading { color: #e8e8e8; }

.audio-player-subheading {
  font-size: 13px;
  color: #999;
  margin-bottom: 20px;
}

.audio-player-element {
  width: 100%;
  margin-bottom: 10px;
  outline: none;
}
.theme-dark .audio-player-element {
  filter: invert(0.9) hue-rotate(180deg);
}

.audio-player-time {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #666;
  margin-bottom: 20px;
  padding: 0 10px;
}
.theme-dark .audio-player-time { color: #aaa; }

.audio-player-actions {
  display: flex;
  justify-content: center;
  gap: 15px;
  margin-top: 10px;
}
</style>
