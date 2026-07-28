import { Outlet } from "react-router"
import NavSidebar from "@/components/layout/NavSidebar"
import LanguageSwitcher from "@/components/layout/LanguageSwitcher"
import SessionExpiryIndicator from "@/components/layout/SessionExpiryIndicator"
import UserMenu from "@/components/layout/UserMenu"

function AppLayout() {
  return (
    <div className="flex min-h-screen">
      <NavSidebar />
      <div className="flex flex-1 flex-col">
        <header className="flex items-center justify-between border-b px-6 py-3">
          <div className="flex items-center gap-3">
            <SessionExpiryIndicator />
            <LanguageSwitcher />
          </div>
          <UserMenu />
        </header>
        <main className="flex-1 p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

export default AppLayout