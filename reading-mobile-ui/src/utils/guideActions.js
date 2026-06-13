const GUIDE_ACTION_PATTERN = /\[ACTION:([^:]+):([^:]+):([^\]]+)\]/g
const GUIDE_ACTION_STRIP_PATTERN = /\[ACTION:.*?\]/g

export const parseGuideActions = (text) => {
  const actions = []
  if (!text) return actions

  let match
  GUIDE_ACTION_PATTERN.lastIndex = 0
  while ((match = GUIDE_ACTION_PATTERN.exec(text)) !== null) {
    actions.push({
      type: match[1],
      payload: match[2],
      label: match[3]
    })
  }
  return actions
}

export const stripGuideActions = (text) => (text || '').replace(GUIDE_ACTION_STRIP_PATTERN, '')
