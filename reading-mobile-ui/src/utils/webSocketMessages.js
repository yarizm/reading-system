export const parseWebSocketJsonMessage = (event) => {
  const raw = event?.data
  if (!raw) {
    return { message: null, error: null }
  }

  try {
    return { message: JSON.parse(raw), error: null }
  } catch (error) {
    return { message: null, error }
  }
}
