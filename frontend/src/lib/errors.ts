import axios from "axios"
import i18n from "@/lib/i18n"

export function getErrorMessage(error: unknown, fallbackKey: string = "errors.GENERIC"): string {
  if (axios.isAxiosError(error)) {
    const message = error.response?.data?.message
    if (typeof message === "string" && message.length > 0) {
      return message
    }
  }
  return i18n.t(fallbackKey)
}