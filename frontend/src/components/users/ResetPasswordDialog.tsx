import { useState } from "react"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { useMutation } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { api } from "@/lib/api"
import { getErrorMessage } from "@/lib/errors"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import {
  Field,
  FieldError,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field"

const resetPasswordSchema = z.object({
  adminPassword: z.string().min(1, "Şifrenizi girin"),
  newPassword: z.string().min(8, "Şifre en az 8 karakter olmalı"),
})

type ResetPasswordValues = z.infer<typeof resetPasswordSchema>

function ResetPasswordDialog({
  userId,
  username,
  open,
  onOpenChange,
}: {
  userId: number
  username: string
  open: boolean
  onOpenChange: (open: boolean) => void
}) {
  const { t } = useTranslation()
  const [serverErrorMessage, setServerErrorMessage] = useState<string | null>(null)

  const form = useForm<ResetPasswordValues>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { adminPassword: "", newPassword: "" },
  })

  const resetMutation = useMutation({
    mutationFn: async (values: ResetPasswordValues) => {
      await api.put(`/api/users/${userId}/password`, values)
    },
    onSuccess: () => {
      form.reset()
      onOpenChange(false)
    },
    onError: (error: unknown) => {
      setServerErrorMessage(getErrorMessage(error))
    },
  })

  function onSubmit(values: ResetPasswordValues) {
    setServerErrorMessage(null)
    resetMutation.mutate(values)
  }

  function handleOpenChange(next: boolean) {
    if (!next) {
      form.reset()
      setServerErrorMessage(null)
    }
    onOpenChange(next)
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("users.resetPasswordDialogTitle")}</DialogTitle>
          <DialogDescription>
            {t("users.resetPasswordDialogPrefix")}{" "}
            <strong className="rounded-md bg-foreground/5 px-1.5 py-0.5 font-semibold text-foreground">
              {username}
            </strong>{" "}
            {t("users.resetPasswordDialogSuffix")}
          </DialogDescription>
        </DialogHeader>

        {serverErrorMessage && (
          <div className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {serverErrorMessage}
          </div>
        )}

        <form onSubmit={form.handleSubmit(onSubmit)}>
          <FieldGroup>
            <Controller
              name="adminPassword"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="reset-admin-password">{t("users.yourPasswordLabel")}</FieldLabel>
                  <Input {...field} id="reset-admin-password" type="password" autoComplete="current-password" />
                  {fieldState.invalid && <FieldError errors={[fieldState.error]} />}
                </Field>
              )}
            />

            <Controller
              name="newPassword"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="reset-new-password">{t("users.newPasswordLabel")}</FieldLabel>
                  <Input {...field} id="reset-new-password" type="password" />
                  {fieldState.invalid && <FieldError errors={[fieldState.error]} />}
                </Field>
              )}
            />

            <Button type="submit" className="w-full" disabled={resetMutation.isPending}>
              {resetMutation.isPending ? t("users.resettingPassword") : t("users.resetPasswordSubmit")}
            </Button>
          </FieldGroup>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export default ResetPasswordDialog