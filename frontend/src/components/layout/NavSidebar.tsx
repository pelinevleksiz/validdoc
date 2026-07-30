import NavLinks from "@/components/layout/NavLinks"

function NavSidebar() {
  return (
    <aside className="hidden w-56 border-r p-4 md:block">
      <div className="font-amarego lowercase mb-6 w-full text-center text-4xl">validdoc</div>
      <NavLinks />
    </aside>
  )
}

export default NavSidebar