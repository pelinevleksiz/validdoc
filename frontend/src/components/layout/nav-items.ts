export interface NavItem {
  labelKey: string
  path: string
  roles?: string[]
}

export const NAV_ITEMS: NavItem[] = [
  { labelKey: "nav.dashboard", path: "/dashboard" },
  { labelKey: "nav.upload", path: "/upload" },
  { labelKey: "nav.documents", path: "/documents" },
  { labelKey: "nav.reviewQueue", path: "/review-queue" },
  { labelKey: "nav.templates", path: "/templates", roles: ["ADMIN"] },
  { labelKey: "nav.users", path: "/users", roles: ["ADMIN"] },
  { labelKey: "nav.auditLogs", path: "/audit-logs", roles: ["ADMIN"] },
  { labelKey: "nav.settings", path: "/settings", roles: ["ADMIN"] },
  { labelKey: "nav.changePassword", path: "/change-password", roles: ["ADMIN"] },
]