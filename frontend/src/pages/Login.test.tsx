import { describe, expect, it, vi, beforeEach } from "vitest"
import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { renderWithProviders } from "@/test/test-utils"
import Login from "@/pages/Login"
import { api } from "@/lib/api"

const navigateMock = vi.fn()

vi.mock("@/lib/api", () => ({
  api: {
    post: vi.fn(),
    get: vi.fn(),
    delete: vi.fn(),
  },
}))

vi.mock("react-router", async () => {
  const actual = await vi.importActual<typeof import("react-router")>("react-router")
  return { ...actual, useNavigate: () => navigateMock }
})

describe("Login", () => {
  beforeEach(() => {
    vi.mocked(api.post).mockReset()
    navigateMock.mockReset()
  })

  it("kullanıcı adı ve şifre alanlarını gösterir", () => {
    renderWithProviders(<Login />)
    expect(screen.getByLabelText(/kullanıcı adı/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/şifre/i)).toBeInTheDocument()
    expect(screen.getByRole("button", { name: /^giriş$/i })).toBeInTheDocument()
  })

  it("boş formu göndermeye çalışınca doğrulama hatalarını gösterir", async () => {
    const user = userEvent.setup()
    renderWithProviders(<Login />)

    await user.click(screen.getByRole("button", { name: /^giriş$/i }))

    expect(await screen.findByText(/kullanıcı adı zorunlu/i)).toBeInTheDocument()
    expect(screen.getByText(/şifre zorunlu/i)).toBeInTheDocument()
    expect(api.post).not.toHaveBeenCalled()
  })

  it("başarılı girişte token'ı kaydeder ve panele yönlendirir", async () => {
    const user = userEvent.setup()
    vi.mocked(api.post).mockResolvedValue({
      data: { token: "test-token", role: "ADMIN" },
    } as never)

    renderWithProviders(<Login />)

    await user.type(screen.getByLabelText(/kullanıcı adı/i), "admin")
    await user.type(screen.getByLabelText(/şifre/i), "SifrePass1!")
    await user.click(screen.getByRole("button", { name: /^giriş$/i }))

    await waitFor(() => expect(navigateMock).toHaveBeenCalledWith("/dashboard"))
    expect(api.post).toHaveBeenCalledWith("/api/auth/login", {
      username: "admin",
      password: "SifrePass1!",
    })
  })

  it("hatalı kimlik bilgilerinde sunucu hata mesajını gösterir", async () => {
    const user = userEvent.setup()
    const axiosError = Object.assign(new Error("Unauthorized"), {
      isAxiosError: true,
      response: { status: 401, data: { code: "BAD_CREDENTIALS", message: "Kullanıcı adı veya şifre hatalı." } },
    })
    vi.mocked(api.post).mockRejectedValue(axiosError)

    renderWithProviders(<Login />)

    await user.type(screen.getByLabelText(/kullanıcı adı/i), "admin")
    await user.type(screen.getByLabelText(/şifre/i), "yanlis")
    await user.click(screen.getByRole("button", { name: /^giriş$/i }))

    expect(await screen.findByText(/kullanıcı adı veya şifre hatalı/i)).toBeInTheDocument()
    expect(navigateMock).not.toHaveBeenCalled()
  })
})