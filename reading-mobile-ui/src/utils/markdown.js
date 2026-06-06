import { marked } from 'marked'
import DOMPurify from 'dompurify'

/**
 * 将 Markdown 文本渲染为安全的 HTML
 */
export function renderMarkdown(text) {
  if (!text) return ''
  return DOMPurify.sanitize(marked.parse(text))
}
