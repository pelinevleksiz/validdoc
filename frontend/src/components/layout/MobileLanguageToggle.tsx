import { useState } from "react"
import { getLanguage, setLanguage, type Language } from "@/lib/language"
import i18n from "@/lib/i18n"

function MobileLanguageToggle() {
  const [language, setLanguageState] = useState<Language>(getLanguage())

  function toggle() {
    const next: Language = language === "tr" ? "en" : "tr"
    setLanguage(next)
    setLanguageState(next)
    i18n.changeLanguage(next)
  }

  return (
    <button
      type="button"
      onClick={toggle}
      className="flex h-9 w-9 items-center justify-center rounded-md border bg-background text-sm font-medium text-muted-foreground hover:bg-accent hover:text-accent-foreground"
    >
      {language.toUpperCase()}
    </button>
  )
}

export default MobileLanguageToggle