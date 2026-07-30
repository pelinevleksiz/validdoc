import { useTranslation } from "react-i18next"

function Dashboard() {
  const { t } = useTranslation()
  return (
    <div>
      <h1 className="font-amarego lowercase mb-4 text-3xl">{t("nav.dashboard")}</h1>
      <p className="text-muted-foreground">{t("dashboard.placeholder")}</p>
    </div>
  )
}

export default Dashboard