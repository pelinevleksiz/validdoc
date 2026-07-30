import { useState } from "react"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { useTranslation } from "react-i18next"
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

interface ValidationSettingsData {
  retentionDays: number
  inkDensityThreshold: number
  ocrConfidenceThreshold: number
  updatedAt: string
  updatedBy: string
}

function useSettingsSchema() {
  const { t } = useTranslation()
  return z.object({
    retentionDays: z
      .string()
      .min(1, t("settings.required"))
      .refine((v) => Number.isInteger(Number(v)) && Number(v) > 0, t("settings.retentionInvalid")),
    inkDensityThreshold: z
      .string()
      .min(1, t("settings.required"))
      .refine((v) => !Number.isNaN(Number(v)) && Number(v) >= 0 && Number(v) <= 1, t("settings.inkDensityInvalid")),
    ocrConfidenceThreshold: z
      .string()
      .min(1, t("settings.required"))
      .refine((v) => !Number.isNaN(Number(v)) && Number(v) >= 0 && Number(v) <= 100, t("settings.ocrConfidenceInvalid")),
  })
}

type SettingsFormValues = z.infer<ReturnType<typeof useSettingsSchema>>

function Settings() {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [serverErrorCode, setServerErrorCode] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)
  const settingsSchema = useSettingsSchema()

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
      setServerErrorCode(null)
    },
    onError: (error: unknown) => {
      setSuccess(false)
      const code = axios.isAxiosError(error) ? error.response?.data?.code : null
      setServerErrorCode(code ?? "GENERIC")
    },
  })

  function onSubmit(values: SettingsFormValues) {
    setServerErrorCode(null)
    setSuccess(false)
    updateMutation.mutate(values)
  }

  return (
    <div>
      {isLoading && <p className="text-muted-foreground">{t("common.loading")}</p>}

      {data && (
        <Card className="max-w-sm">
          <CardHeader>
            <h2 className="font-amarego lowercase text-2xl">{t("settings.title")}</h2>
            <CardDescription>
              {t("settings.lastUpdated", {
                by: data.updatedBy,
                at: new Date(data.updatedAt).toLocaleString(),
              })}
            </CardDescription>
          </CardHeader>
          <CardContent>
            {serverErrorCode && (
              <div className="mb-4 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
                {t(`errors.${serverErrorCode}`, t("errors.GENERIC"))}
              </div>
            )}
            {success && (
              <div className="mb-4 rounded-md bg-emerald-500/10 px-3 py-2 text-sm text-emerald-600">
                {t("settings.success")}
              </div>
            )}

            <form onSubmit={form.handleSubmit(onSubmit)} noValidate>
              <FieldGroup>
                <Controller
                  name="retentionDays"
                  control={form.control}
                  render={({ field, fieldState }) => (
                    <Field data-invalid={fieldState.invalid}>
                      <FieldLabel htmlFor="retention-days">{t("settings.retentionDays")}</FieldLabel>
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
                      <FieldLabel htmlFor="ink-density">{t("settings.inkDensity")}</FieldLabel>
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
                      <FieldLabel htmlFor="ocr-confidence">{t("settings.ocrConfidence")}</FieldLabel>
                      <Input {...field} id="ocr-confidence" type="number" step="0.1" min={0} max={100} />
                      {fieldState.invalid && <FieldError errors={[fieldState.error]} />}
                    </Field>
                  )}
                />

                <Button type="submit" className="w-full" disabled={updateMutation.isPending}>
                  {updateMutation.isPending ? t("common.saving") : t("common.save")}
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