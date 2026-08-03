import type { ComponentType, ReactNode } from "react"
import { Link } from "react-router"
import { useTranslation } from "react-i18next"
import { useQuery } from "@tanstack/react-query"
import { FileUp, FilePlus2, UserPlus, ClipboardCheck } from "lucide-react"
import { useAuth } from "@/contexts/AuthContext"
import { api } from "@/lib/api"
import { cn } from "@/lib/utils"

interface DocumentStats {
  todayUploads: number
  pendingReview: number
  weeklyValidationRate: number | null
}

interface Shortcut {
  key: string
  to: string
  titleKey: string
  descriptionKey: string
  icon: ComponentType<{ className?: string }>
}

const ADMIN_SHORTCUTS: Shortcut[] = [
  { key: "upload", to: "/upload", titleKey: "nav.upload", descriptionKey: "dashboard.shortcutUploadDesc", icon: FileUp },
  { key: "template", to: "/templates/new", titleKey: "templates.addButton", descriptionKey: "dashboard.shortcutTemplateDesc", icon: FilePlus2 },
  { key: "user", to: "/users?new=1", titleKey: "users.addButton", descriptionKey: "dashboard.shortcutUserDesc", icon: UserPlus },
]

const OPERATOR_SHORTCUTS: Shortcut[] = [
  { key: "upload", to: "/upload", titleKey: "nav.upload", descriptionKey: "dashboard.shortcutUploadDesc", icon: FileUp },
  { key: "review", to: "/review-queue", titleKey: "nav.reviewQueue", descriptionKey: "dashboard.shortcutReviewDesc", icon: ClipboardCheck },
]

function BoxShell({ children, className }: { children: ReactNode; className?: string }) {
  return (
    <div
      className={cn(
        "flex h-full flex-col justify-between gap-3 rounded-xl border p-5 ring-1 ring-foreground/10",
        className
      )}
    >
      {children}
    </div>
  )
}

function StatsShell({ children }: { children: ReactNode }) {
  return (
    <div
      className={cn(
        "flex flex-col divide-y divide-border overflow-hidden rounded-xl border ring-1 ring-foreground/10",
        "md:grid md:grid-cols-3 md:gap-4 md:divide-y-0 md:overflow-visible md:rounded-none md:border-0 md:ring-0"
      )}
    >
      {children}
    </div>
  )
}

function StatItem({ children }: { children: ReactNode }) {
  return (
    <div
      className={cn(
        "flex flex-col justify-between gap-0.5 px-4 py-2.5",
        "md:gap-1 md:rounded-lg md:border md:px-4 md:py-3 md:ring-1 md:ring-foreground/10"
      )}
    >
      {children}
    </div>
  )
}

function StatBox({ value, label }: { value: string; label: string }) {
  return (
    <StatItem>
      <span className="text-xl font-semibold">{value}</span>
      <span className="text-xs text-muted-foreground">{label}</span>
    </StatItem>
  )
}

function StatsRow() {
  const { t } = useTranslation()
  const { data, isLoading, isError } = useQuery({
    queryKey: ["document-stats"],
    queryFn: async () => {
      const res = await api.get<DocumentStats>("/api/documents/stats")
      return res.data
    },
  })

  if (isLoading) {
    return (
      <StatsShell>
        {[0, 1, 2].map((i) => (
          <StatItem key={i}>
            <span className="text-sm text-muted-foreground">{t("common.loading")}</span>
          </StatItem>
        ))}
      </StatsShell>
    )
  }

  if (isError || !data) {
    return (
      <StatsShell>
        <StatItem>
          <span className="text-sm text-destructive">{t("dashboard.statsLoadError")}</span>
        </StatItem>
      </StatsShell>
    )
  }

  return (
    <StatsShell>
      <StatBox value={String(data.todayUploads)} label={t("dashboard.statsToday")} />
      <StatBox value={String(data.pendingReview)} label={t("dashboard.statsPending")} />
      <StatBox
        value={data.weeklyValidationRate === null ? t("dashboard.statsNoData") : `%${data.weeklyValidationRate.toFixed(1)}`}
        label={t("dashboard.statsWeeklyRate")}
      />
    </StatsShell>
  )
}

function ShortcutCard({ shortcut }: { shortcut: Shortcut }) {
  const { t } = useTranslation()
  const Icon = shortcut.icon
  return (
    <Link to={shortcut.to} className="block h-full">
      <BoxShell className="transition-colors hover:border-primary/50 hover:bg-accent/50">
        <Icon className="h-6 w-6 text-muted-foreground" />
        <div>
          <p className="font-medium">{t(shortcut.titleKey)}</p>
          <p className="text-sm text-muted-foreground">{t(shortcut.descriptionKey)}</p>
        </div>
      </BoxShell>
    </Link>
  )
}

function Dashboard() {
  const { t } = useTranslation()
  const { role } = useAuth()
  const shortcuts = role === "ADMIN" ? ADMIN_SHORTCUTS : OPERATOR_SHORTCUTS

  return (
    <div>
      <h1 className="font-amarego lowercase mb-6 text-3xl">{t("nav.dashboard")}</h1>

      <StatsRow />

      <div
        className={cn(
          "mt-4 grid grid-cols-1 gap-4",
          role === "ADMIN" ? "md:grid-cols-3" : "md:grid-cols-2"
        )}
      >
        {shortcuts.map((shortcut) => (
          <ShortcutCard key={shortcut.key} shortcut={shortcut} />
        ))}
      </div>
    </div>
  )
}

export default Dashboard