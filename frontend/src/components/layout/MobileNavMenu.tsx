import { NavLink } from "react-router"
import { Menu } from "lucide-react"
import { useTranslation } from "react-i18next"
import { useAuth } from "@/contexts/AuthContext"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { NAV_ITEMS } from "@/components/layout/nav-items"

function MobileNavMenu() {
  const { t } = useTranslation()
  const { role } = useAuth()

  const visibleItems = NAV_ITEMS.filter(
    (item) => !item.roles || (role && item.roles.includes(role))
  )

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <Button variant="outline" size="icon" className="h-9 w-9">
            <Menu className="h-5 w-5" />
          </Button>
        }
      />
      <DropdownMenuContent align="start" className="w-56">
        {visibleItems.map((item) => (
          <DropdownMenuItem key={item.path} render={<NavLink to={item.path}>{t(item.labelKey)}</NavLink>} />
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

export default MobileNavMenu