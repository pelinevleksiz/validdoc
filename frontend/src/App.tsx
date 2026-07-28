import { createBrowserRouter, RouterProvider } from "react-router"
import Login from "@/pages/Login"
import Dashboard from "@/pages/Dashboard"
import Users from "@/pages/Users"
import ChangePassword from "@/pages/ChangePassword"
import Templates from "@/pages/Templates"
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
    children: [
      { path: "/dashboard", element: <Dashboard /> },
      {
        path: "/users",
        element: (
          <ProtectedRoute allowedRoles={["ADMIN"]}>
            <Users />
          </ProtectedRoute>
        ),
      },
      {
        path: "/change-password",
        element: (
          <ProtectedRoute allowedRoles={["ADMIN"]}>
            <ChangePassword />
          </ProtectedRoute>
        ),
      },
      {
        path: "/templates",
        element: (
          <ProtectedRoute allowedRoles={["ADMIN"]}>
            <Templates />
          </ProtectedRoute>
        ),
      },
    ],
  },
])

function App() {
  return <RouterProvider router={router} />
}

export default App