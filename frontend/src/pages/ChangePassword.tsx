import { useState } from "react"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { useMutation } from "@tanstack/react-query"
import axios from "axios"
import { useTranslation } from "react-i18next"
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

const changePasswordSchema = z.object({
  currentPassword: z.string().min(1, "Mevcut şifre zorunlu"),
  newPassword: z.string().min(8, "Yeni şifre en az 8 karakter olmalı"),
})

type ChangePasswordValues = z.infer<typeof changePasswordSchema>

const KNOWN_ERROR_CODES = ["BAD_CREDENTIALS"]

function ChangePassword() {
  const { t } = useTranslation()
  const [serverErrorCode, setServerErrorCode] = useState<string | null>(null)
  const [success, setSuccess] = useState(false)

  const form = useForm<ChangePasswordValues>({
    resolver: zodResolver(changePasswordSchema),
    defaultValues: { currentPassword: "", newPassword: "" },
  })

  const changeMutation = useMutation({
    mutationFn: async (values: ChangePasswordValues) => {
      await api.put("/api/users/me/password", values)
    },
    onSuccess: () => {
      setSuccess(true)
      setServerErrorCode(null)
      form.reset()
    },
    onError: (error: unknown) => {
      setSuccess(false)
      const code = axios.isAxiosError(error) ? error.response?.data?.code : null
      setServerErrorCode(code && KNOWN_ERROR_CODES.includes(code) ? code : "GENERIC")
    },
  })

  function onSubmit(values: ChangePasswordValues) {
    setServerErrorCode(null)
    setSuccess(false)
    changeMutation.mutate(values)
  }

  return (
    <Card className="max-w-sm">
      <CardHeader>
        <h2 className="font-amarego lowercase text-2xl">{t("changePassword.title")}</h2>
        <CardDescription>{t("changePassword.description")}</CardDescription>
      </CardHeader>
      <CardContent>
        {serverErrorCode && (
          <div className="mb-4 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {t(`errors.${serverErrorCode}`)}
          </div>
        )}
        {success && (
          <div className="mb-4 rounded-md bg-emerald-500/10 px-3 py-2 text-sm text-emerald-600">
            {t("changePassword.success")}
          </div>
        )}

        <form onSubmit={form.handleSubmit(onSubmit)}>
          <FieldGroup>
            <Controller
              name="currentPassword"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="current-password">{t("changePassword.currentPassword")}</FieldLabel>
                  <Input {...field} id="current-password" type="password" />
                  {fieldState.invalid && <FieldError errors={[fieldState.error]} />}
                </Field>
              )}
            />

            <Controller
              name="newPassword"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="new-password-field">{t("changePassword.newPassword")}</FieldLabel>
                  <Input {...field} id="new-password-field" type="password" />
                  {fieldState.invalid && <FieldError errors={[fieldState.error]} />}
                </Field>
              )}
            />

            <Button type="submit" className="w-full" disabled={changeMutation.isPending}>
              {changeMutation.isPending ? t("changePassword.submitting") : t("changePassword.submit")}
            </Button>
          </FieldGroup>
        </form>
      </CardContent>
    </Card>
  )
}

export default ChangePassword