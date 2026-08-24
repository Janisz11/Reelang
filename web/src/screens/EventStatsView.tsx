import { useCallback, useEffect, useMemo, useState } from "react";
import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import {
  fetchEventStats,
  type EventStatsResponse,
  type EventStatsWindow,
  type EventTimeBucket,
  type ReelRateEntry,
} from "../api/admin";
import { describeAdminError, useAdminToken } from "../lib/useAdminToken";
import { AdminTokenGate } from "../components/AdminTokenGate";
import { ErrorBox, LoadingBox } from "../components/common";

const REFRESH_INTERVAL_MS = 30_000;

const SERIES_COLORS = [
  "#b22222",
  "#e53935",
  "#f4a300",
  "#4caf50",
  "#2f7fb8",
  "#8e6cc4",
  "#5b8c85",
];

const WINDOW_OPTIONS: { value: EventStatsWindow; label: string }[] = [
  { value: "24h", label: "Ostatnie 24h" },
  { value: "14d", label: "Ostatnie 14 dni" },
];

const EMPTY_MESSAGE = "Brak danych — klienci jeszcze nie wysyłają eventów.";

function formatBucket(iso: string, window: EventStatsWindow): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return window === "24h"
    ? date.toLocaleTimeString("pl-PL", { hour: "2-digit", minute: "2-digit" })
    : date.toLocaleDateString("pl-PL", { day: "2-digit", month: "2-digit" });
}

function formatTimestamp(iso: string): string {
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return iso;
  return date.toLocaleString("pl-PL", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
  });
}

interface PivotedBucket {
  label: string;
  [eventType: string]: string | number;
}

function pivot(rows: EventTimeBucket[], window: EventStatsWindow) {
  const byBucket = new Map<string, PivotedBucket>();
  const eventTypes = new Set<string>();

  for (const row of rows) {
    eventTypes.add(row.event_type);
    const existing = byBucket.get(row.bucket);
    if (existing) {
      existing[row.event_type] = ((existing[row.event_type] as number) ?? 0) + row.count;
    } else {
      byBucket.set(row.bucket, {
        label: formatBucket(row.bucket, window),
        [row.event_type]: row.count,
      });
    }
  }

  const data = [...byBucket.entries()]
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([, value]) => value);

  return { data, eventTypes: [...eventTypes].sort() };
}

function EmptySection({ message = EMPTY_MESSAGE }: { message?: string }) {
  return <p className="muted stats-empty">{message}</p>;
}

function RateTable({ title, entries }: { title: string; entries: ReelRateEntry[] }) {
  return (
    <section className="stats-card">
      <h2 className="stats-card__title">{title}</h2>
      {entries.length === 0 ? (
        <EmptySection />
      ) : (
        <div className="stats-table-wrap">
          <table className="stats-table">
            <thead>
              <tr>
                <th>Rolka</th>
                <th className="stats-table__num">Wynik</th>
                <th className="stats-table__num">Impresje</th>
              </tr>
            </thead>
            <tbody>
              {entries.map((entry) => (
                <tr key={entry.reel_id}>
                  <td title={entry.reel_id}>{entry.title ?? entry.reel_id}</td>
                  <td className="stats-table__num">{(entry.rate * 100).toFixed(1)}%</td>
                  <td className="stats-table__num stats-table__muted">{entry.impressions}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

export function EventStatsView() {
  const { token, submitToken, clearToken } = useAdminToken();
  const [stats, setStats] = useState<EventStatsResponse | null>(null);
  const [window_, setWindow] = useState<EventStatsWindow>("24h");
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (activeToken: string, selected: EventStatsWindow, silent = false) => {
      if (!silent) setLoading(true);
      setError(null);
      try {
        setStats(await fetchEventStats(activeToken, selected));
      } catch (err) {
        if (!silent) setStats(null);
        setError(describeAdminError(err));
      } finally {
        if (!silent) setLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    if (!token) return;

    void load(token, window_);
    const timer = globalThis.setInterval(
      () => void load(token, window_, true),
      REFRESH_INTERVAL_MS,
    );
    return () => globalThis.clearInterval(timer);
  }, [token, window_, load]);

  const series = useMemo(
    () => pivot(stats?.time_series ?? [], stats?.window ?? window_),
    [stats, window_],
  );

  if (!token) return <AdminTokenGate submitLabel="Pokaż statystyki" onSubmit={submitToken} />;

  if (loading && !stats) return <LoadingBox />;

  if (error && !stats) {
    return (
      <div className="center-box">
        <ErrorBox message={error} onRetry={() => void load(token, window_)} />
        <button
          className="btn btn--neutral btn--sm"
          onClick={() => {
            clearToken();
            setStats(null);
            setError(null);
          }}
        >
          Zmień token
        </button>
      </div>
    );
  }

  if (!stats) return <LoadingBox />;

  const isCompletelyEmpty =
    stats.time_series.length === 0 &&
    stats.top_completion.length === 0 &&
    stats.top_skip.length === 0 &&
    stats.recent_events.length === 0;

  return (
    <>
      <div className="admin-toolbar">
        <span className="muted">
          {isCompletelyEmpty
            ? EMPTY_MESSAGE
            : `${stats.recent_events.length} ostatnich eventów · odświeżanie co 30 s`}
        </span>
        <span className="admin-toolbar__spacer" />
        {WINDOW_OPTIONS.map((option) => (
          <button
            key={option.value}
            className={`btn btn--sm ${
              window_ === option.value ? "btn--primary" : "btn--outline"
            }`}
            onClick={() => setWindow(option.value)}
          >
            {option.label}
          </button>
        ))}
        <button
          className="btn btn--outline btn--sm"
          onClick={() => void load(token, window_)}
          disabled={loading}
        >
          Odśwież
        </button>
      </div>

      <div className="stats-scroll">
        {error ? <p className="muted stats-inline-error">{error}</p> : null}

        <section className="stats-card">
          <h2 className="stats-card__title">Eventy w czasie</h2>
          {series.data.length === 0 ? (
            <EmptySection />
          ) : (
            <div className="stats-chart">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={series.data}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#e8ddd5" />
                  <XAxis dataKey="label" tick={{ fontSize: 12 }} stroke="#888888" />
                  <YAxis allowDecimals={false} tick={{ fontSize: 12 }} stroke="#888888" />
                  <Tooltip />
                  <Legend />
                  {series.eventTypes.map((eventType, index) => (
                    <Bar
                      key={eventType}
                      dataKey={eventType}
                      stackId="events"
                      fill={SERIES_COLORS[index % SERIES_COLORS.length]}
                    />
                  ))}
                </BarChart>
              </ResponsiveContainer>
            </div>
          )}
        </section>

        <div className="stats-grid">
          <RateTable title="Top 10 — completion rate" entries={stats.top_completion} />
          <RateTable title="Top 10 — skip rate" entries={stats.top_skip} />
        </div>

        <section className="stats-card">
          <h2 className="stats-card__title">Ostatnie eventy</h2>
          {stats.recent_events.length === 0 ? (
            <EmptySection />
          ) : (
            <div className="stats-table-wrap">
              <table className="stats-table">
                <thead>
                  <tr>
                    <th>Typ</th>
                    <th>Rolka</th>
                    <th>Platforma</th>
                    <th>Czas</th>
                  </tr>
                </thead>
                <tbody>
                  {stats.recent_events.map((event) => (
                    <tr key={event.event_id}>
                      <td>{event.event_type}</td>
                      <td>
                        <code className="stats-code" title={event.reel_id}>
                          {event.reel_id.slice(0, 8)}
                        </code>
                      </td>
                      <td className="stats-table__muted">{event.platform}</td>
                      <td className="stats-table__muted">
                        {formatTimestamp(event.server_timestamp)}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </>
  );
}
