import type { ComponentType } from "react";
import { NavLink, Outlet, useLocation } from "react-router-dom";
import { DeploymentStatusWidget } from "./DeploymentStatusWidget";
import { DatabaseIcon } from "./Icons";

interface IconProps {
  size?: number;
  color?: string;
  filled?: boolean;
}

interface AdminNavItem {
  label: string;
  path: string;
  icon: ComponentType<IconProps>;
}

export const ADMIN_NAV: AdminNavItem[] = [
  { label: "Schemat bazy", path: "/admin/schema", icon: DatabaseIcon },
];

function sectionTitle(pathname: string): string {
  const match = ADMIN_NAV.find(
    (item) => pathname === item.path || pathname.startsWith(`${item.path}/`),
  );
  return match?.label ?? "Panel administracyjny";
}

export function AdminLayout() {
  const { pathname } = useLocation();

  return (
    <div className="admin">
      <aside className="admin__sidebar">
        <div className="admin__brand">
          ReeLang <span className="admin__brand-tag">admin</span>
        </div>
        <nav className="admin__nav">
          {ADMIN_NAV.map(({ label, path, icon: Icon }) => (
            <NavLink
              key={path}
              to={path}
              className={({ isActive }) =>
                `admin__nav-item${isActive ? " admin__nav-item--active" : ""}`
              }
            >
              <Icon size={18} />
              {label}
            </NavLink>
          ))}
        </nav>
        <div className="admin__sidebar-foot">
          <NavLink to="/feed" className="admin__nav-item admin__nav-item--quiet">
            Wróć do aplikacji
          </NavLink>
        </div>
      </aside>

      <div className="admin__main">
        <header className="admin__header">
          <h1 className="admin__title">{sectionTitle(pathname)}</h1>
          <span className="admin__header-spacer" />
          <DeploymentStatusWidget />
        </header>
        <div className="admin__content">
          <Outlet />
        </div>
      </div>
    </div>
  );
}
