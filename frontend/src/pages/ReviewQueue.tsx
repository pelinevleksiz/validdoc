import { useEffect, useState } from "react"
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

const PAGE_SIZE = 20

function ReviewQueue() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [page, setPage] = useState(0)
  const [selectedDocId, setSelectedDocId] = useState<number | null>(null)
  const [imageUrl, setImageUrl] = useState<string | null>(null)
  const [resolveError, setResolveError] = useState<string | null>(null)

  const { data: queue, isLoading: queueLoading } = useQuery({
    queryKey: ["review-queue", page],
    queryFn: async () => {
      const res = await api.get<PagedResponse<DocumentSummary>>(`/api/documents/queue?page=${page}&size=${PAGE_SIZE}`)
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
      setResolveError(t("errors.ACTION_FAILED"))
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
        <h1 className="font-amarego lowercase mb-4 text-3xl">{t("reviewQueue.title")}</h1>

        {queueLoading && <p className="text-muted-foreground">{t("common.loading")}</p>}
        {queue && queue.content.length === 0 && (
          <p className="text-muted-foreground">{t("reviewQueue.empty")}</p>
        )}

        {queue && queue.content.length > 0 && (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>{t("reviewQueue.fileNameHeader")}</TableHead>
                  <TableHead className="text-right">{t("reviewQueue.actionHeader")}</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {queue.content.map((doc) => (
                  <TableRow key={doc.id}>
                    <TableCell>{doc.fileName}</TableCell>
                    <TableCell className="text-right">
                      <Button size="sm" onClick={() => setSelectedDocId(doc.id)}>
                        {t("reviewQueue.review")}
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
                {t("common.page", { current: page + 1, total: queue.totalPages })}
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage((p) => Math.min(queue.totalPages - 1, p + 1))}
                disabled={page >= queue.totalPages - 1}
              >
                {t("common.next")}
              </Button>
            </div>
          </>
        )}
      </div>
    )
  }

  return (
    <div>
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-bold">{document?.fileName}</h1>
        <Button variant="outline" size="sm" onClick={backToQueue}>
          {t("reviewQueue.backToQueue")}
        </Button>
      </div>

      {!currentSegment && (
        <div>
          {document?.segmentResults === null && document?.failureReason ? (
            <div className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {document.failureReason}
            </div>
          ) : (
            <p className="text-muted-foreground">{t("reviewQueue.noSegments")}</p>
          )}
        </div>
      )}

      {currentSegment && (
        <div className="max-w-md">
          <p className="mb-1 text-sm text-muted-foreground">
            {t("reviewQueue.remaining", { count: pendingSegments.length })}
          </p>
          <h2 className="mb-2 text-lg font-semibold">{currentSegment.label}</h2>

          {currentSegment.reason && (
            <div className="mb-3 rounded-md bg-orange-500/10 px-3 py-2 text-sm font-medium text-orange-700">
              {currentSegment.reason}
            </div>
          )}

          {templateSegment && templateSegment.rules.length > 0 && (
            <p className="mb-3 text-sm text-muted-foreground">
              {t("upload.expected", {
                rules: templateSegment.rules.map((r) => t(`rules.${r.type}`, r.type)).join(", "),
              })}
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
              {t("upload.valid")}
            </Button>
            <Button
              variant="destructive"
              className="flex-1"
              onClick={() => resolveMutation.mutate("FILLED_INVALID")}
              disabled={resolveMutation.isPending}
            >
              {t("upload.invalid")}
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}

export default ReviewQueue