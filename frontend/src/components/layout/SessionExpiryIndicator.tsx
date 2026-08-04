import { useEffect, useState } from "react"
import { Clock } from "lucide-react"
import { useAuth } from "@/contexts/AuthContext"
import { cn } from "@/lib/utils"

function formatRemaining(ms: number) {
  const totalSeconds = Math.max(0, Math.floor(ms / 1000))
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${seconds.toString().padStart(2, "0")}`
}

interface SessionExpiryIndicatorProps {
  variant?: "desktop" | "mobile"
}

function SessionExpiryIndicator({ variant = "desktop" }: SessionExpiryIndicatorProps) {
  const { expiresAt } = useAuth()
  const [now, setNow] = useState(Date.now())
  const [expanded, setExpanded] = useState(false)

  useEffect(() => {
    const interval = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(interval)
  }, [])

  if (!expiresAt) {
    return null
  }

  const isMobile = variant === "mobile"

  return (
    <button
      type="button"
      onClick={() => setExpanded((prev) => !prev)}
      className={cn(
        "flex items-center gap-1.5 overflow-hidden rounded-md border border-muted-foreground/20 px-2 text-muted-foreground transition-all duration-200 hover:bg-accent hover:text-accent-foreground",
        isMobile ? "h-9 flex-row-reverse" : "h-8",
        expanded ? "w-20" : isMobile ? "w-9" : "w-8"
      )}
    >
      <Clock className="h-4 w-4 shrink-0" />
      <span
        className={cn(
          "whitespace-nowrap text-sm font-medium transition-opacity duration-200",
          expanded ? "opacity-100" : "opacity-0"
        )}
      >
        {formatRemaining(expiresAt - now)}
      </span>
    </button>
  )
}

export default SessionExpiryIndicator