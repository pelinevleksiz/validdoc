import { useEffect, useState } from "react"
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
import reviewQueueTitle from "@/assets/inceleme-kuyrugu-title.png"

interface DocumentSummary {
  id: number
  fileName: string
  status: string
  segmentResults: string | null
  templateId: number
  failureReason?: string | null
}

interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

interface SegmentResult {
  segmentId: number
  label: string
  outcome: string
  reason?: string
}

interface TemplateSegmentDetail {
  id: number
  label: string
  page: number
  rules: { type: string; param?: number }[]
}

interface TemplateDetail {
  id: number
  name: string
  segments: TemplateSegmentDetail[]
}

const RULE_LABELS: Record<string, string> = {
  LETTERS_ONLY: "Yalnızca harf",
  DIGITS_ONLY: "Yalnızca rakam",
  ALPHANUMERIC: "Harf ve rakam",
  DATE: "Tarih",
  MIN_LENGTH: "Minimum uzunluk",
  MAX_LENGTH: "Maksimum uzunluk",
  TC_KIMLIK_NO: "TC Kimlik No",
  VKN: "Vergi Kimlik No (VKN)",
  PHONE_TR: "Telefon (TR)",
  EMAIL: "E-posta",
  SIGNATURE_INK: "İmza",
  STAMP_INK: "Mühür",
}

function ReviewQueue() {
  const queryClient = useQueryClient()
  const [selectedDocId, setSelectedDocId] = useState<number | null>(null)
  const [imageUrl, setImageUrl] = useState<string | null>(null)
  const [resolveError, setResolveError] = useState<string | null>(null)

  const { data: queue, isLoading: queueLoading } = useQuery({
    queryKey: ["review-queue"],
    queryFn: async () => {
      const res = await api.get<PagedResponse<DocumentSummary>>("/api/documents/queue?page=0&size=50")
      return res.data
    },
    enabled: selectedDocId === null,
  })

  const { data: document } = useQuery({
    queryKey: ["review-document", selectedDocId],
    queryFn: async () => {
      const res = await api.get<DocumentSummary>(`/api/documents/${selectedDocId}`)
      return res.data
    },
    enabled: selectedDocId !== null,
  })

  const { data: template } = useQuery({
    queryKey: ["review-template", document?.templateId],
    queryFn: async () => {
      const res = await api.get<TemplateDetail>(`/api/templates/${document?.templateId}`)
      return res.data
    },
    enabled: !!document?.templateId,
  })

  const results: SegmentResult[] = document?.segmentResults ? JSON.parse(document.segmentResults) : []
  const pendingSegments = results.filter((r) => r.outcome === "PENDING_REVIEW")
  const currentSegment = pendingSegments[0] ?? null
  const templateSegment = template?.segments.find((s) => s.id === currentSegment?.segmentId)

  useEffect(() => {
    if (!selectedDocId || !currentSegment) {
      setImageUrl(null)
      return
    }
    let objectUrl: string | null = null
    api
      .get(`/api/documents/${selectedDocId}/segments/${currentSegment.segmentId}/image`, {
        responseType: "blob",
      })
      .then((res) => {
        objectUrl = URL.createObjectURL(res.data)
        setImageUrl(objectUrl)
      })
    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [selectedDocId, currentSegment?.segmentId])

  const resolveMutation = useMutation({
    mutationFn: async (outcome: "FILLED_VALID" | "FILLED_INVALID") => {
      if (!selectedDocId || !currentSegment) return
      await api.post(`/api/documents/${selectedDocId}/segments/${currentSegment.segmentId}/resolve`, {
        outcome,
      })
    },
    onSuccess: () => {
      setResolveError(null)
      queryClient.invalidateQueries({ queryKey: ["review-document", selectedDocId] })
      queryClient.invalidateQueries({ queryKey: ["review-queue"] })
    },
    onError: () => {
      setResolveError("İşlem başarısız oldu.")
    },
  })

  useEffect(() => {
    if (!currentSegment || resolveMutation.isPending) return
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key.toLowerCase() === "v") resolveMutation.mutate("FILLED_VALID")
      if (e.key.toLowerCase() === "i") resolveMutation.mutate("FILLED_INVALID")
    }
    window.addEventListener("keydown", handleKeyDown)
    return () => window.removeEventListener("keydown", handleKeyDown)
  }, [currentSegment, resolveMutation])

  function backToQueue() {
    setSelectedDocId(null)
  }

  if (selectedDocId === null) {
    return (
      <div>
        <img
          src={reviewQueueTitle}
          alt="İnceleme Kuyruğu"
          className="mb-4 h-9 w-auto"
          style={{ transform: "translateY(-7.2px)" }}
        />

        {queueLoading && <p className="text-muted-foreground">Yükleniyor...</p>}
        {queue && queue.content.length === 0 && (
          <p className="text-muted-foreground">Bekleyen belge yok.</p>
        )}

        {queue && queue.content.length > 0 && (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Dosya adı</TableHead>
                <TableHead className="text-right">İşlem</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {queue.content.map((doc) => (
                <TableRow key={doc.id}>
                  <TableCell>{doc.fileName}</TableCell>
                  <TableCell className="text-right">
                    <Button size="sm" onClick={() => setSelectedDocId(doc.id)}>
                      İncele
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </div>
    )
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-bold">{document?.fileName}</h1>
        <Button variant="outline" size="sm" onClick={backToQueue}>
          Kuyruğa dön
        </Button>
      </div>

      {!currentSegment && (
        <div>
          {document?.segmentResults === null && document?.failureReason ? (
            <div className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {document.failureReason}
            </div>
          ) : (
            <p className="text-muted-foreground">Bu belgede incelenecek segment kalmadı.</p>
          )}
        </div>
      )}

      {currentSegment && (
        <div className="max-w-md">
          <p className="mb-1 text-sm text-muted-foreground">
            Kalan: {pendingSegments.length} segment
          </p>
          <h2 className="mb-2 text-lg font-semibold">{currentSegment.label}</h2>

          {currentSegment.reason && (
            <p className="mb-1 text-sm text-muted-foreground">{currentSegment.reason}</p>
          )}

          {templateSegment && templateSegment.rules.length > 0 && (
            <p className="mb-3 text-sm text-muted-foreground">
              Beklenen: {templateSegment.rules.map((r) => RULE_LABELS[r.type] ?? r.type).join(", ")}
            </p>
          )}

          {imageUrl && (
            <img src={imageUrl} alt={currentSegment.label} className="mb-4 w-full rounded-md border" />
          )}

          {resolveError && (
            <div className="mb-3 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {resolveError}
            </div>
          )}

          <div className="flex gap-2">
            <Button
              className="flex-1 bg-emerald-600 hover:bg-emerald-700"
              onClick={() => resolveMutation.mutate("FILLED_VALID")}
              disabled={resolveMutation.isPending}
            >
              Geçerli (V)
            </Button>
            <Button
              variant="destructive"
              className="flex-1"
              onClick={() => resolveMutation.mutate("FILLED_INVALID")}
              disabled={resolveMutation.isPending}
            >
              Geçersiz (I)
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}

export default ReviewQueue