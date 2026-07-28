import { useState } from "react"
import axios from "axios"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { Button } from "@/components/ui/button"
import { Badge } from "@/components/ui/badge"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog"
import CreateUserDialog from "@/components/users/CreateUserDialog"
import usersTitle from "@/assets/kullanicilar-title.png"

export interface UserSummary {
  id: number
  username: string
  role: "ADMIN" | "OPERATOR"
}

interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

function Users() {
  const queryClient = useQueryClient()
  const [userPendingDeactivation, setUserPendingDeactivation] = useState<UserSummary | null>(null)
  const [deactivateError, setDeactivateError] = useState<string | null>(null)

  const { data, isLoading, isError } = useQuery({
    queryKey: ["users"],
    queryFn: async () => {
      const res = await api.get<PagedResponse<UserSummary>>("/api/users?page=0&size=50")
      return res.data
    },
  })

  const deactivateMutation = useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`/api/users/${id}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] })
      setUserPendingDeactivation(null)
      setDeactivateError(null)
    },
    onError: (error: unknown) => {
      if (axios.isAxiosError(error) && error.response?.data?.message) {
        setDeactivateError(error.response.data.message)
      } else {
        setDeactivateError("İşlem başarısız oldu.")
      }
    },
  })

  function handleOpenChange(open: boolean) {
    if (!open) {
      setUserPendingDeactivation(null)
      setDeactivateError(null)
    }
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <img src={usersTitle} alt="Kullanıcılar" className="h-6.75 w-auto" />
        <CreateUserDialog />
      </div>

      {isLoading && <p className="text-muted-foreground">Yükleniyor...</p>}
      {isError && <p className="text-destructive">Kullanıcılar yüklenemedi.</p>}

      {data && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Kullanıcı adı</TableHead>
              <TableHead>Rol</TableHead>
              <TableHead className="text-right">İşlemler</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.content.map((user) => (
              <TableRow key={user.id}>
                <TableCell>{user.username}</TableCell>
                <TableCell>
                  <Badge variant={user.role === "ADMIN" ? "default" : "secondary"}>
                    {user.role === "ADMIN" ? "Admin" : "Operatör"}
                  </Badge>
                </TableCell>
                <TableCell className="text-right">
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-destructive"
                    onClick={() => setUserPendingDeactivation(user)}
                  >
                    Devre dışı bırak
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <AlertDialog open={userPendingDeactivation !== null} onOpenChange={handleOpenChange}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {userPendingDeactivation?.username} devre dışı bırakılsın mı?
            </AlertDialogTitle>
            <AlertDialogDescription>
              Bu kullanıcı artık sisteme giriş yapamayacak. Geçmiş belge kayıtları etkilenmez.
            </AlertDialogDescription>
          </AlertDialogHeader>

          {deactivateError && (
            <div className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {deactivateError}
            </div>
          )}

          <AlertDialogFooter>
            <AlertDialogCancel>Vazgeç</AlertDialogCancel>
            <AlertDialogAction
              onClick={(e) => {
                e.preventDefault()
                userPendingDeactivation && deactivateMutation.mutate(userPendingDeactivation.id)
              }}
              disabled={deactivateMutation.isPending}
            >
              {deactivateMutation.isPending ? "İşleniyor..." : "Devre dışı bırak"}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}

export default Users