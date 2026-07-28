import { useEffect, useRef, useState } from "react"
import axios from "axios"
import { useMutation, useQuery } from "@tanstack/react-query"
import { api } from "@/lib/api"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import belgeYukleTitle from "@/assets/belge-yukle-title.png"
import { Link } from "react-router"

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

interface DocumentStatusData {
  id: number
  fileName: string
  status: "PROCESSING" | "PENDING_REVIEW" | "VALIDATED" | "REJECTED_EMPTY" | "REJECTED_INVALID"
}

const STATUS_LABELS: Record<string, string> = {
  PENDING_REVIEW: "İnceleme bekliyor",
  VALIDATED: "Onaylandı",
  REJECTED_EMPTY: "Boş belge olarak reddedildi",
  REJECTED_INVALID: "Geçersiz olarak reddedildi",
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

function Upload() {
  const [step, setStep] = useState<Step>("file")
  const [file, setFile] = useState<File | null>(null)
  const [selectedTemplateId, setSelectedTemplateId] = useState<number | null>(null)
  const [language, setLanguage] = useState<"tur" | "eng">("tur")
  const [documentId, setDocumentId] = useState<number | null>(null)
  const [statusData, setStatusData] = useState<DocumentStatusData | null>(null)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const [isDragging, setIsDragging] = useState(false)
  const fileInputRef = useRef<HTMLInputElement | null>(null)

  const { data: templates, isLoading: templatesLoading } = useQuery({
    queryKey: ["templates-for-upload"],
    queryFn: async () => {
      const res = await api.get<PagedResponse<TemplateSummary>>("/api/templates?page=0&size=50")
      return res.data
    },
    enabled: step === "template",
  })

  const uploadMutation = useMutation({
    mutationFn: async () => {
      if (!file || !selectedTemplateId) throw new Error("Eksik bilgi")
      const formData = new FormData()
      formData.append("file", file)
      const res = await api.post<{ id: number; status: string }>(
        `/api/documents/upload?templateId=${selectedTemplateId}&lang=${language}`,
        formData
      )
      return res.data
    },
    onSuccess: (data) => {
      setDocumentId(data.id)
      setStatusData({ id: data.id, fileName: file?.name ?? "", status: "PROCESSING" })
      setStep("processing")
    },
    onError: (error: unknown) => {
      if (axios.isAxiosError(error) && error.response?.data?.message) {
        setUploadError(error.response.data.message)
      } else {
        setUploadError("Yükleme başarısız oldu.")
      }
    },
  })

  useEffect(() => {
    if (!documentId || !statusData || statusData.status !== "PROCESSING") return
    const timer = setTimeout(async () => {
      const res = await api.get<DocumentStatusData>(`/api/documents/${documentId}`)
      setStatusData(res.data)
      if (res.data.status !== "PROCESSING") {
        setStep("done")
      }
    }, 1000)
    return () => clearTimeout(timer)
  }, [documentId, statusData])

  function processFile(selected: File) {
    if (!ACCEPTED_TYPES.includes(selected.type)) {
      setUploadError("Sadece PDF, PNG veya JPEG dosyaları desteklenir.")
      return
    }
    setUploadError(null)
    setFile(selected)
    setStep("template")
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const selected = e.target.files?.[0]
    if (!selected) return
    processFile(selected)
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
    const dropped = e.dataTransfer.files?.[0]
    if (!dropped) return
    processFile(dropped)
  }

  function handleUpload() {
    setUploadError(null)
    uploadMutation.mutate()
  }

  function reset() {
    setStep("file")
    setFile(null)
    setSelectedTemplateId(null)
    setLanguage("tur")
    setDocumentId(null)
    setStatusData(null)
    setUploadError(null)
  }

  return (
    <div className="max-w-lg">
      <img src={belgeYukleTitle} alt="Belge yükle" className="mb-6 h-7.5 w-auto" />

      <StepIndicator currentStep={stepToNumber(step)} isComplete={step === "done"} />

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
            {isDragging ? "Bırak..." : "PDF, PNG veya JPEG — dosyayı sürükleyin ya da seçin"}
          </p>
          <Button onClick={() => fileInputRef.current?.click()}>Dosya seç</Button>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/png,image/jpeg,application/pdf"
            className="hidden"
            onChange={handleFileChange}
          />
        </div>
      )}

      {step === "template" && (
        <div>
          <p className="mb-2 text-sm text-muted-foreground">
            Seçilen dosya: <span className="font-medium text-foreground">{file?.name}</span>
          </p>

          {uploadError && (
            <div className="mb-3 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {uploadError}
            </div>
          )}

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

          {templates && templates.content.length === 0 && (
            <p className="text-sm text-muted-foreground">Henüz kullanılabilir bir şablon yok.</p>
          )}

          {templates && templates.content.length > 0 && (
            <div className="mb-4 grid grid-cols-2 gap-2">
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

          <div className="flex gap-2">
            <Button variant="outline" onClick={reset}>
              Geri
            </Button>
            <Button
              className="flex-1"
              onClick={handleUpload}
              disabled={!selectedTemplateId || uploadMutation.isPending}
            >
              {uploadMutation.isPending ? "Yükleniyor..." : "Yükle"}
            </Button>
          </div>
        </div>
      )}

      {step === "processing" && (
        <div className="flex h-64 flex-col items-center justify-center gap-2">
          <p className="text-sm text-muted-foreground">Belge işleniyor...</p>
        </div>
      )}

      {step === "done" && statusData && (
        <div className="flex h-64 flex-col items-center justify-center gap-4">
          <p className="text-lg font-medium">{STATUS_LABELS[statusData.status] ?? statusData.status}</p>
          <div className="flex gap-2">
            <Button variant="outline" render={<Link to={`/documents/${statusData.id}`}>Belgeyi görüntüle</Link>} />
            <Button onClick={reset}>Yeni belge yükle</Button>
          </div>
        </div>
      )}
    </div>
  )
}

export default Upload