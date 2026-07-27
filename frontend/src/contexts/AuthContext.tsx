import {
  createContext,
  useContext,
  useEffect,
  useRef,
  useState,
  type ReactNode,
} from "react"
import {
  saveSession,
  clearSession,
  getStoredRole,
  getStoredUsername,
  getStoredToken,
  getTokenExpiryMs,
} from "@/lib/auth"

interface AuthState {
  token: string | null
  role: string | null
  username: string | null
  isAuthenticated: boolean
}

interface AuthContextValue extends AuthState {
  login: (token: string, role: string, username: string) => void
  logout: () => void
  expiresAt: number | null
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

function getInitialState(): AuthState {
  const token = getStoredToken()
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
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)

  function login(token: string, role: string, username: string) {
    saveSession(token, role, username)
    setState({ token, role, username, isAuthenticated: true })
  }

  function logout() {
    clearSession()
    setState({ token: null, role: null, username: null, isAuthenticated: false })
  }

  const expiresAt = state.token ? getTokenExpiryMs(state.token) : null

  useEffect(() => {
    if (timerRef.current) {
      clearTimeout(timerRef.current)
      timerRef.current = null
    }

    if (!state.token || !expiresAt) {
      return
    }

    const msUntilExpiry = expiresAt - Date.now()

    if (msUntilExpiry <= 0) {
      clearSession()
      window.location.href = "/?expired=1"
      return
    }

    timerRef.current = setTimeout(() => {
      clearSession()
      window.location.href = "/?expired=1"
    }, msUntilExpiry)

    return () => {
      if (timerRef.current) {
        clearTimeout(timerRef.current)
      }
    }
  }, [state.token, expiresAt])

  return (
    <AuthContext.Provider value={{ ...state, login, logout, expiresAt }}>
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