import { describe, expect, it, vi } from 'vitest'
import { formatChatTime, formatDatePart, formatDateTimeMinute } from '../utils/dateTime'
import { parseGuideActions, stripGuideActions } from '../utils/guideActions'
import { parseJsonSafely } from '../utils/jsonUtils'
import { parseSseJsonEvent } from '../utils/sseEvents'
import { parseWebSocketJsonMessage } from '../utils/webSocketMessages'
import {
  formatSharePosition,
  getAudioShare,
  getBookShare,
  getParagraphShare,
  SHARE_PREFIXES
} from '../utils/shareMessage'

describe('chat utilities', () => {
  it('parses supported share message payloads and ignores invalid content', () => {
    const paragraph = { bookId: 3, chapterIndex: 1, paragraphIndex: 2, quote: 'hello' }
    const audio = { bookId: 4, sourceType: 'chapter', audioUrl: '/files/a.mp3' }
    const book = { bookId: 5, bookTitle: 'Book' }

    expect(getParagraphShare({ content: SHARE_PREFIXES.paragraph + JSON.stringify(paragraph) })).toEqual(paragraph)
    expect(getAudioShare({ content: SHARE_PREFIXES.audio + JSON.stringify(audio) })).toEqual(audio)
    expect(getBookShare({ content: SHARE_PREFIXES.book + JSON.stringify(book) })).toEqual(book)
    expect(getBookShare({ content: SHARE_PREFIXES.book + '{bad json' })).toBeNull()
    expect(getBookShare({ content: 'plain text' })).toBeNull()
  })

  it('formats share positions consistently', () => {
    expect(formatSharePosition({ chapterIndex: 0, paragraphIndex: 3 })).toBe('第 1 章 · 第 4 段')
    expect(formatSharePosition({ sourceType: 'chapter' })).toBe('整章听书')
    expect(formatSharePosition({ sourceType: 'paragraph' })).toBe('段落朗读')
    expect(formatSharePosition(null)).toBe('来自聊天分享')
  })

  it('formats chat time relative to the current day', () => {
    const now = new Date('2026-06-09T12:00:00')

    expect(formatChatTime('2026-06-09T09:05:00', now)).toBe('09:05')
    expect(formatChatTime('2026-06-08T21:07:00', now)).toBe('06-08 21:07')
    expect(formatChatTime('', now)).toBe('')
    expect(formatChatTime('not a date', now)).toBe('')
  })

  it('parses and strips guide action markers', () => {
    const content = 'Open [ACTION:navigate:/shelf:Go Shelf] then [ACTION:api_call:refresh:Refresh Now].'

    expect(parseGuideActions(content)).toEqual([
      { type: 'navigate', payload: '/shelf', label: 'Go Shelf' },
      { type: 'api_call', payload: 'refresh', label: 'Refresh Now' }
    ])
    expect(parseGuideActions('plain text')).toEqual([])
    expect(parseGuideActions('[ACTION:broken]')).toEqual([])
    expect(stripGuideActions(content)).toBe('Open  then .')
    expect(stripGuideActions(null)).toBe('')
  })

  it('parses SSE JSON events without throwing on empty or invalid data', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})

    expect(parseSseJsonEvent({ data: '{"event":"message","answer":"ok"}' }, 'Test SSE'))
      .toEqual({ event: 'message', answer: 'ok' })
    expect(parseSseJsonEvent({ data: '' }, 'Test SSE')).toBeNull()
    expect(parseSseJsonEvent({ data: 'not json' }, 'Test SSE')).toBeNull()
    expect(warn).toHaveBeenCalledTimes(1)

    warn.mockRestore()
  })

  it('parses WebSocket JSON messages and reports invalid payloads', () => {
    expect(parseWebSocketJsonMessage({ data: '{"type":"chat","count":2}' }))
      .toEqual({ message: { type: 'chat', count: 2 }, error: null })
    expect(parseWebSocketJsonMessage({ data: '' }))
      .toEqual({ message: null, error: null })

    const result = parseWebSocketJsonMessage({ data: 'not json' })
    expect(result.message).toBeNull()
    expect(result.error).toBeInstanceOf(SyntaxError)
  })

  it('parses local JSON values safely', () => {
    const onError = vi.fn()

    expect(parseJsonSafely('{"theme":"dark"}')).toEqual({ theme: 'dark' })
    expect(parseJsonSafely('', { theme: 'default' })).toEqual({ theme: 'default' })
    expect(parseJsonSafely('null', { theme: 'default' })).toEqual({ theme: 'default' })
    expect(parseJsonSafely('{bad json', { theme: 'default' }, onError)).toEqual({ theme: 'default' })
    expect(onError).toHaveBeenCalledTimes(1)
  })

  it('formats common date values for list views', () => {
    expect(formatDatePart('2026-06-09T09:05:30')).toBe('2026-06-09')
    expect(formatDatePart('', '未知')).toBe('未知')
    expect(formatDateTimeMinute('2026-06-09T09:05:30')).toBe('2026-06-09 09:05')
    expect(formatDateTimeMinute(null, '刚刚')).toBe('刚刚')
  })
})
