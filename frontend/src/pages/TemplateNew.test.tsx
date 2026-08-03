import { describe, expect, it, vi, beforeEach } from "vitest"
import { screen } from "@testing-library/react"
import userEvent from "@testing-library/user-event"
import { renderWithProviders } from "@/test/test-utils"
import TemplateNew from "@/pages/TemplateNew"
import { api } from "@/lib/api"

vi.mock("@/lib/api", () => ({
  api: {
    post: vi.fn(),
    get: vi.fn(),
    delete: vi.fn(),
  },
}))

vi.mock("pdfjs-dist", () => ({
  GlobalWorkerOptions: { workerPort: null },
  getDocument: vi.fn(),
}))

vi.mock("react-konva", () => ({
  Stage: ({ children }: { children?: unknown }) => <div data-testid="konva-stage">{children as never}</div>,
  Layer: ({ children }: { children?: unknown }) => <div>{children as never}</div>,
  Image: () => <div data-testid="konva-image" />,
  Rect: () => <div data-testid="konva-rect" />,
  Text: () => <div data-testid="konva-text" />,
}))

describe("TemplateNew", () => {
  beforeEach(() => {
    vi.mocked(api.get).mockReset()
    vi.mocked(api.get).mockResolvedValue({
      data: [
        { type: "LETTERS_ONLY", requiresParam: false, inkRule: false },
        { type: "SIGNATURE_INK", requiresParam: false, inkRule: true },
      ],
    } as never)
  })

  it("başlangıçta şablon adı alanını ve dosya bırakma alanını gösterir", () => {
    renderWithProviders(<TemplateNew />)
    expect(screen.getByLabelText(/şablon adı/i)).toBeInTheDocument()
    expect(screen.getByText(/örnek belge yükle/i)).toBeInTheDocument()
  })

  it("şablon adı yazılabilir", async () => {
    const user = userEvent.setup()
    renderWithProviders(<TemplateNew />)

    const nameInput = screen.getByLabelText(/şablon adı/i)
    await user.type(nameInput, "Kimlik Kartı")

    expect(nameInput).toHaveValue("Kimlik Kartı")
  })

  it("hiç segment eklenmeden kaydet butonu devre dışı kalır", () => {
    renderWithProviders(<TemplateNew />)
    expect(screen.getByRole("button", { name: /şablonu kaydet/i })).toBeDisabled()
  })
})