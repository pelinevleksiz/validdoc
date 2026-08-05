import { useState } from "react"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { useMutation, useQueryClient } from "@tanstack/react-query"
import { useTranslation } from "react-i18next"
import { api } from "@/lib/api"
import { getErrorMessage } from "@/lib/errors"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@/components/ui/dialog"
import {
  Field,
  FieldError,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"

const createUserSchema = z.object({
  username: z.string().min(1, "Kullanıcı adı zorunlu"),
  password: z.string().min(8, "Şifre en az 8 karakter olmalı"),
  role: z.enum(["ADMIN", "OPERATOR"], { message: "Rol seçin" }),
})

type CreateUserValues = z.infer<typeof createUserSchema>

function CreateUserDialog({ defaultOpen = false }: { defaultOpen?: boolean }) {
  const { t } = useTranslation()
  const queryClient = useQueryClient()
  const [open, setOpen] = useState(defaultOpen)
  const [serverErrorMessage, setServerErrorMessage] = useState<string | null>(null)

  const form = useForm<CreateUserValues>({
    resolver: zodResolver(createUserSchema),
    defaultValues: { username: "", password: "", role: "OPERATOR" },
  })

  const createMutation = useMutation({
    mutationFn: async (values: CreateUserValues) => {
      const res = await api.post("/api/users", values)
      return res.data
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["users"] })
      form.reset()
      setOpen(false)
    },
    onError: (error: unknown) => {
      setServerErrorMessage(getErrorMessage(error))
    },
  })

  function onSubmit(values: CreateUserValues) {
    setServerErrorMessage(null)
    createMutation.mutate(values)
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger render={<Button>{t("users.addButton")}</Button>} />
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t("users.createDialogTitle")}</DialogTitle>
        </DialogHeader>

        {serverErrorMessage && (
          <div className="rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
            {serverErrorMessage}
          </div>
        )}

        <form onSubmit={form.handleSubmit(onSubmit)}>
          <FieldGroup>
            <Controller
              name="username"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="new-username">{t("users.usernameHeader")}</FieldLabel>
                  <Input {...field} id="new-username" />
                  {fieldState.invalid && <FieldError errors={[fieldState.error]} />}
                </Field>
              )}
            />

            <Controller
              name="password"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="new-password">{t("users.password")}</FieldLabel>
                  <Input {...field} id="new-password" type="password" />
                  {fieldState.invalid && <FieldError errors={[fieldState.error]} />}
                </Field>
              )}
            />

            <Controller
              name="role"
              control={form.control}
              render={({ field, fieldState }) => (
                <Field data-invalid={fieldState.invalid}>
                  <FieldLabel htmlFor="new-role">{t("users.role")}</FieldLabel>
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id="new-role">
                      <SelectValue placeholder={t("users.selectRole")}>
                        {(value: string) => (value === "ADMIN" ? t("users.roleAdmin") : t("users.roleOperator"))}
                      </SelectValue>
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="OPERATOR">{t("users.roleOperator")}</SelectItem>
                      <SelectItem value="ADMIN">{t("users.roleAdmin")}</SelectItem>
                    </SelectContent>
                  </Select>
                  {fieldState.invalid && <FieldError errors={[fieldState.error]} />}
                </Field>
              )}
            />

            <Button type="submit" className="w-full" disabled={createMutation.isPending}>
              {createMutation.isPending ? t("users.creating") : t("users.create")}
            </Button>
          </FieldGroup>
        </form>
      </DialogContent>
    </Dialog>
  )
}

export default CreateUserDialog