import { useState } from "react"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { useMutation } from "@tanstack/react-query"
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
import changePasswordTitle from "@/assets/sifre-degistir-title.png"

const changePasswordSchema = z.object({
  currentPassword: z.string().min(1, "Mevcut şifre zorunlu"),
  newPassword: z.string().min(8, "Yeni şifre en az 8 karakter olmalı"),
})

type ChangePasswordValues = z.infer<typeof changePasswordSchema>

function ChangePassword() {
  const [serverError, setServerError] = useState<string | null>(null)
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
      setServerError(null)
      form.reset()
    },
    onError: (error: unknown) => {
      setSuccess(false)
      if (axios.isAxiosError(error) && error.response?.data?.message) {
        setServerError(error.response.data.message)
      } else {
        setServerError("Bir hata oluştu, lütfen tekrar deneyin.")
      }
    },
  })

  function onSubmit(values: ChangePasswordValues) {
    setServerError(null)
    setSuccess(false)
    changeMutation.mutate(values)
  }

  return (
    <Card className="max-w-sm">
      <CardHeader>
        <img src={changePasswordTitle} alt="Şifre değiştir" className="mb-1 h-8.25 w-auto" />
        <CardDescription>Kendi hesabının şifresini güncelle.</CardDescription>
      </CardHeader>
      <CardContent>
        {serverError && (
          <div className="mb-4 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {serverError}
          </div>
        )}
        {success && (
          <div className="mb-4 rounded-md bg-emerald-500/10 px-3 py-2 text-sm text-emerald-600">
            Şifre başarıyla güncellendi.
          </div>
        )}

        <form onSubmit={form.handleSubmit(onSubmit)}>
          <FieldGroup>
            <Controller
              name="currentPassword"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="current-password">Mevcut şifre</FieldLabel>
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
                  <FieldLabel htmlFor="new-password-field">Yeni şifre</FieldLabel>
                  <Input {...field} id="new-password-field" type="password" />
                  {fieldState.invalid && <FieldError errors={[fieldState.error]} />}
                </Field>
              )}
            />

            <Button type="submit" className="w-full" disabled={changeMutation.isPending}>
              {changeMutation.isPending ? "Güncelleniyor..." : "Şifreyi güncelle"}
            </Button>
          </FieldGroup>
        </form>
      </CardContent>
    </Card>
  )
}

export default ChangePassword