import { NavLink } from "react-router"
import { useAuth } from "@/contexts/AuthContext"
import { cn } from "@/lib/utils"
import validdocWordmark from "@/assets/validdoc-wordmark.png"

interface NavItem {
  label: string
  path: string
  roles?: string[]
}

const NAV_ITEMS: NavItem[] = [
  { label: "Panel", path: "/dashboard" },
  { label: "Belge Yükle", path: "/upload" },
  { label: "Şablonlar", path: "/templates", roles: ["ADMIN"] },
  { label: "Kullanıcılar", path: "/users", roles: ["ADMIN"] },
  { label: "Denetim Kayıtları", path: "/audit-logs", roles: ["ADMIN"] },
  { label: "Doğrulama Ayarları", path: "/settings", roles: ["ADMIN"] },
  { label: "Şifre Değiştir", path: "/change-password", roles: ["ADMIN"] },
]

function NavSidebar() {
  const { role } = useAuth()

  const visibleItems = NAV_ITEMS.filter(
    (item) => !item.roles || (role && item.roles.includes(role))
  )

  return (
    <aside className="w-56 border-r p-4">
      <img src={validdocWordmark} alt="validdoc" className="mb-6 block h-auto w-full" />
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
            {item.label}
          </NavLink>
        ))}
      </nav>
    </aside>
  )
}

export default NavSidebar