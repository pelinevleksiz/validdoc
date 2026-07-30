import { NavLink } from "react-router"
import { useTranslation } from "react-i18next"
import { useAuth } from "@/contexts/AuthContext"
import { cn } from "@/lib/utils"
import { NAV_ITEMS } from "@/components/layout/nav-items"

function NavLinks() {
  const { t } = useTranslation()
  const { role } = useAuth()

  const visibleItems = NAV_ITEMS.filter(
    (item) => !item.roles || (role && item.roles.includes(role))
  )

  return (
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
  )
}

export default NavLinks