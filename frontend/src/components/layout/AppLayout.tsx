import { Outlet, useNavigate } from "react-router"
import NavSidebar from "@/components/layout/NavSidebar"
import LanguageSwitcher from "@/components/layout/LanguageSwitcher"
import SessionExpiryIndicator from "@/components/layout/SessionExpiryIndicator"
import { useAuth } from "@/contexts/AuthContext"
import { Button } from "@/components/ui/button"

function AppLayout() {
  const navigate = useNavigate()
  const { logout, username } = useAuth()

  function handleLogout() {
    logout()
    navigate("/")
  }

  return (
    <div className="flex min-h-screen">
      <NavSidebar />
      <div className="flex flex-1 flex-col">
        <header className="flex items-center justify-between border-b px-6 py-3">
          <SessionExpiryIndicator />
          <div className="flex items-center gap-4">
            <LanguageSwitcher />
            <span className="text-sm text-muted-foreground">{username}</span>
            <Button variant="outline" size="sm" onClick={handleLogout}>
              Çıkış yap
            </Button>
          </div>
        </header>
        <main className="flex-1 p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

export default AppLayout