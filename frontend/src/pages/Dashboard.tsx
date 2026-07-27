import { useNavigate } from "react-router"
import { useAuth } from "@/contexts/AuthContext"
import { Button } from "@/components/ui/button"

function Dashboard() {
  const navigate = useNavigate()
  const { logout, username } = useAuth()

  function handleLogout() {
    logout()
    navigate("/")
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-900">
      <div className="flex flex-col items-center gap-4">
        <h1 className="text-2xl font-bold text-white">
          Dashboard sayfası (placeholder) — hoş geldin, {username}
        </h1>
        <Button variant="outline" onClick={handleLogout}>
          Çıkış yap
        </Button>
      </div>
    </div>
  )
}

export default Dashboard