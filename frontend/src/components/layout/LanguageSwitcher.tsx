import { useTranslation } from "react-i18next"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import { setLanguage, type Language } from "@/lib/language"

interface LanguageSwitcherProps {
  variant?: "desktop" | "mobile"
}

function LanguageSwitcher({ variant = "desktop" }: LanguageSwitcherProps) {
  const { i18n } = useTranslation()
  const language = i18n.language as Language

  function handleChange(next: Language) {
    setLanguage(next)
    i18n.changeLanguage(next)
  }

  if (variant === "mobile") {
    return (
      <button
        type="button"
        onClick={() => handleChange(language === "tr" ? "en" : "tr")}
        className="flex h-9 w-9 items-center justify-center rounded-md border bg-background text-sm font-medium text-muted-foreground hover:bg-accent hover:text-accent-foreground"
      >
        {language.toUpperCase()}
      </button>
    )
  }

  return (
    <div className="flex items-center gap-1 text-sm">
      <Button
        variant="outline"
        size="sm"
        className={cn("h-8 w-8 px-0", language === "tr" ? "font-semibold text-foreground" : "font-normal text-muted-foreground")}
        onClick={() => handleChange("tr")}
      >
        TR
      </Button>
      <Button
        variant="outline"
        size="sm"
        className={cn("h-8 w-8 px-0", language === "en" ? "font-semibold text-foreground" : "font-normal text-muted-foreground")}
        onClick={() => handleChange("en")}
      >
        EN
      </Button>
    </div>
  )
}

export default LanguageSwitcher