import { onUnmounted, ref } from 'vue'
import { parseWebSocketJsonMessage } from '../utils/webSocketMessages'

const buildNotificationUrl = (user) => {
  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws/notification?userId=${user.id}&token=${encodeURIComponent(user.token || '')}`
}

export function useNotificationSocket({ getUser, onMessage, onParseError, reconnectDelay = 5000 } = {}) {
  const isConnected = ref(false)
  let socket = null
  let reconnectTimer = null

  const clearReconnectTimer = () => {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = null
    }
  }

  const currentUser = () => {
    if (typeof getUser === 'function') return getUser()
    return getUser?.value || getUser || null
  }

  const close = () => {
    clearReconnectTimer()
    if (socket) {
      const closingSocket = socket
      socket = null
      closingSocket.close()
    }
    isConnected.value = false
  }

  const connect = () => {
    const user = currentUser()
    if (!user?.id) return

    close()
    const nextSocket = new WebSocket(buildNotificationUrl(user))
    socket = nextSocket

    nextSocket.onopen = () => {
      if (socket === nextSocket) {
        isConnected.value = true
      }
    }

    nextSocket.onmessage = (event) => {
      const { message, error } = parseWebSocketJsonMessage(event)
      if (error) {
        if (onParseError) {
          onParseError(error, event)
        } else {
          console.error('WS parse error', error)
        }
        return
      }
      if (message === null) return

      try {
        onMessage?.(message, event)
      } catch (handlerError) {
        if (onParseError) {
          onParseError(handlerError, event)
        } else {
          console.error('WS message handler error', handlerError)
        }
      }
    }

    nextSocket.onclose = () => {
      if (socket === nextSocket) {
        socket = null
        isConnected.value = false
        if (currentUser()?.id) {
          reconnectTimer = setTimeout(connect, reconnectDelay)
        }
      }
    }
  }

  onUnmounted(close)

  return {
    connect,
    close,
    isConnected
  }
}
