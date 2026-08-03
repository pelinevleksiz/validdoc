import { lazy, Suspense, type ReactNode } from "react"
import { createBrowserRouter, RouterProvider } from "react-router"
import ProtectedRoute from "@/components/ProtectedRoute"
import ErrorBoundary from "@/components/ErrorBoundary"
import AppLayout from "@/components/layout/AppLayout"
import LoadingState from "@/components/ui/loading-state"
import NotFound from "@/pages/NotFound"

const Login = lazy(() => import("@/pages/Login"))
const Dashboard = lazy(() => import("@/pages/Dashboard"))
const Users = lazy(() => import("@/pages/Users"))
const ChangePassword = lazy(() => import("@/pages/ChangePassword"))
const Templates = lazy(() => import("@/pages/Templates"))
const TemplateNew = lazy(() => import("@/pages/TemplateNew"))
const Settings = lazy(() => import("@/pages/Settings"))
const AuditLog = lazy(() => import("@/pages/AuditLog"))
const Upload = lazy(() => import("@/pages/Upload"))
const DocumentsList = lazy(() => import("@/pages/DocumentsList"))
const DocumentDetail = lazy(() => import("@/pages/DocumentDetail"))
const ReviewQueue = lazy(() => import("@/pages/ReviewQueue"))

function withSuspense(element: ReactNode) {
  return <Suspense fallback={<LoadingState />}>{element}</Suspense>
}

const router = createBrowserRouter([
  { path: "/", element: withSuspense(<Login />) },
  {
    element: (
      <ProtectedRoute>
        <AppLayout />
      </ProtectedRoute>
    ),
    children: [
      { path: "/dashboard", element: withSuspense(<Dashboard />) },
      { path: "/upload", element: withSuspense(<Upload />) },
      { path: "/documents", element: withSuspense(<DocumentsList />) },
      { path: "/documents/:id", element: withSuspense(<DocumentDetail />) },
      { path: "/review-queue", element: withSuspense(<ReviewQueue />) },
      {
        path: "/users",
        element: (
          <ProtectedRoute allowedRoles={["ADMIN"]}>
            {withSuspense(<Users />)}
          </ProtectedRoute>
        ),
      },
      {
        path: "/change-password",
        element: (
          <ProtectedRoute allowedRoles={["ADMIN"]}>
            {withSuspense(<ChangePassword />)}
          </ProtectedRoute>
        ),
      },
      {
        path: "/templates",
        element: (
          <ProtectedRoute allowedRoles={["ADMIN"]}>
            {withSuspense(<Templates />)}
          </ProtectedRoute>
        ),
      },
      {
        path: "/templates/new",
        element: (
          <ProtectedRoute allowedRoles={["ADMIN"]}>
            {withSuspense(<TemplateNew />)}
          </ProtectedRoute>
        ),
      },
      {
        path: "/settings",
        element: (
          <ProtectedRoute allowedRoles={["ADMIN"]}>
            {withSuspense(<Settings />)}
          </ProtectedRoute>
        ),
      },
      {
        path: "/audit-logs",
        element: (
          <ProtectedRoute allowedRoles={["ADMIN"]}>
            {withSuspense(<AuditLog />)}
          </ProtectedRoute>
        ),
      },
    ],
  },
  { path: "*", element: <NotFound /> },
])

function App() {
  return (
    <ErrorBoundary>
      <RouterProvider router={router} />
    </ErrorBoundary>
  )
}

export default App