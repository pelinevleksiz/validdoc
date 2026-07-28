import panelTitle from "@/assets/panel-title.png"

function Dashboard() {
  return (
    <div>
      <img src={panelTitle} alt="Panel" className="mb-4 h-6.75 w-auto" />
      <p className="text-muted-foreground">Bu sayfa henüz yapım aşamasında.</p>
    </div>
  )
}

export default Dashboard