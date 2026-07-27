import axios from "axios"
import { getStoredToken, clearSession } from "@/lib/auth"

export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
})

api.interceptors.request.use((config) => {
  const token = getStoredToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (
      axios.isAxiosError(error) &&
      error.response?.status === 401 &&
      !error.config?.url?.includes("/api/auth/login")
    ) {
      clearSession()
      window.location.href = "/"
    }
    return Promise.reject(error)
  }
)