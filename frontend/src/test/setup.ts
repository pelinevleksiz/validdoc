import "@testing-library/jest-dom/vitest"
import { afterEach, vi } from "vitest"
import { cleanup } from "@testing-library/react"

class WorkerStub {
  onmessage: ((event: MessageEvent) => void) | null = null
  onerror: ((event: ErrorEvent) => void) | null = null
  postMessage() {}
  terminate() {}
  addEventListener() {}
  removeEventListener() {}
  dispatchEvent() {
    return false
  }
}

vi.stubGlobal("Worker", WorkerStub)

if (!window.matchMedia) {
  vi.stubGlobal(
    "matchMedia",
    vi.fn().mockImplementation((query: string) => ({
      matches: false,
      media: query,
      onchange: null,
      addListener: vi.fn(),
      removeListener: vi.fn(),
      addEventListener: vi.fn(),
      removeEventListener: vi.fn(),
      dispatchEvent: vi.fn(),
    }))
  )
}

if (!Blob.prototype.arrayBuffer) {
  Object.defineProperty(Blob.prototype, "arrayBuffer", {
    configurable: true,
    writable: true,
    value(this: Blob) {
      return Promise.resolve(new ArrayBuffer(this.size))
    },
  })
}

if (!URL.createObjectURL) {
  URL.createObjectURL = vi.fn(() => "blob:mock")
  URL.revokeObjectURL = vi.fn()
}

if (!window.ResizeObserver) {
  class ResizeObserverStub {
    observe() {}
    unobserve() {}
    disconnect() {}
  }
  vi.stubGlobal("ResizeObserver", ResizeObserverStub)
}

if (!Element.prototype.hasPointerCapture) {
  Element.prototype.hasPointerCapture = () => false
}
if (!Element.prototype.setPointerCapture) {
  Element.prototype.setPointerCapture = () => {}
}
if (!Element.prototype.releasePointerCapture) {
  Element.prototype.releasePointerCapture = () => {}
}
if (!Element.prototype.scrollIntoView) {
  Element.prototype.scrollIntoView = () => {}
}

afterEach(() => {
  cleanup()
  localStorage.clear()
})