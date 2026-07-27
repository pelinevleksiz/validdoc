import { createBrowserRouter, RouterProvider } from "react-router"
import Login from "@/pages/Login"
import Dashboard from "@/pages/Dashboard"
import ProtectedRoute from "@/components/ProtectedRoute"
import AppLayout from "@/components/layout/AppLayout"

const router = createBrowserRouter([
  { path: "/", element: <Login /> },
  {
    element: (
      <ProtectedRoute>
        <AppLayout />
      </ProtectedRoute>
    ),
    children: [{ path: "/dashboard", element: <Dashboard /> }],
  },
])

function App() {
  return <RouterProvider router={router} />
}

export default App