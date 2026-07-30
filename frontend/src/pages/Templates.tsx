import { useState } from "react"
import { Link } from "react-router"
import { useTranslation } from "react-i18next"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { Button } from "@/components/ui/button"
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
import LoadingState from "@/components/ui/loading-state"
import EmptyState from "@/components/ui/empty-state"

interface TemplateSummary {
  id: number
  name: string
}

interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

function Templates() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [templatePendingDeactivation, setTemplatePendingDeactivation] = useState<TemplateSummary | null>(null)
  const [deactivateErrorCode, setDeactivateErrorCode] = useState<string | null>(null)

  const { data, isLoading, isError } = useQuery({
    queryKey: ["templates"],
    queryFn: async () => {
      const res = await api.get<PagedResponse<TemplateSummary>>("/api/templates?page=0&size=50")
      return res.data
    },
  })

  const deactivateMutation = useMutation({
    mutationFn: async (id: number) => {
      await api.delete(`/api/templates/${id}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["templates"] })
      setTemplatePendingDeactivation(null)
      setDeactivateErrorCode(null)
    },
    onError: () => {
      setDeactivateErrorCode("ACTION_FAILED")
    },
  })

  function handleOpenChange(open: boolean) {
    if (!open) {
      setTemplatePendingDeactivation(null)
      setDeactivateErrorCode(null)
    }
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="font-amarego lowercase text-3xl">{t("templates.title")}</h1>
        <Button render={<Link to="/templates/new">{t("templates.addButton")}</Link>} />
      </div>

      {isLoading && <LoadingState />}
      {isError && <p className="text-destructive">{t("templates.loadError")}</p>}

      {data && data.content.length === 0 && <EmptyState message={t("templates.empty")} />}
      {data && data.content.length > 0 && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>{t("templates.nameHeader")}</TableHead>
              <TableHead className="text-right">{t("templates.actionsHeader")}</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {data.content.map((template) => (
              <TableRow key={template.id}>
                <TableCell>{template.name}</TableCell>
                <TableCell className="text-right">
                  <Button
                    variant="ghost"
                    size="sm"
                    className="text-destructive"
                    onClick={() => setTemplatePendingDeactivation(template)}
                  >
                    {t("templates.deactivate")}
                  </Button>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}

      <AlertDialog open={templatePendingDeactivation !== null} onOpenChange={handleOpenChange}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>
              {t("templates.deactivateConfirmTitle", { name: templatePendingDeactivation?.name })}
            </AlertDialogTitle>
            <AlertDialogDescription>
              {t("templates.deactivateConfirmDescription")}
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
                templatePendingDeactivation && deactivateMutation.mutate(templatePendingDeactivation.id)
              }}
              disabled={deactivateMutation.isPending}
            >
              {deactivateMutation.isPending ? t("templates.deactivating") : t("templates.deactivate")}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}

export default Templates