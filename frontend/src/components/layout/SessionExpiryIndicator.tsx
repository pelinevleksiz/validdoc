import { useEffect, useState } from "react"
import { useAuth } from "@/contexts/AuthContext"

function formatRemaining(ms: number) {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${seconds.toString().padStart(2, "0")}`
}

function SessionExpiryIndicator() {
  const { expiresAt } = useAuth()
  const [now, setNow] = useState(Date.now())

  useEffect(() => {
    const interval = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(interval)
  }, [])

  if (!expiresAt) {
    return null
  }

  return (
    <span className="text-xs text-muted-foreground">
      Oturum: {formatRemaining(expiresAt - now)}
    </span>
  )
}

export default SessionExpiryIndicator