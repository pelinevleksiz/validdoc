import { useRef, useState } from "react"
import { Stage, Layer, Image as KonvaImage, Rect, Text } from "react-konva"
import * as pdfjsLib from "pdfjs-dist"
import pdfjsWorker from "pdfjs-dist/build/pdf.worker.min.mjs?url"
import { useMutation, useQuery } from "@tanstack/react-query"
import axios from "axios"
import { useTranslation } from "react-i18next"
import { api } from "@/lib/api"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Field, FieldLabel } from "@/components/ui/field"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

pdfjsLib.GlobalWorkerOptions.workerSrc = pdfjsWorker

const A4_WIDTH_PX = 2480.3149606299213
const A4_HEIGHT_PX = 3507.874015748031
const DISPLAY_WIDTH = 495
const TOOLBAR_MAX_WIDTH = DISPLAY_WIDTH + 16 + 256

type CanvasSource = HTMLImageElement | HTMLCanvasElement

interface DraftRule {
  type: string
  param?: number
}

interface DraftSegment {
  id: string
  label: string
  page: number
  x: number
  y: number
  w: number
  h: number
  rules: DraftRule[]
}

interface RuleTypeOption {
  type: string
  requiresParam: boolean
  inkRule: boolean
}

interface Point {
  x: number
  y: number
}

interface PreviewResult {
  label: string
  page: number
  rawText: string
  inkDensity: number
}

function TemplateNew() {
  const { t } = useTranslation()
  const [templateName, setTemplateName] = useState("")
  const [fileKind, setFileKind] = useState<"image" | "pdf" | null>(null)
  const [source, setSource] = useState<CanvasSource | null>(null)
  const [zoom, setZoom] = useState(1)
  const [stagePos, setStagePos] = useState({ x: 0, y: 0 })
  const [pdfDoc, setPdfDoc] = useState<any>(null)
  const [pdfPageNumber, setPdfPageNumber] = useState(1)
  const [pdfPageCount, setPdfPageCount] = useState(1)
  const [isDragging, setIsDragging] = useState(false)
  const fileInputRef = useRef<HTMLInputElement | null>(null)

  const [segments, setSegments] = useState<DraftSegment[]>([])
  const [isDrawingMode, setIsDrawingMode] = useState(false)
  const [drawStart, setDrawStart] = useState<Point | null>(null)
  const [drawCurrent, setDrawCurrent] = useState<Point | null>(null)
  const [pendingSegment, setPendingSegment] = useState<{ x: number; y: number; w: number; h: number; page: number } | null>(null)
  const [pendingLabel, setPendingLabel] = useState("")
  const [drawError, setDrawError] = useState<string | null>(null)

  const [ruleEditSegmentId, setRuleEditSegmentId] = useState<string | null>(null)
  const [newRuleType, setNewRuleType] = useState<string | null>(null)
  const [newRuleParam, setNewRuleParam] = useState("")

  const [previewResults, setPreviewResults] = useState<PreviewResult[] | null>(null)
  const [previewError, setPreviewError] = useState<string | null>(null)
  const [saveError, setSaveError] = useState<string | null>(null)

  const { data: ruleTypes } = useQuery({
    queryKey: ["rule-types"],
    queryFn: async () => {
      const res = await api.get<RuleTypeOption[]>("/api/templates/rule-types")
      return res.data
    },
  })

  const baseScale = DISPLAY_WIDTH / A4_WIDTH_PX
  const displayHeight = A4_HEIGHT_PX * baseScale
  const currentPage = fileKind === "pdf" ? pdfPageNumber : 1
  const effectiveScale = baseScale * zoom

  function ruleLabel(type: string) {
    return t(`rules.${type}`, type)
  }

  function rectsOverlap(
    a: { x: number; y: number; w: number; h: number },
    b: { x: number; y: number; w: number; h: number }
  ) {
    return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y
  }

  function clampPos(pos: Point, currentZoom: number) {
    const imageWidth = DISPLAY_WIDTH * currentZoom
    const imageHeight = displayHeight * currentZoom
    const minX = Math.min(0, DISPLAY_WIDTH - imageWidth)
    const minY = Math.min(0, displayHeight - imageHeight)
    return {
      x: Math.min(0, Math.max(minX, pos.x)),
      y: Math.min(0, Math.max(minY, pos.y)),
    }
  }

  async function renderPdfPage(doc: any, pageNumber: number) {
    const page = await doc.getPage(pageNumber)
    const unscaledViewport = page.getViewport({ scale: 1 })
    const renderScale = A4_WIDTH_PX / unscaledViewport.width
    const viewport = page.getViewport({ scale: renderScale })

    const canvas = document.createElement("canvas")
    canvas.width = viewport.width
    canvas.height = viewport.height
    const context = canvas.getContext("2d")
    if (!context) return

    await page.render({ canvasContext: context, viewport }).promise
    setSource(canvas)
  }

  async function processFile(file: File) {
    if (file.type === "application/pdf") {
      setFileKind("pdf")
      const arrayBuffer = await file.arrayBuffer()
      const loadingTask = pdfjsLib.getDocument({ data: arrayBuffer })
      const doc = await loadingTask.promise
      setPdfDoc(doc)
      setPdfPageCount(doc.numPages)
      setPdfPageNumber(1)
      await renderPdfPage(doc, 1)
      return
    }

    setFileKind("image")
    setPdfDoc(null)
    const reader = new FileReader()
    reader.onload = () => {
      const img = new window.Image()
      img.onload = () => setSource(img)
      img.src = reader.result as string
    }
    reader.readAsDataURL(file)
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (!file) return
    processFile(file)
  }

  function goToPdfPage(pageNumber: number) {
    if (!pdfDoc || pageNumber < 1 || pageNumber > pdfPageCount) return
    setPdfPageNumber(pageNumber)
    setStagePos({ x: 0, y: 0 })
    renderPdfPage(pdfDoc, pageNumber)
  }

  function resetUpload() {
    setSource(null)
    setFileKind(null)
    setPdfDoc(null)
    setPdfPageNumber(1)
    setPdfPageCount(1)
    setZoom(1)
    setStagePos({ x: 0, y: 0 })
    setSegments([])
    setIsDrawingMode(false)
  }

  function zoomIn() {
    setZoom((z) => {
      const next = Math.min(z + 0.25, 3)
      setStagePos((pos) => clampPos(pos, next))
      return next
    })
  }

  function zoomOut() {
    setZoom((z) => {
      const next = Math.max(z - 0.25, 0.5)
      setStagePos((pos) => clampPos(pos, next))
      return next
    })
  }

  function handleStageMouseDown(e: any) {
    if (!isDrawingMode) return
    const stage = e.target.getStage()
    const pos = stage.getRelativePointerPosition()
    setDrawStart(pos)
    setDrawCurrent(pos)
  }

  function handleStageMouseMove(e: any) {
    if (!isDrawingMode || !drawStart) return
    const stage = e.target.getStage()
    const pos = stage.getRelativePointerPosition()
    setDrawCurrent(pos)
  }

  function handleStageMouseUp() {
    if (!isDrawingMode || !drawStart || !drawCurrent) return

    const x = Math.min(drawStart.x, drawCurrent.x)
    const y = Math.min(drawStart.y, drawCurrent.y)
    const w = Math.abs(drawCurrent.x - drawStart.x)
    const h = Math.abs(drawCurrent.y - drawStart.y)

    setDrawStart(null)
    setDrawCurrent(null)
    setIsDrawingMode(false)

    const minSize = 8 / baseScale
    if (w < minSize || h < minSize) return

    const newRect = { x, y, w, h }
    const overlapsExisting = segments
      .filter((s) => s.page === currentPage)
      .some((s) => rectsOverlap(newRect, s))

    if (overlapsExisting) {
      setDrawError(t("templateNew.overlapError"))
      return
    }

    setDrawError(null)
    setPendingSegment({ x, y, w, h, page: currentPage })
  }

  function openRuleEditor(segmentId: string) {
    setRuleEditSegmentId(segmentId)
    setNewRuleType(null)
    setNewRuleParam("")
  }

  function confirmPendingSegment() {
    if (!pendingSegment || !pendingLabel.trim()) return
    const newId = crypto.randomUUID()
    setSegments((prev) => [
      ...prev,
      {
        id: newId,
        label: pendingLabel.trim().slice(0, 30),
        page: pendingSegment.page,
        x: pendingSegment.x,
        y: pendingSegment.y,
        w: pendingSegment.w,
        h: pendingSegment.h,
        rules: [],
      },
    ])
    setPendingSegment(null)
    setPendingLabel("")
    openRuleEditor(newId)
  }

  function cancelPendingSegment() {
    setPendingSegment(null)
    setPendingLabel("")
  }

  function removeSegment(id: string) {
    setSegments((prev) => prev.filter((s) => s.id !== id))
  }

  function closeRuleEditor() {
    setRuleEditSegmentId(null)
    setNewRuleType(null)
    setNewRuleParam("")
  }

  const ruleEditSegment = segments.find((s) => s.id === ruleEditSegmentId) ?? null
  const selectedRuleInfo = ruleTypes?.find((rt) => rt.type === newRuleType)
  const hasInkRule =
    ruleEditSegment?.rules.some((r) => ruleTypes?.find((rt) => rt.type === r.type)?.inkRule) ?? false
  const hasAnyRule = (ruleEditSegment?.rules.length ?? 0) > 0
  const usedTypes = new Set(ruleEditSegment?.rules.map((r) => r.type) ?? [])
  const availableRuleTypes = (ruleTypes ?? []).filter((rt) => {
    if (usedTypes.has(rt.type)) return false
    if (hasInkRule) return false
    if (rt.inkRule && hasAnyRule) return false
    return true
  })

  function addRule() {
    if (!ruleEditSegment || !newRuleType || !selectedRuleInfo) return
    if (selectedRuleInfo.requiresParam && !newRuleParam) return

    setSegments((prev) =>
      prev.map((s) =>
        s.id === ruleEditSegment.id
          ? {
              ...s,
              rules: [
                ...s.rules,
                {
                  type: newRuleType,
                  param: selectedRuleInfo.requiresParam ? Number(newRuleParam) : undefined,
                },
              ],
            }
          : s
      )
    )
    setNewRuleType(null)
    setNewRuleParam("")
  }

  function removeRule(segmentId: string, index: number) {
    setSegments((prev) =>
      prev.map((s) => (s.id === segmentId ? { ...s, rules: s.rules.filter((_, i) => i !== index) } : s))
    )
  }

  function getCurrentSourceBlob(): Promise<Blob | null> {
    return new Promise((resolve) => {
      if (!source) {
        resolve(null)
        return
      }
      if (source instanceof HTMLCanvasElement) {
        source.toBlob((blob) => resolve(blob), "image/png")
        return
      }
      const canvas = document.createElement("canvas")
      canvas.width = source.naturalWidth
      canvas.height = source.naturalHeight
      const ctx = canvas.getContext("2d")
      if (!ctx) {
        resolve(null)
        return
      }
      ctx.drawImage(source, 0, 0)
      canvas.toBlob((blob) => resolve(blob), "image/png")
    })
  }

  const previewMutation = useMutation({
    mutationFn: async () => {
      const blob = await getCurrentSourceBlob()
      if (!blob) throw new Error("Görsel hazır değil")

      const formData = new FormData()
      formData.append("file", blob, "preview.png")
      formData.append(
        "segments",
        JSON.stringify(
          visibleSegments.map((s) => ({
            label: s.label,
            page: s.page,
            x: s.x,
            y: s.y,
            w: s.w,
            h: s.h,
          }))
        )
      )

      const res = await api.post<{ segments: PreviewResult[] }>("/api/templates/preview", formData)
      return res.data.segments
    },
    onSuccess: (data) => {
      setPreviewResults(data)
      setPreviewError(null)
    },
    onError: (error: unknown) => {
      setPreviewResults(null)
      const code = axios.isAxiosError(error) ? error.response?.data?.code : null
      setPreviewError(code ? t(`errors.${code}`, t("errors.GENERIC")) : t("errors.GENERIC"))
    },
  })

  const saveMutation = useMutation({
    mutationFn: async () => {
      const res = await api.post("/api/templates", {
        name: templateName.trim(),
        pageCount: fileKind === "pdf" ? pdfPageCount : 1,
        segments: segments.map((s) => ({
          label: s.label,
          page: s.page,
          x: s.x,
          y: s.y,
          w: s.w,
          h: s.h,
          rules: s.rules.map((r) => ({ type: r.type, param: r.param })),
        })),
      })
      return res.data
    },
    onSuccess: () => {
      window.location.href = "/templates"
    },
    onError: (error: unknown) => {
      const code = axios.isAxiosError(error) ? error.response?.data?.code : null
      setSaveError(code ? t(`errors.${code}`, t("errors.GENERIC")) : t("errors.GENERIC"))
    },
  })

  const hasSegmentWithoutRules = segments.some((s) => s.rules.length === 0)
  const canSave = templateName.trim().length > 0 && segments.length > 0 && !hasSegmentWithoutRules

  const visibleSegments = segments.filter((s) => s.page === currentPage)
  const previewRect =
    drawStart && drawCurrent
      ? {
          x: Math.min(drawStart.x, drawCurrent.x),
          y: Math.min(drawStart.y, drawCurrent.y),
          w: Math.abs(drawCurrent.x - drawStart.x),
          h: Math.abs(drawCurrent.y - drawStart.y),
        }
      : null

  return (
    <div>
      <h1 className="font-amarego lowercase mb-4 text-3xl">{t("templateNew.title")}</h1>

      <Field className="mb-4 max-w-sm">
        <FieldLabel htmlFor="template-name">{t("templateNew.nameLabel")}</FieldLabel>
        <Input
          id="template-name"
          value={templateName}
          onChange={(e) => setTemplateName(e.target.value)}
          placeholder={t("templateNew.namePlaceholder")}
        />
      </Field>

      {source && (
        <div className="mx-auto mb-2 w-full md:mx-0" style={{ maxWidth: TOOLBAR_MAX_WIDTH }}>
          <div className="flex flex-wrap items-center gap-2">
            <Button variant="outline" size="sm" onClick={zoomOut}>-</Button>
            <span className="text-sm text-muted-foreground">{Math.round(zoom * 100)}%</span>
            <Button variant="outline" size="sm" onClick={zoomIn}>+</Button>

            {fileKind === "pdf" && pdfPageCount > 1 && (
              <>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => goToPdfPage(pdfPageNumber - 1)}
                  disabled={pdfPageNumber <= 1}
                >
                  {t("templateNew.previousPage")}
                </Button>
                <span className="text-sm text-muted-foreground">
                  {t("templateNew.pageOf", { current: pdfPageNumber, total: pdfPageCount })}
                </span>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => goToPdfPage(pdfPageNumber + 1)}
                  disabled={pdfPageNumber >= pdfPageCount}
                >
                  {t("templateNew.nextPage")}
                </Button>
              </>
            )}

            <div className="ml-auto flex items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => previewMutation.mutate()}
                disabled={visibleSegments.length === 0 || previewMutation.isPending}
              >
                {previewMutation.isPending ? t("templateNew.previewing") : t("templateNew.preview")}
              </Button>
              <Button
                size="sm"
                onClick={resetUpload}
                className="bg-black text-white hover:bg-black/90"
              >
                {t("templateNew.changeDocument")}
              </Button>
            </div>
          </div>
        </div>
      )}

      <div className="flex flex-col items-center gap-4 md:flex-row md:items-start">
        <div className="mx-auto overflow-x-auto md:mx-0" style={{ width: DISPLAY_WIDTH }}>
          {!source && (
            <div
              onDragOver={(e) => {
                e.preventDefault()
                setIsDragging(true)
              }}
              onDragLeave={(e) => {
                e.preventDefault()
                setIsDragging(false)
              }}
              onDrop={(e) => {
                e.preventDefault()
                setIsDragging(false)
                const dropped = e.dataTransfer.files?.[0]
                if (dropped) processFile(dropped)
              }}
              className={`flex flex-col items-center justify-center gap-3 rounded-md border border-dashed transition-colors ${
                isDragging ? "border-primary bg-primary/5" : ""
              }`}
              style={{ height: displayHeight }}
            >
              <p className="text-sm text-muted-foreground">
                {isDragging ? t("templateNew.dropzoneDragging") : t("templateNew.dropzone")}
              </p>
              <Button onClick={() => fileInputRef.current?.click()}>{t("templateNew.selectFile")}</Button>
              <input
                ref={fileInputRef}
                type="file"
                accept="image/png,image/jpeg,application/pdf"
                className="hidden"
                onChange={handleFileChange}
              />
            </div>
          )}

          {source && (
            <div
              className="overflow-hidden rounded-md border bg-muted/30"
              style={{
                width: DISPLAY_WIDTH,
                height: displayHeight,
                cursor: isDrawingMode ? "crosshair" : "grab",
              }}
            >
              <Stage
                width={DISPLAY_WIDTH}
                height={displayHeight}
                scaleX={effectiveScale}
                scaleY={effectiveScale}
                x={stagePos.x}
                y={stagePos.y}
                draggable={!isDrawingMode}
                dragBoundFunc={(pos) => clampPos(pos, zoom)}
                onDragMove={(e) => setStagePos({ x: e.target.x(), y: e.target.y() })}
                onMouseDown={handleStageMouseDown}
                onMouseMove={handleStageMouseMove}
                onMouseUp={handleStageMouseUp}
              >
                <Layer>
                  {source && (
                    <KonvaImage image={source} width={A4_WIDTH_PX} height={A4_HEIGHT_PX} />
                  )}

                  {visibleSegments.map((seg) => (
                    <Rect
                      key={seg.id}
                      x={seg.x}
                      y={seg.y}
                      width={seg.w}
                      height={seg.h}
                      fill={seg.rules.length === 0 ? "rgba(234, 88, 12, 0.15)" : "rgba(37, 99, 235, 0.15)"}
                      stroke={seg.rules.length === 0 ? "#ea580c" : "#2563eb"}
                      strokeWidth={1.5 / effectiveScale}
                    />
                  ))}
                  {visibleSegments.map((seg) => (
                    <Text
                      key={`${seg.id}-label`}
                      x={seg.x + 4 / effectiveScale}
                      y={seg.y + 2 / effectiveScale}
                      text={seg.label}
                      fontSize={11 / effectiveScale}
                      fill={seg.rules.length === 0 ? "#ea580c" : "#2563eb"}
                    />
                  ))}

                  {previewRect && (
                    <Rect
                      x={previewRect.x}
                      y={previewRect.y}
                      width={previewRect.w}
                      height={previewRect.h}
                      fill="rgba(37, 99, 235, 0.15)"
                      stroke="#2563eb"
                      strokeWidth={1.5 / effectiveScale}
                      dash={[4 / effectiveScale, 4 / effectiveScale]}
                    />
                  )}
                </Layer>
              </Stage>
            </div>
          )}
        </div>

        <div
          className="flex w-full flex-col overflow-y-auto rounded-md border p-3 md:w-64 md:shrink-0"
          style={{ height: displayHeight }}
        >
          <div className="flex flex-col gap-2">
            {segments.length === 0 && (
              <p className="text-sm text-muted-foreground">{t("templateNew.noSegments")}</p>
            )}
            {segments.map((seg) => (
              <div
                key={seg.id}
                className="flex items-center justify-between gap-2 rounded-md border px-2 py-1.5"
              >
                <button
                  type="button"
                  onClick={() => openRuleEditor(seg.id)}
                  className="min-w-0 flex-1 text-left"
                >
                  <p className="truncate text-sm">{seg.label}</p>
                  <p
                    className={
                      seg.rules.length === 0
                        ? "text-xs text-orange-600"
                        : "text-xs text-muted-foreground"
                    }
                  >
                    {t("templateNew.page", { page: seg.page })} ·{" "}
                    {seg.rules.length === 0
                      ? t("templateNew.noRules")
                      : t("templateNew.ruleCount", { count: seg.rules.length })}
                  </p>
                </button>
                <button
                  type="button"
                  onClick={() => removeSegment(seg.id)}
                  className="shrink-0 text-muted-foreground hover:text-destructive"
                >
                  ×
                </button>
              </div>
            ))}
          </div>

          {drawError && (
            <div className="mb-2 mt-3 rounded-md border border-destructive/50 bg-destructive/10 px-3 py-2 text-xs text-destructive">
              {drawError}
            </div>
          )}

          <Button
            variant="outline"
            size="sm"
            className="mt-3 w-full"
            onClick={() => {
              setDrawError(null)
              setIsDrawingMode(true)
            }}
            disabled={!source || isDrawingMode}
          >
            {isDrawingMode ? t("templateNew.drawing") : t("templateNew.addSegment")}
          </Button>
        </div>
      </div>

      <div className="mx-auto mt-4 w-full md:mx-0" style={{ maxWidth: TOOLBAR_MAX_WIDTH }}>
        {saveError && (
          <div className="mb-2 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {saveError}
          </div>
        )}
        {!canSave && segments.length > 0 && (
          <p className="mb-2 text-xs text-muted-foreground">
            {hasSegmentWithoutRules ? t("templateNew.needsRuleWarning") : t("templateNew.needsNameWarning")}
          </p>
        )}
        <Button
          className="w-full"
          onClick={() => saveMutation.mutate()}
          disabled={!canSave || saveMutation.isPending}
        >
          {saveMutation.isPending ? t("templateNew.saving") : t("templateNew.saveButton")}
        </Button>
      </div>

      <Dialog open={pendingSegment !== null} onOpenChange={(open) => !open && cancelPendingSegment()}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("templateNew.nameSegmentTitle")}</DialogTitle>
          </DialogHeader>
          <Field>
            <FieldLabel htmlFor="segment-label">{t("templateNew.segmentNameLabel")}</FieldLabel>
            <Input
              id="segment-label"
              value={pendingLabel}
              onChange={(e) => setPendingLabel(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault()
                  confirmPendingSegment()
                }
              }}
              maxLength={30}
              placeholder={t("templateNew.segmentNamePlaceholder")}
              autoFocus
            />
          </Field>
          <DialogFooter>
            <Button variant="outline" onClick={cancelPendingSegment}>
              {t("common.cancel")}
            </Button>
            <Button onClick={confirmPendingSegment} disabled={!pendingLabel.trim()}>
              {t("templateNew.add")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={ruleEditSegmentId !== null} onOpenChange={(open) => !open && closeRuleEditor()}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("templateNew.rulesDialogTitle", { label: ruleEditSegment?.label })}</DialogTitle>
          </DialogHeader>

          {ruleEditSegment && ruleEditSegment.rules.length > 0 && (
            <div className="flex flex-col gap-2">
              {ruleEditSegment.rules.map((rule, index) => (
                <div
                  key={index}
                  className="flex items-center justify-between rounded-md border px-2 py-1.5 text-sm"
                >
                  <span>
                    {ruleLabel(rule.type)}
                    {rule.param !== undefined && ` (${rule.param})`}
                  </span>
                  <button
                    type="button"
                    onClick={() => removeRule(ruleEditSegment.id, index)}
                    className="text-muted-foreground hover:text-destructive"
                  >
                    ×
                  </button>
                </div>
              ))}
            </div>
          )}

          {availableRuleTypes.length > 0 ? (
            <div className="flex flex-col gap-2 border-t pt-3">
              <Field>
                <FieldLabel htmlFor="new-rule-type">{t("templateNew.addRule")}</FieldLabel>
                <Select value={newRuleType} onValueChange={setNewRuleType}>
                  <SelectTrigger id="new-rule-type">
                    <SelectValue placeholder={t("templateNew.selectRule")}>
                      {(value: string) => (value ? ruleLabel(value) : null)}
                    </SelectValue>
                  </SelectTrigger>
                  <SelectContent>
                    {availableRuleTypes.map((rt) => (
                      <SelectItem key={rt.type} value={rt.type}>
                        {ruleLabel(rt.type)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>

              {selectedRuleInfo?.requiresParam && (
                <Field>
                  <FieldLabel htmlFor="new-rule-param">{t("templateNew.value")}</FieldLabel>
                  <Input
                    id="new-rule-param"
                    type="number"
                    min={1}
                    value={newRuleParam}
                    onChange={(e) => setNewRuleParam(e.target.value)}
                  />
                </Field>
              )}

              <Button
                size="sm"
                onClick={addRule}
                disabled={!newRuleType || (selectedRuleInfo?.requiresParam && !newRuleParam)}
              >
                {t("templateNew.add")}
              </Button>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">
              {hasInkRule ? t("templateNew.inkRuleExclusive") : t("templateNew.allRulesAdded")}
            </p>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={closeRuleEditor}>
              {t("templateNew.close")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={previewResults !== null} onOpenChange={(open) => !open && setPreviewResults(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("templateNew.previewResultsTitle")}</DialogTitle>
          </DialogHeader>
          <div className="flex flex-col gap-2">
            {previewResults?.map((r, i) => (
              <div key={i} className="rounded-md border px-3 py-2">
                <p className="text-sm font-medium">{r.label}</p>
                <p className="text-xs text-muted-foreground">
                  {t("templateNew.readText")}: {r.rawText ? `"${r.rawText}"` : t("templateNew.empty")}
                </p>
                <p className="text-xs text-muted-foreground">
                  {t("templateNew.inkDensity")}: {(r.inkDensity * 100).toFixed(1)}%
                </p>
              </div>
            ))}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPreviewResults(null)}>
              {t("templateNew.close")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <Dialog open={previewError !== null} onOpenChange={(open) => !open && setPreviewError(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{t("templateNew.previewFailedTitle")}</DialogTitle>
          </DialogHeader>
          <div className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {previewError}
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setPreviewError(null)}>
              {t("templateNew.close")}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

export default TemplateNew