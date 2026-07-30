import { useState } from "react"
import { Button } from "@/components/ui/button"
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
        variant={language === "tr" ? "default" : "ghost"}
        size="sm"
        onClick={() => handleChange("tr")}
      >
        TR
      </Button>
      <Button
        variant={language === "en" ? "default" : "ghost"}
        size="sm"
        onClick={() => handleChange("en")}
      >
        EN
      </Button>
    </div>
  )
}

export default LanguageSwitcher