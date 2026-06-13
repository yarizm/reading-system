export const parseSseJsonEvent = (event, source = 'SSE') => {
  const raw = event?.data
  if (!raw) return null

  try {
    return JSON.parse(raw)
  } catch (error) {
    console.warn(`${source} parse error:`, error, raw)
    return null
  }
}
