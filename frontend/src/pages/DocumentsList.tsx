import { Link } from "react-router"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import { useQuery } from "@tanstack/react-query"
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
import { cn } from "@/lib/utils"

interface DocumentSummary {
  id: number
  fileName: string
  status: string
  uploadedByUsername: string
  uploadedAt: string
}

interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

const STATUS_COLORS: Record<string, string> = {
  PROCESSING: "bg-muted text-muted-foreground",
  PENDING_REVIEW: "bg-orange-500/10 text-orange-600",
  VALIDATED: "bg-emerald-500/10 text-emerald-600",
  REJECTED_EMPTY: "bg-muted text-muted-foreground",
  REJECTED_INVALID: "bg-destructive/10 text-destructive",
}

const PAGE_SIZE = 20

function DocumentsList() {
  const { t } = useTranslation()
  const [page, setPage] = useState(0)

  const { data, isLoading, isError } = useQuery({
    queryKey: ["documents", page],
    queryFn: async () => {
      const res = await api.get<PagedResponse<DocumentSummary>>(
        `/api/documents?page=${page}&size=${PAGE_SIZE}`
      )
      return res.data
    },
  })

  return (
    <div>
      <h1 className="font-amarego lowercase mb-4 text-3xl">{t("documents.title")}</h1>

      {isLoading && <p className="text-muted-foreground">{t("common.loading")}</p>}
      {isError && <p className="text-destructive">{t("documents.loadError")}</p>}

      {data && data.content.length === 0 && (
        <p className="text-muted-foreground">{t("documents.empty")}</p>
      )}

      {data && data.content.length > 0 && (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t("documents.fileNameHeader")}</TableHead>
                <TableHead>{t("documents.statusHeader")}</TableHead>
                <TableHead>{t("documents.uploadedByHeader")}</TableHead>
                <TableHead>{t("documents.uploadedAtHeader")}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.map((doc) => (
                <TableRow key={doc.id}>
                  <TableCell>
                    <Link to={`/documents/${doc.id}`} className="text-primary hover:underline">
                      {doc.fileName}
                    </Link>
                  </TableCell>
                  <TableCell>
                    <span
                      className={cn(
                        "rounded-full px-2 py-0.5 text-xs font-medium",
                        STATUS_COLORS[doc.status] ?? "bg-muted text-muted-foreground"
                      )}
                    >
                      {t(`documentStatus.${doc.status}`, doc.status)}
                    </span>
                  </TableCell>
                  <TableCell>{doc.uploadedByUsername}</TableCell>
                  <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
                    {new Date(doc.uploadedAt).toLocaleString()}
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
    </div>
  )
}

export default DocumentsList