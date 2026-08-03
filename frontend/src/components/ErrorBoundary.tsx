import { Component, type ErrorInfo, type ReactNode } from "react"
import { getLanguage } from "@/lib/language"
import { Button } from "@/components/ui/button"

interface ErrorBoundaryProps {
  children: ReactNode
}

interface ErrorBoundaryState {
  hasError: boolean
}

const TEXT = {
  tr: {
    title: "Bir şeyler ters gitti",
    description: "Beklenmeyen bir hata oluştu. Sayfayı yenilemeyi deneyebilirsiniz.",
    reload: "Sayfayı yenile",
  },
  en: {
    title: "Something went wrong",
    description: "An unexpected error occurred. Try reloading the page.",
    reload: "Reload page",
  },
}

class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props)
    this.state = { hasError: false }
  }

  static getDerivedStateFromError() {
    return { hasError: true }
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    console.error("Yakalanmamis render hatasi:", error, errorInfo)
  }

  render() {
    if (this.state.hasError) {
      const text = TEXT[getLanguage()]
      return (
        <div className="flex min-h-screen flex-col items-center justify-center gap-3 px-4 text-center">
          <h1 className="text-xl font-semibold">{text.title}</h1>
          <p className="max-w-sm text-sm text-muted-foreground">{text.description}</p>
          <Button onClick={() => window.location.reload()}>{text.reload}</Button>
        </div>
      )
    }

    return this.props.children
  }
}

export default ErrorBoundary