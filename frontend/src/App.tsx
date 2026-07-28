import { createBrowserRouter, RouterProvider } from "react-router"
import Login from "@/pages/Login"
import Dashboard from "@/pages/Dashboard"
import Users from "@/pages/Users"
import ChangePassword from "@/pages/ChangePassword"
import Templates from "@/pages/Templates"
import TemplateNew from "@/pages/TemplateNew"
import Settings from "@/pages/Settings"
import AuditLog from "@/pages/AuditLog"
import Upload from "@/pages/Upload"
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
      { path: "/upload", element: <Upload /> },
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
      {
        path: "/templates/new",
        element: (
          <ProtectedRoute allowedRoles={["ADMIN"]}>
            <TemplateNew />
          </ProtectedRoute>
        ),
      },
      {
        path: "/settings",
        element: (
          <ProtectedRoute allowedRoles={["ADMIN"]}>
            <Settings />
          </ProtectedRoute>
        ),
      },
      {
        path: "/audit-logs",
        element: (
          <ProtectedRoute allowedRoles={["ADMIN"]}>
            <AuditLog />
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