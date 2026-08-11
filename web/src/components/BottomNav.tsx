import { NavLink } from "react-router-dom";
import { BookIcon, HomeIcon, PersonIcon, SearchIcon } from "./Icons";
import { useWordsBadge } from "../lib/wordsBadge";

const items = [
  { to: "/feed", label: "Feed", Icon: HomeIcon },
  { to: "/search", label: "Search", Icon: SearchIcon },
  { to: "/words", label: "Words", Icon: BookIcon },
  { to: "/profile", label: "Profile", Icon: PersonIcon },
] as const;

export function BottomNav() {
  const { dueCount } = useWordsBadge();

  return (
    <nav className="bottom-nav">
      {items.map(({ to, label, Icon }) => (
        <NavLink
          key={to}
          to={to}
          className={({ isActive }) => `bottom-nav__item${isActive ? " bottom-nav__item--active" : ""}`}
        >
          {({ isActive }) => (
            <>
              <Icon size={24} filled={isActive} />
              <span>{label}</span>
              {to === "/words" && dueCount > 0 && (
                <span className="bottom-nav__badge">{dueCount > 99 ? "99+" : dueCount}</span>
              )}
            </>
          )}
        </NavLink>
      ))}
    </nav>
  );
}
