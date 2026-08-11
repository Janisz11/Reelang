import type { ReactNode } from "react";
import { useNavigate } from "react-router-dom";
import { BackIcon } from "./Icons";

export function Spinner({ light = false, small = false }: { light?: boolean; small?: boolean }) {
  return <div className={`spinner${light ? " spinner--light" : ""}${small ? " spinner--sm" : ""}`} />;
}

export function LoadingBox() {
  return (
    <div className="center-box">
      <Spinner />
    </div>
  );
}

export function ErrorBox({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div className="center-box">
      <p className="muted">{message}</p>
      {onRetry && (
        <button className="modal__action" onClick={onRetry}>
          Retry
        </button>
      )}
    </div>
  );
}

export function EmptyBox({ title, subtitle, action }: { title: string; subtitle?: string; action?: ReactNode }) {
  return (
    <div className="center-box">
      <p style={{ fontSize: 18, fontWeight: 700, margin: 0 }}>{title}</p>
      {subtitle && <p className="muted" style={{ margin: 0 }}>{subtitle}</p>}
      {action}
    </div>
  );
}

export function TopBar({ title, children }: { title: string; children?: ReactNode }) {
  const navigate = useNavigate();
  return (
    <div className="topbar">
      <button className="icon-btn" onClick={() => navigate(-1)} aria-label="Back">
        <BackIcon size={22} />
      </button>
      <span className="topbar__title">{title}</span>
      <span className="topbar__spacer" />
      {children}
    </div>
  );
}

export function Tabs({
  tabs,
  selected,
  onSelect,
}: {
  tabs: string[];
  selected: number;
  onSelect: (index: number) => void;
}) {
  return (
    <div className="tabs">
      {tabs.map((tab, index) => (
        <button
          key={tab}
          className={`tabs__tab${index === selected ? " tabs__tab--active" : ""}`}
          onClick={() => onSelect(index)}
        >
          {tab}
        </button>
      ))}
    </div>
  );
}

export function Modal({
  title,
  children,
  confirmLabel,
  onConfirm,
  onDismiss,
}: {
  title: string;
  children: ReactNode;
  confirmLabel: string;
  onConfirm: () => void;
  onDismiss: () => void;
}) {
  return (
    <div className="modal-backdrop" onClick={onDismiss}>
      <div className="modal" onClick={(event) => event.stopPropagation()}>
        <p className="modal__title">{title}</p>
        {children}
        <div className="modal__actions">
          <button className="modal__action modal__action--muted" onClick={onDismiss}>
            Cancel
          </button>
          <button className="modal__action" onClick={onConfirm}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  );
}

export function ProgressBar({ value, color }: { value: number; color?: string }) {
  const pct = Math.max(0, Math.min(1, value)) * 100;
  return (
    <div className="progress" style={color ? { background: `${color}26` } : undefined}>
      <div className="progress__bar" style={{ width: `${pct}%`, background: color }} />
    </div>
  );
}

export function Avatar({ initials, size = 44, color = "var(--red)" }: { initials: string; size?: number; color?: string }) {
  return (
    <div
      style={{
        width: size,
        height: size,
        borderRadius: "50%",
        background: color,
        color: "#fff",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        fontWeight: 800,
        fontSize: size * 0.36,
        flexShrink: 0,
      }}
    >
      {initials}
    </div>
  );
}
