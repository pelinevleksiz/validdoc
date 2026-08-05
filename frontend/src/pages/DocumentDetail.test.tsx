import { describe, expect, it, vi, beforeEach } from "vitest"
import { screen, waitFor } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { Routes, Route } from "react-router"
import { renderWithProviders } from "@/test/test-utils"
import { saveSession } from "@/lib/auth"
import DocumentDetail from "@/pages/DocumentDetail"
import { api } from "@/lib/api"

vi.mock("@/lib/api", () => ({
  api: {
    post: vi.fn(),
    get: vi.fn(),
    delete: vi.fn(),
  },
}))

function makeFakeToken(role: string) {
  const header = btoa(JSON.stringify({ alg: "none" }))
  const farFutureSeconds = Math.floor(Date.now() / 1000) + 3600
  const payload = btoa(JSON.stringify({ sub: "test-user", role, exp: farFutureSeconds }))
  return `${header}.${payload}.signature`
}

function loginAs(role: "ADMIN" | "OPERATOR") {
  saveSession(makeFakeToken(role), role, role === "ADMIN" ? "test-admin" : "test-operator")
}

const mockDocument = {
  id: 42,
  fileName: "test-belge.png",
  status: "VALIDATED",
  templateId: 7,
  segmentResults: JSON.stringify([
    { segmentId: 1, label: "Ad Soyad", outcome: "FILLED_VALID" },
    { segmentId: 2, label: "İmza", outcome: "FILLED_VALID" },
  ]),
  uploadedByUsername: "operator1",
  uploadedAt: "2026-01-01T10:00:00Z",
}

const mockTemplate = {
  id: 7,
  name: "Test Şablon",
  segments: [
    { id: 1, label: "Ad Soyad", page: 1 },
    { id: 2, label: "İmza", page: 1 },
  ],
}

function mockApiGetSuccess() {
  vi.mocked(api.get).mockImplementation((url: unknown) => {
    const u = String(url)
    if (u.includes("/segments/") && u.includes("/image")) {
      return Promise.resolve({ data: new Blob(["fake-bytes"], { type: "image/jpeg" }) } as never)
    }
    if (u.startsWith("/api/documents/")) {
      return Promise.resolve({ data: mockDocument } as never)
    }
    if (u.startsWith("/api/templates/")) {
      return Promise.resolve({ data: mockTemplate } as never)
    }
    return Promise.reject(new Error("beklenmeyen url: " + u))
  })
}

function renderDocumentDetail() {
  return renderWithProviders(
    <Routes>
      <Route path="/documents/:id" element={<DocumentDetail />} />
    </Routes>,
    { route: "/documents/42" }
  )
}

describe("DocumentDetail", () => {
  beforeEach(() => {
    vi.mocked(api.get).mockReset()
    vi.mocked(api.post).mockReset()
    localStorage.clear()
  })

  it("segment listesini küçük görüntülerle birlikte gösterir", async () => {
    loginAs("OPERATOR")
    mockApiGetSuccess()
    renderDocumentDetail()

    expect(await screen.findByText("Ad Soyad")).toBeInTheDocument()
    expect(screen.getByText("İmza")).toBeInTheDocument()
  })

  it("operatöre müdahale et butonunu göstermez", async () => {
    loginAs("OPERATOR")
    mockApiGetSuccess()
    renderDocumentDetail()

    await screen.findByText("Ad Soyad")
    const user = userEvent.setup()
    const thumbnails = await screen.findAllByRole("button")
    await user.click(thumbnails[0])

    await waitFor(() => expect(screen.queryByText(/müdahale et/i)).not.toBeInTheDocument())
  })

  it("admin'e karar verilmiş bir segmentte müdahale et butonunu gösterir", async () => {
    loginAs("ADMIN")
    mockApiGetSuccess()
    renderDocumentDetail()

    await screen.findByText("Ad Soyad")
    const user = userEvent.setup()
    const thumbnails = await screen.findAllByRole("button")
    await user.click(thumbnails[0])

    expect(await screen.findByText(/müdahale et/i)).toBeInTheDocument()
  })

  it("görüntü yüklenemezse yer tutucu metni gösterir", async () => {
    loginAs("OPERATOR")
    vi.mocked(api.get).mockImplementation((url: unknown) => {
      const u = String(url)
      if (u.includes("/segments/") && u.includes("/image")) {
        return Promise.reject(new Error("404"))
      }
      if (u.startsWith("/api/documents/")) {
        return Promise.resolve({ data: mockDocument } as never)
      }
      if (u.startsWith("/api/templates/")) {
        return Promise.resolve({ data: mockTemplate } as never)
      }
      return Promise.reject(new Error("beklenmeyen url: " + u))
    })
    renderDocumentDetail()

    expect(await screen.findAllByText(/görüntü yok/i)).toHaveLength(2)
  })

  it("admin müdahale akışını uçtan uca tamamlayabilir", async () => {
    loginAs("ADMIN")
    mockApiGetSuccess()
    vi.mocked(api.post).mockResolvedValue({ data: {} } as never)
    renderDocumentDetail()

    await screen.findByText("Ad Soyad")
    const user = userEvent.setup()
    const thumbnails = await screen.findAllByRole("button")
    await user.click(thumbnails[0])

    await user.click(await screen.findByText(/müdahale et/i))
    await user.click(await screen.findByText(/evet, devam et/i))

    const outcomeSelect = await screen.findByLabelText(/yeni karar/i)
    await user.selectOptions(outcomeSelect, "FILLED_INVALID")

    await user.click(await screen.findByText(/^kaydet$/i))

    await waitFor(() =>
      expect(api.post).toHaveBeenCalledWith(
        "/api/documents/42/segments/1/override",
        expect.objectContaining({ reasonCode: "OCR_MISREAD" })
      )
    )
  })
})