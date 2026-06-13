import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '../stores/auth'
import { getAuthHeaders, withFileAccessToken } from '../utils/authHeaders'

describe('auth header utilities', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('builds bearer headers from the current auth store token', () => {
    useAuthStore().login({ token: 'abc123' })

    expect(getAuthHeaders()).toEqual({ Authorization: 'Bearer abc123' })
  })

  it('adds encoded access token to protected file urls', () => {
    useAuthStore().login({ token: 'a b&c' })

    expect(withFileAccessToken('/files/audio.mp3')).toBe('/files/audio.mp3?token=a%20b%26c')
    expect(withFileAccessToken('/files/audio.mp3?download=1')).toBe('/files/audio.mp3?download=1&token=a%20b%26c')
  })

  it('keeps non-file urls and already-tokenized file urls unchanged', () => {
    useAuthStore().login({ token: 'abc123' })

    expect(withFileAccessToken('/api/books')).toBe('/api/books')
    expect(withFileAccessToken('/files/audio.mp3?token=old')).toBe('/files/audio.mp3?token=old')
    expect(withFileAccessToken('')).toBe('')
  })
})
