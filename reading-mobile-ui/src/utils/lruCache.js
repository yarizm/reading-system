export class LruCache {
  constructor(maxSize = 50, onEvict = null) {
    this.cache = new Map()
    this.maxSize = maxSize
    this.onEvict = onEvict
  }

  get(key) {
    if (!this.cache.has(key)) return undefined
    const val = this.cache.get(key)
    this.cache.delete(key)
    this.cache.set(key, val)
    return val
  }

  set(key, value) {
    if (this.cache.has(key)) {
      const oldVal = this.cache.get(key)
      this.cache.delete(key)
      if (this.onEvict && oldVal !== value) this.onEvict(oldVal)
    }
    this.cache.set(key, value)
    
    if (this.cache.size > this.maxSize) {
      const oldestKey = this.cache.keys().next().value
      const oldestVal = this.cache.get(oldestKey)
      this.cache.delete(oldestKey)
      if (this.onEvict) this.onEvict(oldestVal)
    }
  }

  has(key) {
    return this.cache.has(key)
  }

  delete(key) {
    if (this.cache.has(key)) {
      const val = this.cache.get(key)
      this.cache.delete(key)
      if (this.onEvict) this.onEvict(val)
      return true
    }
    return false
  }

  clear() {
    if (this.onEvict) {
      for (const val of this.cache.values()) {
        this.onEvict(val)
      }
    }
    this.cache.clear()
  }
}
