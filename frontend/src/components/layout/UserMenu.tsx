import { useTranslation } from "react-i18next"
import { useNavigate } from "react-router"
import { useAuth } from "@/contexts/AuthContext"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"

interface UserMenuProps {
  showSessionInDropdown?: boolean
}

function UserMenu({ showSessionInDropdown = false }: UserMenuProps) {
  const navigate = useNavigate()
  const { logout, username, role } = useAuth()
  function handleLogout() {
    logout()
    navigate("/")
  }
  const initial = username ? username.charAt(0).toUpperCase() : "?"
  const { t } = useTranslation()

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <button className="flex items-center gap-2 rounded-md px-2 py-1 hover:bg-accent">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary text-sm font-medium text-primary-foreground">
              {initial}
            </span>
            <span className="hidden flex-col items-start leading-tight md:flex">
              <span className="text-sm">{username}</span>
              <span className="text-xs text-muted-foreground">{role}</span>
            </span>
          </button>
        }
      />
      <DropdownMenuContent align="end">
        {showSessionInDropdown && (
          <>
            <div className="flex items-center gap-2 px-2 py-1.5">
              <span className="text-sm font-medium">{username}</span>
              <span className="rounded bg-muted px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-muted-foreground">
                {role}
              </span>
            </div>
            <DropdownMenuSeparator />
          </>
        )}
        <DropdownMenuItem onClick={handleLogout}>{t("common.logout")}</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

export default UserMenu