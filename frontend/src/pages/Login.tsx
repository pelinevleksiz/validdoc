import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { Button } from "@/components/ui/button"

function Login() {
  const { data, isLoading, isError } = useQuery({
    queryKey: ["health"],
    queryFn: async () => {
      const res = await api.get("/actuator/health")
      return res.data
    },
  })

  return (
    <div className="flex min-h-screen items-center justify-center bg-slate-900">
      <div className="flex flex-col items-center gap-4">
        <h1 className="text-2xl font-bold text-white">Login sayfası (placeholder)</h1>
        <Button>shadcn Button testi</Button>
        <p className="text-white">
          {isLoading && "Backend kontrol ediliyor..."}
          {isError && "Backend'e ulaşılamadı"}
          {data && `Backend durumu: ${data.status}`}
        </p>
      </div>
    </div>
  )
}

export default Login