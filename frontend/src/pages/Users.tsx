import { useState } from "react"
import { useSearchParams } from "react-router"
import { useTranslation } from "react-i18next"
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
import LoadingState from "@/components/ui/loading-state"
import EmptyState from "@/components/ui/empty-state"

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

const PAGE_SIZE = 20

function Users() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const openCreateOnLoad = searchParams.get("new") === "1"
  const [page, setPage] = useState(0)
  const [userPendingDeactivation, setUserPendingDeactivation] = useState<UserSummary | null>(null)
  const [deactivateErrorCode, setDeactivateErrorCode] = useState<string | null>(null)

  const { data, isLoading, isError } = useQuery({
    queryKey: ["users", page],
    queryFn: async () => {
      const res = await api.get<PagedResponse<UserSummary>>(`/api/users?page=${page}&size=${PAGE_SIZE}`)
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
      setDeactivateErrorCode(null)
    },
    onError: () => {
      setDeactivateErrorCode("ACTION_FAILED")
    },
  })

  function handleOpenChange(open: boolean) {
    if (!open) {
      setUserPendingDeactivation(null)
      setDeactivateErrorCode(null)
    }
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="font-amarego lowercase text-3xl">{t("users.title")}</h1>
        <CreateUserDialog defaultOpen={openCreateOnLoad} />
      </div>

      {isLoading && <LoadingState />}
      {isError && <p className="text-destructive">{t("users.loadError")}</p>}

      {data && data.content.length === 0 && <EmptyState message={t("users.empty")} />}

      {data && data.content.length > 0 && (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("users.usernameHeader")}</TableHead>
                <TableHead>{t("users.roleHeader")}</TableHead>
                <TableHead className="text-right">{t("users.actionsHeader")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.map((user) => (
                <TableRow key={user.id}>
                  <TableCell>{user.username}</TableCell>
                  <TableCell>
                    <Badge variant={user.role === "ADMIN" ? "default" : "secondary"}>
                      {user.role === "ADMIN" ? t("users.roleAdmin") : t("users.roleOperator")}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="ghost"
                      size="sm"
                      className="text-destructive"
                      onClick={() => setUserPendingDeactivation(user)}
                    >
                      {t("users.deactivate")}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>

          <div className="mt-4 flex items-center justify-between">
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
            >
              {t("common.previous")}
            </Button>
            <span className="text-sm text-muted-foreground">
              {t("common.page", { current: page + 1, total: data.totalPages })}
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.min(data.totalPages - 1, p + 1))}
              disabled={page >= data.totalPages - 1}
            >
              {t("common.next")}
            </Button>
          </div>
        </>
      )}

      <AlertDialog open={userPendingDeactivation !== null} onOpenChange={handleOpenChange}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {t("users.deactivateConfirmTitle", { username: userPendingDeactivation?.username })}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {t("users.deactivateConfirmDescription")}
            </AlertDialogDescription>
          </AlertDialogHeader>

          {deactivateErrorCode && (
            <div className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {t(`errors.${deactivateErrorCode}`)}
            </div>
          )}

          <AlertDialogFooter>
            <AlertDialogCancel>{t("common.cancel")}</AlertDialogCancel>
            <AlertDialogAction
              onClick={(e) => {
                e.preventDefault()
                userPendingDeactivation && deactivateMutation.mutate(userPendingDeactivation.id)
              }}
              disabled={deactivateMutation.isPending}
            >
              {deactivateMutation.isPending ? t("users.deactivating") : t("users.deactivate")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}

export default Users