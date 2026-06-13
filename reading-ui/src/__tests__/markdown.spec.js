import { describe, expect, it } from 'vitest'
import { renderMarkdown } from '../utils/markdown'

// renderMarkdown 是 XSS 关键路径：marked.parse 后必须 DOMPurify.sanitize。
// 该测试锁定"解析 + 强制 sanitize"行为，两端（桌面/移动）共享同一份 markdown.js，故在此单一来源覆盖。

describe('renderMarkdown', () => {
  it('returns empty string for falsy input', () => {
    expect(renderMarkdown('')).toBe('')
    expect(renderMarkdown(null)).toBe('')
    expect(renderMarkdown(undefined)).toBe('')
  })

  it('parses markdown syntax', () => {
    expect(renderMarkdown('**粗体**')).toContain('<strong>粗体</strong>')
    expect(renderMarkdown('# 标题')).toContain('<h1>')
    expect(renderMarkdown('# 标题')).toContain('标题')
  })

  it('strips script tags (XSS)', () => {
    expect(renderMarkdown('<script>alert(1)</script>')).not.toContain('<script>')
  })

  it('strips dangerous event-handler attributes (XSS)', () => {
    expect(renderMarkdown('<img src=x onerror=alert(1)>')).not.toContain('onerror')
  })

  it('keeps markdown formatting while stripping embedded malicious html', () => {
    const out = renderMarkdown('**粗体**<script>alert(1)</script>')

    expect(out).toContain('<strong>')
    expect(out).not.toContain('<script>')
  })
})
