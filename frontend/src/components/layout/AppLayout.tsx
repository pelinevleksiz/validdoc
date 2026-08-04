import { Outlet } from "react-router"
import NavSidebar from "@/components/layout/NavSidebar"
import MobileNavMenu from "@/components/layout/MobileNavMenu"
import LanguageSwitcher from "@/components/layout/LanguageSwitcher"
import SessionExpiryIndicator from "@/components/layout/SessionExpiryIndicator"
import UserMenu from "@/components/layout/UserMenu"

function AppLayout() {
  return (
    <div className="flex min-h-screen flex-col md:flex-row">
      <NavSidebar />

      <div className="flex h-16 items-center justify-between border-b px-4 md:hidden">
        <div className="flex items-center gap-2.5">
          <MobileNavMenu />
          <span className="font-amarego lowercase text-xl">validdoc</span>
        </div>

        <div className="flex items-center gap-1.5">
          <LanguageSwitcher variant="mobile" />
          <SessionExpiryIndicator variant="mobile" />
          <UserMenu showSessionInDropdown />
        </div>
      </div>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="hidden items-center justify-between border-b px-6 py-3 md:flex">
          <div className="flex items-center gap-3">
            <SessionExpiryIndicator />
            <LanguageSwitcher />
          </div>
          <UserMenu />
        </header>
        <main className="flex-1 p-4 md:p-6">
          <Outlet />
        </main>
      </div>
    </div>
  )
}

export default AppLayout