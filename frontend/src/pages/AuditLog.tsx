import { useState } from "react"
import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { Button } from "@/components/ui/button"
import auditLogTitle from "@/assets/denetim-kayitlari-title.png"
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

const ACTION_LABELS: Record<string, string> = {
  USER_DEACTIVATED: "Kullanıcı devre dışı bırakıldı",
  TEMPLATE_DEACTIVATED: "Şablon devre dışı bırakıldı",
  PASSWORD_CHANGED: "Şifre değiştirildi",
  VALIDATION_SETTINGS_UPDATED: "Doğrulama ayarları güncellendi",
  DOCUMENT_UPLOADED: "Belge yüklendi",
  RETENTION_PURGE: "Saklama süresi doldu, segment verisi anonimleştirildi",
  RETENTION_ABANDONED_REVIEW_EXPIRED: "İnceleme süresi doldu, belge otomatik reddedildi",
}

function formatAction(action: string) {
  return ACTION_LABELS[action] ?? action
}

const PAGE_SIZE = 20

function AuditLog() {
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

  return (
    <div>
      <img src={auditLogTitle} alt="Denetim kayıtları" className="mb-4 h-7 w-auto" />

      {isLoading && <p className="text-muted-foreground">Yükleniyor...</p>}
      {isError && <p className="text-destructive">Kayıtlar yüklenemedi.</p>}

      {data && data.content.length === 0 && (
        <p className="text-muted-foreground">Henüz kayıt yok.</p>
      )}

      {data && data.content.length > 0 && (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Zaman</TableHead>
                <TableHead>İşlem</TableHead>
                <TableHead>Yapan</TableHead>
                <TableHead>Belge</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.map((entry) => (
                <TableRow key={entry.id}>
                  <TableCell className="whitespace-nowrap text-sm">
                    {new Date(entry.timestamp).toLocaleString("tr-TR")}
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

export default AuditLog