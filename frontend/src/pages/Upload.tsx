import { useEffect, useRef, useState } from "react"
import { Link } from "react-router"
import axios from "axios"
import { useMutation, useQuery } from "@tanstack/react-query"
import * as pdfjsLib from "pdfjs-dist"
import pdfjsWorker from "pdfjs-dist/build/pdf.worker.min.mjs?url"
import { api } from "@/lib/api"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import belgeYukleTitle from "@/assets/belge-yukle-title.png"

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfjsWorker

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

interface TemplateSegmentDetail {
  id: number
  label: string
  page: number
  rules: { type: string; param?: number }[]
}

interface TemplateDetail {
  id: number
  name: string
  pageCount: number
  segments: TemplateSegmentDetail[]
}

interface SegmentResult {
  segmentId: number
  label: string
  outcome: string
}

interface DocumentStatusData {
  id: number
  fileName: string
  status: "PROCESSING" | "PENDING_REVIEW" | "VALIDATED" | "REJECTED_EMPTY" | "REJECTED_INVALID"
  segmentResults: string | null
}

interface PendingFile {
  file: File
  pageCount: number
}

interface UploadJob {
  file: File
  documentId: number | null
  status: DocumentStatusData["status"] | "ERROR"
  segmentResults: string | null
  errorMessage: string | null
}

const STATUS_LABELS: Record<string, string> = {
  PROCESSING: "İşleniyor",
  PENDING_REVIEW: "İnceleme bekliyor",
  VALIDATED: "Onaylandı",
  REJECTED_EMPTY: "Boş belge olarak reddedildi",
  REJECTED_INVALID: "Geçersiz olarak reddedildi",
  ERROR: "Yükleme başarısız",
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

const ACCEPTED_TYPES = ["image/png", "image/jpeg", "application/pdf"]

type Step = "file" | "template" | "processing" | "done"

const STEPS = [
  { number: 1, label: "Yükle" },
  { number: 2, label: "Şablon" },
  { number: 3, label: "Sonuç" },
]

function stepToNumber(step: Step): 1 | 2 | 3 {
  if (step === "file") return 1
  if (step === "template") return 2
  return 3
}

function StepIndicator({ currentStep, isComplete }: { currentStep: 1 | 2 | 3; isComplete: boolean }) {
  return (
    <div className="mb-8 flex items-start">
      {STEPS.map((s, i) => {
        const done = currentStep > s.number || (isComplete && s.number === 3)
        const active = currentStep === s.number && !done

        return (
          <div key={s.number} className="flex flex-1 items-start last:flex-none">
            <div className="flex flex-col items-center gap-1.5">
              <div
                className={cn(
                  "flex h-9 w-9 shrink-0 items-center justify-center rounded-full border-2 text-sm font-semibold transition-colors",
                  done
                    ? "border-emerald-600 bg-emerald-600 text-white"
                    : active
                      ? "border-foreground bg-foreground text-background"
                      : "border-muted-foreground/30 text-muted-foreground"
                )}
              >
                {done ? "✓" : s.number}
              </div>
              <span
                className={cn(
                  "text-xs",
                  active || done ? "font-semibold text-foreground" : "text-muted-foreground"
                )}
              >
                {s.label}
              </span>
            </div>
            {i < STEPS.length - 1 && (
              <div
                className={cn(
                  "mx-2 mt-4.5 h-0.5 flex-1 -translate-y-1/2 transition-colors",
                  currentStep > s.number ? "bg-emerald-600" : "bg-muted-foreground/20"
                )}
              />
            )}
          </div>
        )
      })}
    </div>
  )
}

async function getFilePageCount(file: File): Promise<number> {
  if (file.type !== "application/pdf") return 1
  const buf = await file.arrayBuffer()
  const doc = await pdfjsLib.getDocument({ data: buf }).promise
  return doc.numPages
}

function Upload() {
  const [step, setStep] = useState<Step>("file")
  const [pendingFiles, setPendingFiles] = useState<PendingFile[]>([])
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null)
  const [language, setLanguage] = useState<"tur" | "eng">("tur")
  const [jobs, setJobs] = useState<UploadJob[]>([])
  const [expandedJobIndex, setExpandedJobIndex] = useState<number | null>(null)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [isDragging, setIsDragging] = useState(false)
  const [resolveError, setResolveError] = useState<string | null>(null)
  const [segmentImageUrl, setSegmentImageUrl] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)

  const { data: templates, isLoading: templatesLoading } = useQuery({
    queryKey: ["templates-for-upload"],
    queryFn: async () => {
      const res = await api.get<PagedResponse<TemplateSummary>>("/api/templates?page=0&size=50")
      return res.data
    },
    enabled: step === "template",
  })

  const { data: templateDetail } = useQuery({
    queryKey: ["template-detail-for-upload", selectedTemplateId],
    queryFn: async () => {
      const res = await api.get<TemplateDetail>(`/api/templates/${selectedTemplateId}`)
      return res.data
    },
    enabled: selectedTemplateId !== null,
  })

  const templateMaxPage = templateDetail ? templateDetail.pageCount : null

  const mismatchedFiles = pendingFiles.filter(
    (pf) => templateMaxPage !== null && pf.pageCount !== templateMaxPage
  )

  async function addFiles(newFiles: File[]) {
    const invalid = newFiles.find((f) => !ACCEPTED_TYPES.includes(f.type))
    if (invalid) {
      setUploadError("Sadece PDF, PNG veya JPEG dosyaları desteklenir.")
      return
    }

    const isDuplicate = (file: File) =>
      pendingFiles.some((pf) => pf.file.name === file.name && pf.file.size === file.size)
    const uniqueNewFiles = newFiles.filter((f) => !isDuplicate(f))

    setUploadError(
      uniqueNewFiles.length < newFiles.length ? "Bazı dosyalar zaten listede olduğu için tekrar eklenmedi." : null
    )

    const withPageCounts = await Promise.all(
      uniqueNewFiles.map(async (file) => ({ file, pageCount: await getFilePageCount(file) }))
    )
    setPendingFiles((prev) => [...prev, ...withPageCounts])
    setStep("template")
  }

  function removeFile(index: number) {
    setPendingFiles((prev) => prev.filter((_, i) => i !== index))
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const selected = e.target.files
    if (!selected || selected.length === 0) return
    addFiles(Array.from(selected))
    e.target.value = ""
  }

  function handleDragOver(e: React.DragEvent<HTMLDivElement>) {
    e.preventDefault()
    setIsDragging(true)
  }

  function handleDragLeave(e: React.DragEvent<HTMLDivElement>) {
    e.preventDefault()
    setIsDragging(false)
  }

  function handleDrop(e: React.DragEvent<HTMLDivElement>) {
    e.preventDefault()
    setIsDragging(false)
    const dropped = e.dataTransfer.files
    if (!dropped || dropped.length === 0) return
    addFiles(Array.from(dropped))
  }

  const uploadMutation = useMutation({
    mutationFn: async () => {
      const validFiles = pendingFiles.filter((pf) => !mismatchedFiles.includes(pf))
      const initialJobs: UploadJob[] = validFiles.map((pf) => ({
        file: pf.file,
        documentId: null,
        status: "PROCESSING",
        segmentResults: null,
        errorMessage: null,
      }))
      setJobs(initialJobs)
      setStep("processing")

      for (let i = 0; i < validFiles.length; i++) {
        try {
          const formData = new FormData()
          formData.append("file", validFiles[i].file)
          const res = await api.post<{ id: number; status: string }>(
            `/api/documents/upload?templateId=${selectedTemplateId}&lang=${language}`,
            formData
          )
          setJobs((prev) =>
            prev.map((job, idx) => (idx === i ? { ...job, documentId: res.data.id } : job))
          )
        } catch (error) {
          const message =
            axios.isAxiosError(error) && error.response?.data?.message
              ? error.response.data.message
              : "Yükleme başarısız oldu."
          setJobs((prev) =>
            prev.map((job, idx) => (idx === i ? { ...job, status: "ERROR", errorMessage: message } : job))
          )
        }
      }
      setStep("done")
    },
  })

  useEffect(() => {
    const stillProcessing = jobs.some((j) => j.status === "PROCESSING" && j.documentId !== null)
    if (!stillProcessing) return
    const timer = setTimeout(async () => {
      const updated = await Promise.all(
        jobs.map(async (job) => {
          if (job.status !== "PROCESSING" || job.documentId === null) return job
          const res = await api.get<DocumentStatusData>(`/api/documents/${job.documentId}`)
          return { ...job, status: res.data.status, segmentResults: res.data.segmentResults }
        })
      )
      setJobs(updated)
    }, 1000)
    return () => clearTimeout(timer)
  }, [jobs])

  const expandedJob = expandedJobIndex !== null ? jobs[expandedJobIndex] : null
  const expandedResults: SegmentResult[] = expandedJob?.segmentResults
    ? JSON.parse(expandedJob.segmentResults)
    : []
  const expandedPending = expandedResults.filter((r) => r.outcome === "PENDING_REVIEW")
  const currentPendingSegment = expandedPending[0] ?? null
  const currentTemplateSegment = templateDetail?.segments.find((s) => s.id === currentPendingSegment?.segmentId)

  useEffect(() => {
    if (!expandedJob?.documentId || !currentPendingSegment) {
      setSegmentImageUrl(null)
      return
    }
    let objectUrl: string | null = null
    api
      .get(`/api/documents/${expandedJob.documentId}/segments/${currentPendingSegment.segmentId}/image`, {
        responseType: "blob",
      })
      .then((res) => {
        objectUrl = URL.createObjectURL(res.data)
        setSegmentImageUrl(objectUrl)
      })
    return () => {
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [expandedJob?.documentId, currentPendingSegment?.segmentId])

  const resolveMutation = useMutation({
    mutationFn: async (outcome: "FILLED_VALID" | "FILLED_INVALID") => {
      if (!expandedJob?.documentId || !currentPendingSegment || expandedJobIndex === null) return
      await api.post(
        `/api/documents/${expandedJob.documentId}/segments/${currentPendingSegment.segmentId}/resolve`,
        { outcome }
      )
      const res = await api.get<DocumentStatusData>(`/api/documents/${expandedJob.documentId}`)
      setJobs((prev) =>
        prev.map((job, idx) =>
          idx === expandedJobIndex
            ? { ...job, status: res.data.status, segmentResults: res.data.segmentResults }
            : job
        )
      )
    },
    onSuccess: () => setResolveError(null),
    onError: () => setResolveError("İşlem başarısız oldu."),
  })

  useEffect(() => {
    if (!currentPendingSegment || resolveMutation.isPending) return
    function handleKeyDown(e: KeyboardEvent) {
      if (e.key.toLowerCase() === "v") resolveMutation.mutate("FILLED_VALID")
      if (e.key.toLowerCase() === "i") resolveMutation.mutate("FILLED_INVALID")
    }
    window.addEventListener("keydown", handleKeyDown)
    return () => window.removeEventListener("keydown", handleKeyDown)
  }, [currentPendingSegment, resolveMutation])

  function reset() {
    setStep("file")
    setPendingFiles([])
    setSelectedTemplateId(null)
    setLanguage("tur")
    setJobs([])
    setExpandedJobIndex(null)
    setUploadError(null)
    setResolveError(null)
  }

  const allJobsFinal = jobs.length > 0 && jobs.every((j) => j.status !== "PROCESSING")

  return (
    <div className="max-w-lg">
      <img src={belgeYukleTitle} alt="Belge yükle" className="mb-6 h-7.5 w-auto" />

      <StepIndicator currentStep={stepToNumber(step)} isComplete={step === "done" && allJobsFinal} />

      <input
        ref={fileInputRef}
        type="file"
        accept="image/png,image/jpeg,application/pdf"
        multiple
        className="hidden"
        onChange={handleFileChange}
      />

      {step === "file" && (
        <div
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
          className={cn(
            "flex h-64 flex-col items-center justify-center gap-3 rounded-md border border-dashed transition-colors",
            isDragging ? "border-primary bg-primary/5" : "border-muted-foreground/30"
          )}
        >
          <p className="text-sm text-muted-foreground">
            {isDragging ? "Bırak..." : "PDF, PNG veya JPEG — birden fazla dosyayı sürükleyin ya da seçin"}
          </p>
          <Button onClick={() => fileInputRef.current?.click()}>Dosya seç</Button>
        </div>
      )}

      {step === "template" && (
        <div>
          {uploadError && (
            <div className="mb-3 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {uploadError}
            </div>
          )}

          <p className="mb-2 text-sm font-medium">Seçilen dosyalar ({pendingFiles.length})</p>
          <div className="mb-4 flex flex-col gap-1.5">
            {pendingFiles.map((pf, i) => {
              const mismatched = mismatchedFiles.includes(pf)
              return (
                <div
                  key={i}
                  className={cn(
                    "flex items-center justify-between rounded-md border px-3 py-1.5 text-sm",
                    mismatched && "border-destructive/50 bg-destructive/5"
                  )}
                >
                  <span className="truncate">
                    {pf.file.name} <span className="text-muted-foreground">({pf.pageCount} sayfa)</span>
                    {mismatched && (
                      <span className="ml-2 text-xs text-destructive">
                        şablon tam {templateMaxPage} sayfa istiyor
                      </span>
                    )}
                  </span>
                  <button
                    type="button"
                    onClick={() => removeFile(i)}
                    className="ml-2 shrink-0 text-muted-foreground hover:text-destructive"
                  >
                    ×
                  </button>
                </div>
              )
            })}
            <Button variant="outline" size="sm" onClick={() => fileInputRef.current?.click()}>
              + Dosya ekle
            </Button>
          </div>

          <div className="mb-3 flex items-center gap-2">
            <span className="text-sm text-muted-foreground">Dil:</span>
            <Button
              size="sm"
              variant={language === "tur" ? "default" : "outline"}
              onClick={() => setLanguage("tur")}
            >
              TR
            </Button>
            <Button
              size="sm"
              variant={language === "eng" ? "default" : "outline"}
              onClick={() => setLanguage("eng")}
            >
              EN
            </Button>
          </div>

          {templatesLoading && <p className="text-muted-foreground">Şablonlar yükleniyor...</p>}

          {templates && templates.content.length > 0 && (
            <div className="mb-2 grid grid-cols-2 gap-2">
              {templates.content.map((template) => (
                <button
                  key={template.id}
                  type="button"
                  onClick={() => setSelectedTemplateId(template.id)}
                  className={cn(
                    "rounded-md border px-3 py-2 text-left text-sm",
                    selectedTemplateId === template.id
                      ? "border-primary bg-primary/10"
                      : "hover:bg-accent"
                  )}
                >
                  {template.name}
                </button>
              ))}
            </div>
          )}

          {mismatchedFiles.length > 0 && (
            <div className="mb-4 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {mismatchedFiles.length} dosya sayfa sayısı bakımından bu şablonla uyumsuz. Devam etmek için
              onları listeden çıkarın.
            </div>
          )}

          <div className="mt-2 flex gap-2">
            <Button variant="outline" onClick={reset}>
              Geri
            </Button>
            <Button
              className="flex-1"
              onClick={() => uploadMutation.mutate()}
              disabled={
                pendingFiles.length === 0 ||
                !selectedTemplateId ||
                mismatchedFiles.length > 0 ||
                uploadMutation.isPending
              }
            >
              {uploadMutation.isPending ? "Yükleniyor..." : `${pendingFiles.length} dosyayı yükle`}
            </Button>
          </div>
        </div>
      )}

      {step === "processing" && (
        <div className="flex h-64 flex-col items-center justify-center gap-2">
          <p className="text-sm text-muted-foreground">Belgeler yükleniyor...</p>
        </div>
      )}

      {step === "done" && expandedJobIndex === null && (
        <div>
          <div className="flex flex-col gap-2">
            {jobs.map((job, i) => (
              <button
                key={i}
                type="button"
                onClick={() => job.status === "PENDING_REVIEW" && setExpandedJobIndex(i)}
                className={cn(
                  "flex items-center justify-between rounded-md border px-3 py-2 text-left text-sm",
                  job.status === "PENDING_REVIEW" && "hover:bg-accent"
                )}
              >
                <span className="truncate">{job.file.name}</span>
                <span
                  className={cn(
                    "shrink-0 rounded-full px-2 py-0.5 text-xs font-medium",
                    job.status === "VALIDATED" && "bg-emerald-500/10 text-emerald-600",
                    job.status === "PENDING_REVIEW" && "bg-orange-500/10 text-orange-600",
                    (job.status === "REJECTED_EMPTY" || job.status === "REJECTED_INVALID" || job.status === "ERROR") &&
                      "bg-destructive/10 text-destructive",
                    job.status === "PROCESSING" && "bg-muted text-muted-foreground"
                  )}
                >
                  {STATUS_LABELS[job.status] ?? job.status}
                </span>
              </button>
            ))}
          </div>
          <Button className="mt-4" onClick={reset}>
            Yeni belge yükle
          </Button>
        </div>
      )}

      {step === "done" && expandedJob && (
        <div>
          <Button variant="outline" size="sm" className="mb-3" onClick={() => setExpandedJobIndex(null)}>
            ← Listeye dön
          </Button>

          {currentPendingSegment ? (
            <div>
              <p className="mb-1 text-sm text-orange-600">
                {expandedJob.file.name} — kalan: {expandedPending.length} segment
              </p>
              <h2 className="mb-2 text-lg font-semibold">{currentPendingSegment.label}</h2>

              {currentTemplateSegment && currentTemplateSegment.rules.length > 0 && (
                <p className="mb-3 text-sm text-muted-foreground">
                  Beklenen:{" "}
                  {currentTemplateSegment.rules.map((r) => RULE_LABELS[r.type] ?? r.type).join(", ")}
                </p>
              )}

              {segmentImageUrl && (
                <img
                  src={segmentImageUrl}
                  alt={currentPendingSegment.label}
                  className="mb-4 w-full rounded-md border"
                />
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
          ) : (
            <div className="flex flex-col items-center justify-center gap-4 py-8">
              <p className="text-lg font-medium">
                {STATUS_LABELS[expandedJob.status] ?? expandedJob.status}
              </p>
              <Button
                variant="outline"
                render={<Link to={`/documents/${expandedJob.documentId}`}>Belgeyi görüntüle</Link>}
              />
            </div>
          )}
        </div>
      )}
    </div>
  )
}

export default Upload