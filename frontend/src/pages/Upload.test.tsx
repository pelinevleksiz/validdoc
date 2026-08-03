import { describe, expect, it, vi, beforeEach } from "vitest"
import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { renderWithProviders } from "@/test/test-utils"
import Upload from "@/pages/Upload"
import { api } from "@/lib/api"

const getDocumentMock = vi.fn()

vi.mock("@/lib/api", () => ({
  api: {
    post: vi.fn(),
    get: vi.fn(),
    delete: vi.fn(),
  },
}))

vi.mock("pdfjs-dist", () => ({
  GlobalWorkerOptions: { workerPort: null },
  getDocument: (...args: unknown[]) => getDocumentMock(...args),
}))

function makeFile(name: string, type: string) {
  return new File(["dummy-content"], name, { type })
}

describe("Upload", () => {
  beforeEach(() => {
    vi.mocked(api.get).mockReset()
    getDocumentMock.mockReset()
    vi.mocked(api.get).mockResolvedValue({
      data: { content: [{ id: 1, name: "Kimlik Kartı" }], page: 0, size: 50, totalElements: 1, totalPages: 1 },
    } as never)
  })

  it("başlangıçta belge yükleme alanını gösterir", () => {
    renderWithProviders(<Upload />)
    expect(screen.getByText(/sürükleyin ya da seçin/i)).toBeInTheDocument()
    expect(screen.getByRole("button", { name: /dosya seç/i })).toBeInTheDocument()
  })

  it("desteklenmeyen dosya tipinde hata gösterir ve adımı ilerletmez", async () => {
    const user = userEvent.setup({ applyAccept: false })
    const { container } = renderWithProviders(<Upload />)

    const input = container.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, makeFile("notlar.txt", "text/plain"))

    expect(await screen.findByText(/sadece pdf, png veya jpeg/i)).toBeInTheDocument()
    expect(screen.getByRole("button", { name: /dosya seç/i })).toBeInTheDocument()
  })

  it("bir PNG seçilince şablon seçim adımına geçer", async () => {
    const user = userEvent.setup()
    const { container } = renderWithProviders(<Upload />)

    const input = container.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, makeFile("belge.png", "image/png"))

    expect(await screen.findByText(/seçilen dosyalar/i)).toBeInTheDocument()
    expect(screen.getByText("belge.png", { exact: false })).toBeInTheDocument()
  })

  it("PDF okunamazsa hata gösterir ve dosyayı listeye eklemez", async () => {
    const user = userEvent.setup()
    getDocumentMock.mockImplementation(() => ({
      promise: Promise.reject(new Error("bozuk pdf")),
    }))

    const { container } = renderWithProviders(<Upload />)
    const input = container.querySelector('input[type="file"]') as HTMLInputElement
    await user.upload(input, makeFile("bozuk.pdf", "application/pdf"))

    expect(await screen.findByText(/okunamadı/i)).toBeInTheDocument()
    expect(screen.queryByText(/seçilen dosyalar/i)).not.toBeInTheDocument()
  })
})