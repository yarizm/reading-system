export const parseJsonSafely = (value, fallback = null, onError) => {
  if (value === undefined || value === null || value === '' || value === 'null') {
    return fallback
  }

  try {
    return JSON.parse(value) ?? fallback
  } catch (error) {
    onError?.(error)
    return fallback
  }
}
