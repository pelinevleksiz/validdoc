export type Language = "tr" | "en"

const LANGUAGE_STORAGE_KEY = "validdoc_lang"

export function getLanguage(): Language {
  const stored = localStorage.getItem(LANGUAGE_STORAGE_KEY)
  return stored === "en" ? "en" : "tr"
}

export function setLanguage(language: Language) {
  localStorage.setItem(LANGUAGE_STORAGE_KEY, language)
}