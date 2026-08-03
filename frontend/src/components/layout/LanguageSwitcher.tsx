import { useState } from "react"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"
import { getLanguage, setLanguage, type Language } from "@/lib/language"
import i18n from "@/lib/i18n"

function LanguageSwitcher() {
  const [language, setLanguageState] = useState<Language>(getLanguage())

  function handleChange(next: Language) {
    setLanguage(next)
    setLanguageState(next)
    i18n.changeLanguage(next)
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