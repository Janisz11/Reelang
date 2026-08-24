import { useCallback, useEffect, useState } from "react";
import { fetchLogs, type AppLogEntry, type LogLevel } from "../api/admin";
import { describeAdminError, useAdminToken } from "../lib/useAdminToken";
import { AdminTokenGate } from "../components/AdminTokenGate";
import { ErrorBox, LoadingBox } from "../components/common";

const PAGE_SIZE = 50;

const LEVEL_FILTERS: { value: LogLevel | "ALL"; label: string }[] = [
  { value: "ALL", label: "Wszystkie" },
  { value: "WARNING", label: "WARNING" },
  { value: "ERROR", label: "ERROR" },
  { value: "CRITICAL", label: "CRITICAL" },
];

function levelClass(level: string): string {
  const normalized = level.toUpperCase();
  if (normalized === "WARNING") return "log-badge log-badge--warning";
  if (normalized === "ERROR" || normalized === "CRITICAL") return "log-badge log-badge--error";
  return "log-badge";
}

function formatTimestamp(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleString("pl-PL", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

function LogRow({ entry }: { entry: AppLogEntry }) {
  const [expanded, setExpanded] = useState(false);
  const hasContext = entry.context !== null && Object.keys(entry.context).length > 0;

  return (
    <>
      <tr
        className={hasContext ? "log-row log-row--clickable" : "log-row"}
        onClick={hasContext ? () => setExpanded((open) => !open) : undefined}
      >
        <td>
          <span className={levelClass(entry.level)}>{entry.level}</span>
        </td>
        <td className="log-cell__logger">{entry.logger_name}</td>
        <td className="log-cell__message">
          {hasContext ? <span className="log-caret">{expanded ? "▾" : "▸"}</span> : null}
          {entry.message}
        </td>
        <td className="log-cell__time">{formatTimestamp(entry.created_at)}</td>
      </tr>
      {expanded && hasContext ? (
        <tr className="log-row log-row--context">
          <td colSpan={4}>
            <pre className="log-context">{JSON.stringify(entry.context, null, 2)}</pre>
          </td>
        </tr>
      ) : null}
    </>
  );
}

export function LogsView() {
  const { token, submitToken, clearToken } = useAdminToken();
  const [entries, setEntries] = useState<AppLogEntry[] | null>(null);
  const [level, setLevel] = useState<LogLevel | "ALL">("ALL");
  const [loading, setLoading] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const [exhausted, setExhausted] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async (activeToken: string, selected: LogLevel | "ALL") => {
    setLoading(true);
    setError(null);
    try {
      const page = await fetchLogs(activeToken, {
        level: selected === "ALL" ? undefined : selected,
        limit: PAGE_SIZE,
      });
      setEntries(page);
      setExhausted(page.length < PAGE_SIZE);
    } catch (err) {
      setEntries(null);
      setError(describeAdminError(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (token) void load(token, level);
  }, [token, level, load]);

  const loadMore = async () => {
    if (!entries || entries.length === 0) return;

    setLoadingMore(true);
    setError(null);
    try {
      const page = await fetchLogs(token, {
        level: level === "ALL" ? undefined : level,
        limit: PAGE_SIZE,
        before: entries[entries.length - 1].created_at,
      });
      setEntries([...entries, ...page]);
      setExhausted(page.length < PAGE_SIZE);
    } catch (err) {
      setError(describeAdminError(err));
    } finally {
      setLoadingMore(false);
    }
  };

  if (!token) return <AdminTokenGate submitLabel="Pokaż logi" onSubmit={submitToken} />;

  if (loading && !entries) return <LoadingBox />;

  if (error && !entries) {
    return (
      <div className="center-box">
        <ErrorBox message={error} onRetry={() => void load(token, level)} />
        <button
          className="btn btn--neutral btn--sm"
          onClick={() => {
            clearToken();
            setEntries(null);
            setError(null);
          }}
        >
          Zmień token
        </button>
      </div>
    );
  }

  if (!entries) return <LoadingBox />;

  return (
    <>
      <div className="admin-toolbar">
        <span className="muted">
          {entries.length === 0
            ? "Brak wpisów"
            : `${entries.length} wpisów${exhausted ? "" : "+"}`}
        </span>
        <span className="admin-toolbar__spacer" />
        {LEVEL_FILTERS.map((filter) => (
          <button
            key={filter.value}
            className={`btn btn--sm ${level === filter.value ? "btn--primary" : "btn--outline"}`}
            onClick={() => setLevel(filter.value)}
          >
            {filter.label}
          </button>
        ))}
        <button
          className="btn btn--outline btn--sm"
          onClick={() => void load(token, level)}
          disabled={loading}
        >
          Odśwież
        </button>
      </div>

      <div className="stats-scroll">
        {error ? <p className="muted stats-inline-error">{error}</p> : null}

        {entries.length === 0 ? (
          <p className="muted stats-empty">
            Brak logów dla wybranego filtru — to dobra wiadomość.
          </p>
        ) : (
          <>
            <div className="stats-table-wrap">
              <table className="stats-table log-table">
                <thead>
                  <tr>
                    <th>Poziom</th>
                    <th>Logger</th>
                    <th>Wiadomość</th>
                    <th>Czas</th>
                  </tr>
                </thead>
                <tbody>
                  {entries.map((entry) => (
                    <LogRow key={entry.id} entry={entry} />
                  ))}
                </tbody>
              </table>
            </div>

            {!exhausted ? (
              <div className="log-more">
                <button
                  className="btn btn--outline btn--sm"
                  onClick={() => void loadMore()}
                  disabled={loadingMore}
                >
                  {loadingMore ? "Ładowanie…" : "Załaduj więcej"}
                </button>
              </div>
            ) : null}
          </>
        )}
      </div>
    </>
  );
}
