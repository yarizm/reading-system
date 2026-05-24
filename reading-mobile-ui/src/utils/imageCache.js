// Cleanup bad localStorage items from previous version
try {
  for (let i = localStorage.length - 1; i >= 0; i--) {
    const key = localStorage.key(i)
    if (key && key.startsWith('avatar_cache_')) {
      localStorage.removeItem(key)
    }
  }
} catch (e) {}

const DB_NAME = 'ImageCacheDB'
const STORE_NAME = 'images'

const initDB = () => {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, 1)
    request.onupgradeneeded = (e) => {
      const db = e.target.result
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        db.createObjectStore(STORE_NAME)
      }
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

const saveToDB = async (url, blob) => {
  const db = await initDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readwrite')
    const store = tx.objectStore(STORE_NAME)
    const request = store.put(blob, url)
    request.onsuccess = () => resolve()
    request.onerror = () => reject(request.error)
  })
}

const getFromDB = async (url) => {
  const db = await initDB()
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_NAME, 'readonly')
    const store = tx.objectStore(STORE_NAME)
    const request = store.get(url)
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

import { LruCache } from './lruCache'

const memoryCache = new LruCache(50, (objectUrl) => {
  if (objectUrl && objectUrl.startsWith('blob:')) {
    URL.revokeObjectURL(objectUrl)
  }
})
const pendingRequests = new Map()

export const getCachedImage = async (url) => {
  if (!url) return ''
  if (url.startsWith('data:') || url.startsWith('blob:')) return url
  if (url.startsWith('http') && url.includes('elemecdn.com')) return url

  if (memoryCache.has(url)) return memoryCache.get(url)
  if (pendingRequests.has(url)) return pendingRequests.get(url)

  const requestPromise = (async () => {
    try {
      const cachedBlob = await getFromDB(url)
      if (cachedBlob) {
        const objectUrl = URL.createObjectURL(cachedBlob)
        memoryCache.set(url, objectUrl)
        return objectUrl
      }

      const response = await fetch(url)
      if (!response.ok) throw new Error('Network error')
      const blob = await response.blob()
      
      saveToDB(url, blob).catch(() => {})
      
      const objectUrl = URL.createObjectURL(blob)
      memoryCache.set(url, objectUrl)
      return objectUrl
    } catch (e) {
      return url
    } finally {
      pendingRequests.delete(url)
    }
  })()

  pendingRequests.set(url, requestPromise)
  return requestPromise
}
