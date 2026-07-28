import { useState } from "react"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import axios from "axios"
import { api } from "@/lib/api"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
} from "@/components/ui/card"
import {
  Field,
  FieldError,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field"
import settingsTitle from "@/assets/dogrulama-ayarlari-title.png"

interface ValidationSettingsData {
  retentionDays: number
  inkDensityThreshold: number
  ocrConfidenceThreshold: number
  updatedAt: string
  updatedBy: string
}

const settingsSchema = z.object({
  retentionDays: z
    .string()
    .min(1, "Zorunlu")
    .refine((v) => Number.isInteger(Number(v)) && Number(v) > 0, "Pozitif bir tam sayı olmalı"),
  inkDensityThreshold: z
    .string()
    .min(1, "Zorunlu")
    .refine((v) => !Number.isNaN(Number(v)) && Number(v) >= 0 && Number(v) <= 1, "0 ile 1 arasında olmalı"),
  ocrConfidenceThreshold: z
    .string()
    .min(1, "Zorunlu")
    .refine((v) => !Number.isNaN(Number(v)) && Number(v) >= 0 && Number(v) <= 100, "0 ile 100 arasında olmalı"),
})

type SettingsFormValues = z.infer<typeof settingsSchema>

function Settings() {
  const queryClient = useQueryClient()
  const [serverError, setServerError] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  const { data, isLoading } = useQuery({
    queryKey: ["validation-settings"],
    queryFn: async () => {
      const res = await api.get<ValidationSettingsData>("/api/admin/validation-settings")
      return res.data
    },
  })

  const form = useForm<SettingsFormValues>({
    resolver: zodResolver(settingsSchema),
    values: data
      ? {
          retentionDays: String(data.retentionDays),
          inkDensityThreshold: String(data.inkDensityThreshold),
          ocrConfidenceThreshold: String(data.ocrConfidenceThreshold),
        }
      : undefined,
  })

  const updateMutation = useMutation({
    mutationFn: async (values: SettingsFormValues) => {
      const res = await api.put<ValidationSettingsData>("/api/admin/validation-settings", {
        retentionDays: Number(values.retentionDays),
        inkDensityThreshold: Number(values.inkDensityThreshold),
        ocrConfidenceThreshold: Number(values.ocrConfidenceThreshold),
      })
      return res.data
    },
    onSuccess: (updated) => {
      queryClient.setQueryData(["validation-settings"], updated)
      setSuccess(true)
      setServerError(null)
    },
    onError: (error: unknown) => {
      setSuccess(false)
      if (axios.isAxiosError(error) && error.response?.data?.message) {
        setServerError(error.response.data.message)
      } else {
        setServerError("Ayarlar kaydedilemedi.")
      }
    },
  })

  function onSubmit(values: SettingsFormValues) {
    setServerError(null)
    setSuccess(false)
    updateMutation.mutate(values)
  }

  return (
    <div>
      {isLoading && <p className="text-muted-foreground">Yükleniyor...</p>}

      {data && (
        <Card className="max-w-sm">
          <CardHeader>
            <img src={settingsTitle} alt="Doğrulama ayarları" className="mb-1 h-8.5 w-auto" />
            <CardDescription>
              Son güncelleme: {data.updatedBy} — {new Date(data.updatedAt).toLocaleString("tr-TR")}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {serverError && (
              <div className="mb-4 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
                {serverError}
              </div>
            )}
            {success && (
              <div className="mb-4 rounded-md bg-emerald-500/10 px-3 py-2 text-sm text-emerald-600">
                Ayarlar güncellendi.
              </div>
            )}

            <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
              <FieldGroup>
                <Controller
                  name="retentionDays"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <Field data-invalid={fieldState.invalid}>
                      <FieldLabel htmlFor="retention-days">Saklama süresi (gün)</FieldLabel>
                      <Input {...field} id="retention-days" type="number" min={1} />
                      {fieldState.invalid && <FieldError errors={[fieldState.error]} />}
                    </Field>
                  )}
                />

                <Controller
                  name="inkDensityThreshold"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <Field data-invalid={fieldState.invalid}>
                      <FieldLabel htmlFor="ink-density">Mürekkep yoğunluğu eşiği (0-1)</FieldLabel>
                      <Input {...field} id="ink-density" type="number" step="0.01" min={0} max={1} />
                      {fieldState.invalid && <FieldError errors={[fieldState.error]} />}
                    </Field>
                  )}
                />

                <Controller
                  name="ocrConfidenceThreshold"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <Field data-invalid={fieldState.invalid}>
                      <FieldLabel htmlFor="ocr-confidence">OCR güven eşiği (0-100)</FieldLabel>
                      <Input {...field} id="ocr-confidence" type="number" step="0.1" min={0} max={100} />
                      {fieldState.invalid && <FieldError errors={[fieldState.error]} />}
                    </Field>
                  )}
                />

                <Button type="submit" className="w-full" disabled={updateMutation.isPending}>
                  {updateMutation.isPending ? "Kaydediliyor..." : "Kaydet"}
                </Button>
              </FieldGroup>
            </form>
          </CardContent>
        </Card>
      )}
    </div>
  )
}

export default Settings