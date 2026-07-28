import { useNavigate } from "react-router"
import { useAuth } from "@/contexts/AuthContext"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"

function UserMenu() {
  const navigate = useNavigate()
  const { logout, username, role } = useAuth()

  function handleLogout() {
    logout()
    navigate("/")
  }

  const initial = username ? username.charAt(0).toUpperCase() : "?"

  return (
    <DropdownMenu>
      <DropdownMenuTrigger
        render={
          <button className="flex items-center gap-2 rounded-md px-2 py-1 hover:bg-accent">
            <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-primary text-sm font-medium text-primary-foreground">
              {initial}
            </span>
            <span className="flex flex-col items-start leading-tight">
              <span className="text-sm">{username}</span>
              <span className="text-xs text-muted-foreground">{role}</span>
            </span>
          </button>
        }
      />
      <DropdownMenuContent align="end">
        <DropdownMenuItem onClick={handleLogout}>Çıkış yap</DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

export default UserMenu