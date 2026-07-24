import { createBrowserRouter, RouterProvider } from "react-router"
import Login from "@/pages/Login"
import Dashboard from "@/pages/Dashboard"

const router = createBrowserRouter([
  { path: "/", element: <Login /> },
  { path: "/dashboard", element: <Dashboard /> },
])

function App() {
  return <RouterProvider router={router} />
}

export default App