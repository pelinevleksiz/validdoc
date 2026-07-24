import { TOKEN_STORAGE_KEY } from "@/lib/api"

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

export function getStoredRole() {
  return localStorage.getItem(ROLE_STORAGE_KEY)
}

export function getStoredUsername() {
  return localStorage.getItem(USERNAME_STORAGE_KEY)
}