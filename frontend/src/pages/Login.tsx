import { useEffect, useState } from "react"
import { Controller, useForm } from "react-hook-form"
import { zodResolver } from "@hookform/resolvers/zod"
import { z } from "zod"
import { useMutation } from "@tanstack/react-query"
import { useNavigate } from "react-router"
import axios from "axios"
import { useTranslation } from "react-i18next"
import { api } from "@/lib/api"
import { useAuth } from "@/contexts/AuthContext"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import {
  Field,
  FieldError,
  FieldGroup,
  FieldLabel,
} from "@/components/ui/field"
import LanguageSwitcher from "@/components/layout/LanguageSwitcher"

const loginSchema = z.object({
  username: z.string().min(1, "Kullanıcı adı zorunlu!"),
  password: z.string().min(1, "Şifre zorunlu!"),
})

type LoginFormValues = z.infer<typeof loginSchema>

interface LoginResponse {
  token: string
  role: string
}

const KNOWN_ERROR_CODES = ["BAD_CREDENTIALS", "TOO_MANY_LOGIN_ATTEMPTS"]

function Login() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { login } = useAuth()
  const [showPassword, setShowPassword] = useState(false)
  const [serverErrorCode, setServerErrorCode] = useState<string | null>(() => {
    const params = new URLSearchParams(window.location.search)
    return params.get("expired") === "1" ? "SESSION_EXPIRED" : null
  })
  const [rateLimitSecondsLeft, setRateLimitSecondsLeft] = useState<number | null>(null)
  const [remainingAttempts, setRemainingAttempts] = useState<number | null>(null)

  useEffect(() => {
    if (rateLimitSecondsLeft === null) return
    if (rateLimitSecondsLeft <= 0) {
      setRateLimitSecondsLeft(null)
      setServerErrorCode(null)
      return
    }
    const timeout = setTimeout(() => {
      setRateLimitSecondsLeft((prev) => (prev === null ? null : prev - 1))
    }, 1000)
    return () => clearTimeout(timeout)
  }, [rateLimitSecondsLeft])

  const form = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { username: "", password: "" },
  })

  const loginMutation = useMutation({
    mutationFn: async (values: LoginFormValues) => {
      const res = await api.post<LoginResponse>("/api/auth/login", values)
      return res.data
    },
    onSuccess: (data, variables) => {
      login(data.token, data.role, variables.username)
      navigate("/dashboard")
    },
    onError: (error: unknown) => {
      if (axios.isAxiosError(error) && error.response?.status === 429) {
        const retryAfter = error.response.data?.retryAfterSeconds
        setRateLimitSecondsLeft(typeof retryAfter === "number" ? retryAfter : null)
        setRemainingAttempts(null)
      } else if (axios.isAxiosError(error) && error.response?.status === 401) {
        const remaining = error.response.data?.remainingAttempts
        setRemainingAttempts(typeof remaining === "number" ? remaining : null)
      }

      const code = axios.isAxiosError(error) ? error.response?.data?.code : null
      setServerErrorCode(code && KNOWN_ERROR_CODES.includes(code) ? code : "GENERIC")
    },
  })

  function onSubmit(values: LoginFormValues) {
    setServerErrorCode(null)
    loginMutation.mutate(values)
  }

  const isRateLimited = rateLimitSecondsLeft !== null && rateLimitSecondsLeft > 0
  const showRemainingAttemptsWarning = remainingAttempts !== null && remainingAttempts <= 3

  return (
    <div className="relative flex min-h-screen items-center justify-center p-4">
      <div className="absolute right-4 top-4">
        <LanguageSwitcher />
      </div>

      <Card className="w-full max-w-sm">
        <CardHeader>
          <CardTitle className="text-xl">{t("login.title")}</CardTitle>
          <CardDescription>{t("login.description")}</CardDescription>
        </CardHeader>
        <CardContent>
          {serverErrorCode && (
            <div className="mb-4 rounded-md bg-destructive/10 px-3 py-2 text-sm text-destructive">
              {t(`errors.${serverErrorCode}`)}
              {showRemainingAttemptsWarning && (
                <div className="mt-1 text-xs">
                  {t("login.remainingAttempts")}: {remainingAttempts}
                </div>
              )}
            </div>
          )}

          <form id="login-form" onSubmit={form.handleSubmit(onSubmit)}>
            <FieldGroup>
              <Controller
                name="username"
                control={form.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="login-username">{t("login.username")}</FieldLabel>
                    <Input
                      {...field}
                      id="login-username"
                      aria-invalid={fieldState.invalid}
                      autoComplete="username"
                    />
                    {fieldState.invalid && (
                      <FieldError errors={[fieldState.error]} />
                    )}
                  </Field>
                )}
              />

              <Controller
                name="password"
                control={form.control}
                render={({ field, fieldState }) => (
                  <Field data-invalid={fieldState.invalid}>
                    <FieldLabel htmlFor="login-password">{t("login.password")}</FieldLabel>
                    <div className="relative">
                      <Input
                        {...field}
                        id="login-password"
                        type={showPassword ? "text" : "password"}
                        aria-invalid={fieldState.invalid}
                        autoComplete="current-password"
                      />
                      <button
                        type="button"
                        onClick={() => setShowPassword((prev) => !prev)}
                        className="absolute right-3 top-1/2 -translate-y-1/2 text-xs text-muted-foreground"
                      >
                        {showPassword ? t("login.hide") : t("login.show")}
                      </button>
                    </div>
                    {fieldState.invalid && (
                      <FieldError errors={[fieldState.error]} />
                    )}
                  </Field>
                )}
              />

              <Button
                type="submit"
                className="w-full"
                disabled={loginMutation.isPending || isRateLimited}
              >
                {isRateLimited
                  ? `${rateLimitSecondsLeft}sn`
                  : loginMutation.isPending
                    ? t("login.submitting")
                    : t("login.submit")}
              </Button>
            </FieldGroup>
          </form>

          <p className="mt-4 text-center text-xs text-muted-foreground">
            {t("login.forgotPassword")}
          </p>
        </CardContent>
      </Card>
    </div>
  )
}

export default Login