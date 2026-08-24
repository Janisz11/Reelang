import { useState } from "react";

export function AdminTokenGate({
  submitLabel = "Pokaż",
  onSubmit,
}: {
  submitLabel?: string;
  onSubmit: (token: string) => void;
}) {
  const [value, setValue] = useState("");

  return (
    <form
      className="center-box"
      onSubmit={(event) => {
        event.preventDefault();
        const trimmed = value.trim();
        if (trimmed) onSubmit(trimmed);
      }}
    >
      <p style={{ fontSize: 18, fontWeight: 700, margin: 0 }}>Admin token</p>
      <p className="muted" style={{ margin: 0, maxWidth: 420, textAlign: "center" }}>
        Token jest trzymany wyłącznie w sessionStorage tej karty i nigdy nie trafia do bundla
        aplikacji.
      </p>
      <input
        type="password"
        value={value}
        autoComplete="off"
        placeholder="X-Admin-Token"
        onChange={(event) => setValue(event.target.value)}
        style={{ padding: "10px 12px", borderRadius: 8, minWidth: 280 }}
      />
      <button className="btn btn--primary" type="submit" disabled={!value.trim()}>
        {submitLabel}
      </button>
    </form>
  );
}
