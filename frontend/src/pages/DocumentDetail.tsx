import { useParams } from "react-router"
import { useTranslation } from "react-i18next"
import { useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { cn } from "@/lib/utils"

interface DocumentDetailData {
  id: number
  fileName: string
  status: string
  templateId: number
  segmentResults: string | null
  uploadedByUsername: string
  uploadedAt: string
}

interface SegmentResult {
  segmentId: number
  label: string
  outcome: "FILLED_VALID" | "FILLED_INVALID" | "EMPTY" | "PENDING_REVIEW"
  failedRules?: string[]
  maskedValue?: string
  ocrConfidence?: number
}

interface TemplateSegmentDetail {
  id: number
  label: string
  page: number
}

interface TemplateDetail {
  id: number
  name: string
  segments: TemplateSegmentDetail[]
}

const OUTCOME_COLORS: Record<string, string> = {
  FILLED_VALID: "bg-emerald-500/10 text-emerald-600",
  FILLED_INVALID: "bg-destructive/10 text-destructive",
  EMPTY: "bg-muted text-muted-foreground",
  PENDING_REVIEW: "bg-orange-500/10 text-orange-600",
}

function DocumentDetail() {
  const { t } = useTranslation()
  const { id } = useParams<{ id: string }>()

  const { data: document, isLoading: documentLoading } = useQuery({
    queryKey: ["document", id],
    queryFn: async () => {
      const res = await api.get<DocumentDetailData>(`/api/documents/${id}`)
      return res.data
    },
  })

  const { data: template } = useQuery({
    queryKey: ["template-for-document", document?.templateId],
    queryFn: async () => {
      const res = await api.get<TemplateDetail>(`/api/templates/${document?.templateId}`)
      return res.data
    },
    enabled: !!document?.templateId,
  })

  if (documentLoading) {
    return <p className="text-muted-foreground">{t("common.loading")}</p>
  }

  if (!document) {
    return <p className="text-destructive">{t("documents.loadError")}</p>
  }

  const results: SegmentResult[] = document.segmentResults
    ? JSON.parse(document.segmentResults)
    : []

  const pageBySegmentId = new Map<number, number>(
    template?.segments.map((s) => [s.id, s.page]) ?? []
  )

  const counts = {
    FILLED_VALID: results.filter((r) => r.outcome === "FILLED_VALID").length,
    FILLED_INVALID: results.filter((r) => r.outcome === "FILLED_INVALID").length,
    EMPTY: results.filter((r) => r.outcome === "EMPTY").length,
    PENDING_REVIEW: results.filter((r) => r.outcome === "PENDING_REVIEW").length,
  }

  return (
    <div>
      <h1 className="mb-1 text-2xl font-bold">{document.fileName}</h1>
      <p className="mb-4 text-sm text-muted-foreground">
        {t(`documentStatus.${document.status}`, document.status)} ·{" "}
        {t("documents.uploadedBy", { username: document.uploadedByUsername })} ·{" "}
        {new Date(document.uploadedAt).toLocaleString()}
      </p>

      {results.length > 0 && (
        <div className="mb-4 flex flex-wrap gap-2">
          <span className="rounded-full bg-emerald-500/10 px-3 py-1 text-xs font-medium text-emerald-600">
            {counts.FILLED_VALID} {t("segmentOutcome.FILLED_VALID")}
          </span>
          <span className="rounded-full bg-destructive/10 px-3 py-1 text-xs font-medium text-destructive">
            {counts.FILLED_INVALID} {t("segmentOutcome.FILLED_INVALID")}
          </span>
          <span className="rounded-full bg-muted px-3 py-1 text-xs font-medium text-muted-foreground">
            {counts.EMPTY} {t("segmentOutcome.EMPTY")}
          </span>
          <span className="rounded-full bg-orange-500/10 px-3 py-1 text-xs font-medium text-orange-600">
            {counts.PENDING_REVIEW} {t("segmentOutcome.PENDING_REVIEW")}
          </span>
        </div>
      )}

      {results.length === 0 && (
        <p className="text-sm text-muted-foreground">{t("documents.noResults")}</p>
      )}

      {results.length > 0 && (
        <div className="flex flex-col gap-2">
          {results.map((r) => (
            <div key={r.segmentId} className="rounded-md border px-3 py-2">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium">{r.label}</span>
                <span
                  className={cn(
                    "rounded-full px-2 py-0.5 text-xs font-medium",
                    OUTCOME_COLORS[r.outcome]
                  )}
                >
                  {t(`segmentOutcome.${r.outcome}`, r.outcome)}
                </span>
              </div>
              <p className="mt-1 text-xs text-muted-foreground">
                {t("documents.page", { page: pageBySegmentId.get(r.segmentId) ?? "?" })}
                {r.maskedValue && ` · ${t("documents.value", { value: r.maskedValue })}`}
              </p>
              {r.failedRules && r.failedRules.length > 0 && (
                <p className="mt-1 text-xs text-destructive">
                  {t("documents.failedRules", {
                    rules: r.failedRules.map((rule) => t(`rules.${rule}`, rule)).join(", "),
                  })}
                </p>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

export default DocumentDetail