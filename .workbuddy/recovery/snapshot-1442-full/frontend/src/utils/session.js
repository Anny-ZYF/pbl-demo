const TOKEN_KEY = 'groupwork.accessToken'
const USER_KEY = 'groupwork.currentUser'

const storage = () => typeof window === 'undefined' ? null : window.localStorage

export function readStoredToken() {
  return storage()?.getItem(TOKEN_KEY) || null
}

export function readStoredUser() {
  const raw = storage()?.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw)
  } catch {
    storage()?.removeItem(USER_KEY)
    return null
  }
}

export function writeStoredSession(token, user) {
  if (!storage()) return
  storage().setItem(TOKEN_KEY, token)
  storage().setItem(USER_KEY, JSON.stringify(user))
}

export function clearStoredSession() {
  storage()?.removeItem(TOKEN_KEY)
  storage()?.removeItem(USER_KEY)
}
