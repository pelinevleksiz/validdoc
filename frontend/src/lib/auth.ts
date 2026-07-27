export const TOKEN_STORAGE_KEY = "validdoc_token"
const ROLE_STORAGE_KEY = "validdoc_role"
const USERNAME_STORAGE_KEY = "validdoc_username"

export function saveSession(token: string, role: string, username: string) {
  localStorage.setItem(TOKEN_STORAGE_KEY, token)
  localStorage.setItem(ROLE_STORAGE_KEY, role)
  localStorage.setItem(USERNAME_STORAGE_KEY, username)
}

export function clearSession() {
  localStorage.removeItem(TOKEN_STORAGE_KEY)
  localStorage.removeItem(ROLE_STORAGE_KEY)
  localStorage.removeItem(USERNAME_STORAGE_KEY)
}

export function getStoredToken() {
  return localStorage.getItem(TOKEN_STORAGE_KEY)
}

export function getStoredRole() {
  return localStorage.getItem(ROLE_STORAGE_KEY)
}

export function getStoredUsername() {
  return localStorage.getItem(USERNAME_STORAGE_KEY)
}

export function getTokenExpiryMs(token: string): number | null {
  try {
    const payload = token.split(".")[1]
    const decoded = JSON.parse(atob(payload))
    if (typeof decoded.exp !== "number") return null
    return decoded.exp * 1000
  } catch {
    return null
  }
}