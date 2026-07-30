import { NavLink } from "react-router"
import { useTranslation } from "react-i18next"
import { useAuth } from "@/contexts/AuthContext"
import { cn } from "@/lib/utils"

interface NavItem {
  labelKey: string
  path: string
  roles?: string[]
}

const NAV_ITEMS: NavItem[] = [
  { labelKey: "nav.dashboard", path: "/dashboard" },
  { labelKey: "nav.upload", path: "/upload" },
  { labelKey: "nav.documents", path: "/documents" },
  { labelKey: "nav.reviewQueue", path: "/review-queue" },
  { labelKey: "nav.templates", path: "/templates", roles: ["ADMIN"] },
  { labelKey: "nav.users", path: "/users", roles: ["ADMIN"] },
  { labelKey: "nav.auditLogs", path: "/audit-logs", roles: ["ADMIN"] },
  { labelKey: "nav.settings", path: "/settings", roles: ["ADMIN"] },
  { labelKey: "nav.changePassword", path: "/change-password", roles: ["ADMIN"] },
]

function NavSidebar() {
  const { t } = useTranslation()
  const { role } = useAuth()

  const visibleItems = NAV_ITEMS.filter(
    (item) => !item.roles || (role && item.roles.includes(role))
  )

  return (
    <aside className="w-56 border-r p-4">
      <div className="font-amarego lowercase mb-6 w-full text-center text-4xl">validdoc</div>
      <nav className="flex flex-col gap-1">
        {visibleItems.map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            className={({ isActive }) =>
              cn(
                "rounded-md px-3 py-2 text-sm",
                isActive
                  ? "bg-accent text-accent-foreground"
                  : "text-muted-foreground hover:bg-accent/50"
              )
            }
          >
            {t(item.labelKey)}
          </NavLink>
        ))}
      </nav>
    </aside>
  )
}

export default NavSidebar