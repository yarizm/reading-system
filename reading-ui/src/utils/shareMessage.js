export const SHARE_PREFIXES = {
  paragraph: '__PARAGRAPH_SHARE__',
  audio: '__AUDIO_SHARE__',
  book: '__BOOK_SHARE__'
}

export const parseShareContent = (content, prefix) => {
  if (!content || !content.startsWith(prefix)) return null
  try {
    return JSON.parse(content.slice(prefix.length))
  } catch (error) {
    return null
  }
}

export const getParagraphShare = (message) => parseShareContent(message?.content, SHARE_PREFIXES.paragraph)
export const getAudioShare = (message) => parseShareContent(message?.content, SHARE_PREFIXES.audio)
export const getBookShare = (message) => parseShareContent(message?.content, SHARE_PREFIXES.book)

export const formatSharePosition = (share) => {
  if (!share) return '来自聊天分享'
  const parts = []
  if (share.chapterIndex !== null && share.chapterIndex !== undefined) {
    parts.push(`第 ${share.chapterIndex + 1} 章`)
  }
  if (share.paragraphIndex !== null && share.paragraphIndex !== undefined) {
    parts.push(`第 ${share.paragraphIndex + 1} 段`)
  }
  if (parts.length > 0) return parts.join(' · ')
  if (share.sourceType === 'chapter') return '整章听书'
  if (share.sourceType === 'paragraph') return '段落朗读'
  return '朗读音频'
}
