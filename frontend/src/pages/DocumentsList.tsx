import { Link } from "react-router"
import { useState } from "react"
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
import documentsTitle from "@/assets/belgeler-title.png"

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

const STATUS_LABELS: Record<string, string> = {
  PROCESSING: "İşleniyor",
  PENDING_REVIEW: "İnceleme Bekliyor",
  VALIDATED: "Onaylandı",
  REJECTED_EMPTY: "Boş",
  REJECTED_INVALID: "Geçersiz",
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
      <img src={documentsTitle} alt="Belgeler" className="mb-4 h-6.75 w-auto" />

      {isLoading && <p className="text-muted-foreground">Yükleniyor...</p>}
      {isError && <p className="text-destructive">Belgeler yüklenemedi.</p>}

      {data && data.content.length === 0 && (
        <p className="text-muted-foreground">Henüz belge yok.</p>
      )}

      {data && data.content.length > 0 && (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Dosya adı</TableHead>
                <TableHead>Durum</TableHead>
                <TableHead>Yükleyen</TableHead>
                <TableHead>Yüklenme zamanı</TableHead>
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
                      {STATUS_LABELS[doc.status] ?? doc.status}
                    </span>
                  </TableCell>
                  <TableCell>{doc.uploadedByUsername}</TableCell>
                  <TableCell className="whitespace-nowrap text-sm text-muted-foreground">
                    {new Date(doc.uploadedAt).toLocaleString("tr-TR")}
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
              Önceki
            </Button>
            <span className="text-sm text-muted-foreground">
              Sayfa {page + 1} / {data.totalPages}
            </span>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setPage((p) => Math.min(data.totalPages - 1, p + 1))}
              disabled={page >= data.totalPages - 1}
            >
              Sonraki
            </Button>
          </div>
        </>
      )}
    </div>
  )
}

export default DocumentsList