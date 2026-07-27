import { createContext, useContext, useState, type ReactNode } from "react"
import { saveSession, clearSession, getStoredRole, getStoredUsername } from "@/lib/auth"
import { TOKEN_STORAGE_KEY } from "@/lib/api"

interface AuthState {
  token: string | null
  role: string | null
  username: string | null
  isAuthenticated: boolean
}

interface AuthContextValue extends AuthState {
  login: (token: string, role: string, username: string) => void
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

function getInitialState(): AuthState {
  const token = localStorage.getItem(TOKEN_STORAGE_KEY)
  const role = getStoredRole()
  const username = getStoredUsername()
  return {
    token,
    role,
    username,
    isAuthenticated: Boolean(token),
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>(getInitialState)

  function login(token: string, role: string, username: string) {
    saveSession(token, role, username)
    setState({ token, role, username, isAuthenticated: true })
  }

  function logout() {
    clearSession()
    setState({ token: null, role: null, username: null, isAuthenticated: false })
  }

  return (
    <AuthContext.Provider value={{ ...state, login, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error("useAuth, AuthProvider içinde kullanılmalı")
  }
  return context
}