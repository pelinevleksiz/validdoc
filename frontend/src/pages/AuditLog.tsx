import { useState } from "react"
import { useTranslation } from "react-i18next"
import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { Button } from "@/components/ui/button"
import LoadingState from "@/components/ui/loading-state"
import EmptyState from "@/components/ui/empty-state"
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table"

interface AuditLogEntry {
  id: number
  documentId: number | null
  action: string
  performedBy: string
  timestamp: string
}

interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

const PAGE_SIZE = 20

function AuditLog() {
  const { t } = useTranslation()
  const [page, setPage] = useState(0)

  const { data, isLoading, isError } = useQuery({
    queryKey: ["audit-logs", page],
    queryFn: async () => {
      const res = await api.get<PagedResponse<AuditLogEntry>>(
        `/api/admin/audit-logs?page=${page}&size=${PAGE_SIZE}`
      )
      return res.data
    },
  })

  function formatAction(action: string) {
    return t(`auditLog.actions.${action}`, action)
  }

  return (
    <div>
      <h1 className="font-amarego lowercase mb-4 text-3xl">{t("auditLog.title")}</h1>

      {isLoading && <LoadingState />}
      {isError && <p className="text-destructive">{t("auditLog.loadError")}</p>}

      {data && data.content.length === 0 && <EmptyState message={t("auditLog.empty")} />}

      {data && data.content.length > 0 && (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("auditLog.timeHeader")}</TableHead>
                <TableHead>{t("auditLog.actionHeader")}</TableHead>
                <TableHead>{t("auditLog.byHeader")}</TableHead>
                <TableHead>{t("auditLog.documentHeader")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.map((entry) => (
                <TableRow key={entry.id}>
                  <TableCell className="whitespace-nowrap text-sm">
                    {new Date(entry.timestamp).toLocaleString()}
                  </TableCell>
                  <TableCell>{formatAction(entry.action)}</TableCell>
                  <TableCell>{entry.performedBy}</TableCell>
                  <TableCell>{entry.documentId ?? "—"}</TableCell>
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
    </div>
  )
}

export default AuditLog