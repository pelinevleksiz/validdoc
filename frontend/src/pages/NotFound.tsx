import { Link } from "react-router"
import { useTranslation } from "react-i18next"
import { Button } from "@/components/ui/button"

function NotFound() {
  const { t } = useTranslation()

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-3 px-4 text-center">
      <h1 className="font-amarego lowercase text-4xl">404</h1>
      <p className="text-lg font-medium">{t("common.notFoundTitle")}</p>
      <p className="max-w-sm text-sm text-muted-foreground">{t("common.notFoundDescription")}</p>
      <Button render={<Link to="/dashboard">{t("common.notFoundBackLink")}</Link>} />
    </div>
  )
}

export default NotFound