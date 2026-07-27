import axios from "axios"
import { getStoredToken, clearSession } from "@/lib/auth"
import { getLanguage } from "@/lib/language"

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
})

const SESSION_INDEPENDENT_ENDPOINTS = ["/api/auth/login", "/api/users/me/password"]

api.interceptors.request.use((config) => {
  const token = getStoredToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  config.headers["Accept-Language"] = getLanguage()
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      const url = error.config?.url ?? ""
      const isSessionIndependent = SESSION_INDEPENDENT_ENDPOINTS.some((path) => url.includes(path))
      if (!isSessionIndependent) {
        clearSession()
        window.location.href = "/"
      }
    }
    return Promise.reject(error)
  }
)