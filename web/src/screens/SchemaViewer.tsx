import { useCallback, useEffect, useRef, useState } from "react";
import mermaid from "mermaid";
import { ApiError } from "../api/client";
import {
  fetchSchema,
  readStoredAdminToken,
  storeAdminToken,
  type SchemaResponse,
} from "../api/admin";
import { buildErDiagram } from "../lib/erDiagram";
import { ErrorBox, LoadingBox, TopBar } from "../components/common";

mermaid.initialize({ startOnLoad: false, securityLevel: "strict", theme: "default" });

function TokenGate({ onSubmit }: { onSubmit: (token: string) => void }) {
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
        Token jest trzymany wyłącznie w sessionStorage tej karty i nigdy nie trafia do
        bundla aplikacji.
      </p>
      <input
        type="password"
        value={value}
        autoComplete="off"
        placeholder="X-Admin-Token"
        onChange={(event) => setValue(event.target.value)}
        style={{ padding: "10px 12px", borderRadius: 8, minWidth: 280 }}
      />
      <button className="modal__action" type="submit" disabled={!value.trim()}>
        Pokaż schemat
      </button>
    </form>
  );
}

export function SchemaViewer() {
  const [token, setToken] = useState(readStoredAdminToken);
  const [schema, setSchema] = useState<SchemaResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const diagramRef = useRef<HTMLDivElement | null>(null);

  const load = useCallback(async (activeToken: string) => {
    setLoading(true);
    setError(null);
    try {
      setSchema(await fetchSchema(activeToken));
    } catch (err) {
      setSchema(null);
      if (err instanceof ApiError && (err.status === 401 || err.status === 403)) {
        setError("Token odrzucony przez serwer (401/403). Sprawdź ADMIN_TOKEN.");
      } else if (err instanceof ApiError) {
        setError(`Błąd serwera (${err.status}): ${err.message}`);
      } else {
        setError("Nie udało się połączyć z API.");
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (token) void load(token);
  }, [token, load]);

  useEffect(() => {
    if (!schema || !diagramRef.current) return;

    let cancelled = false;
    const container = diagramRef.current;
    const definition = buildErDiagram(schema.tables);

    mermaid
      .render(`schema-er-${Date.now()}`, definition)
      .then(({ svg }) => {
        if (!cancelled) container.innerHTML = svg;
      })
      .catch(() => {
        if (!cancelled) {
          container.innerHTML = "";
          setError("Nie udało się wyrenderować diagramu.");
        }
      });

    return () => {
      cancelled = true;
    };
  }, [schema]);

  const changeToken = () => {
    storeAdminToken("");
    setToken("");
    setSchema(null);
    setError(null);
  };

  const submitToken = (next: string) => {
    storeAdminToken(next);
    setToken(next);
  };

  if (!token) {
    return (
      <>
        <TopBar title="Schemat bazy" />
        <TokenGate onSubmit={submitToken} />
      </>
    );
  }

  return (
    <>
      <TopBar title="Schemat bazy">
        <button
          className="icon-btn"
          onClick={() => void load(token)}
          disabled={loading}
          aria-label="Odśwież"
        >
          Odśwież
        </button>
      </TopBar>

      {loading && <LoadingBox />}

      {!loading && error && (
        <div className="center-box">
          <ErrorBox message={error} onRetry={() => void load(token)} />
          <button className="modal__action modal__action--muted" onClick={changeToken}>
            Zmień token
          </button>
        </div>
      )}

      {!loading && !error && schema && (
        <div style={{ padding: 12 }}>
          <p className="muted" style={{ marginTop: 0 }}>
            {schema.tables.length} tabel — struktura odczytana na żywo z bazy.
          </p>
          <div ref={diagramRef} style={{ overflowX: "auto" }} />
        </div>
      )}
    </>
  );
}
