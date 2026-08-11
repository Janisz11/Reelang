import { useCallback, useEffect, useState } from "react";
import { api } from "../api/reelang";
import type { ActivityStatsResponse } from "../api/types";
import { FlameIcon, PlayCircleIcon } from "../components/Icons";
import { ErrorBox, LoadingBox, ProgressBar, TopBar } from "../components/common";

function QuickStat({
  icon,
  tint,
  value,
  unit,
  label,
}: {
  icon: React.ReactNode;
  tint: string;
  value: string;
  unit?: string;
  label: string;
}) {
  return (
    <div className="card" style={{ flex: 1, padding: 16 }}>
      <div
        style={{
          width: 36,
          height: 36,
          borderRadius: 10,
          background: `${tint}1F`,
          display: "grid",
          placeItems: "center",
        }}
      >
        {icon}
      </div>
      <div style={{ display: "flex", alignItems: "baseline", gap: 4, marginTop: 12 }}>
        <span style={{ fontSize: 28, fontWeight: 800 }}>{value}</span>
        {unit && <span className="muted" style={{ fontWeight: 500, fontSize: 14 }}>{unit}</span>}
      </div>
      <div className="muted" style={{ fontSize: 12 }}>
        {label}
      </div>
    </div>
  );
}

function BarChart({ data }: { data: ActivityStatsResponse["weekly_activity"] }) {
  return (
    <div>
      <div style={{ display: "flex", alignItems: "flex-end", height: 110, gap: 0 }}>
        {data.map((stat, index) => (
          <div key={`${stat.day}-${index}`} style={{ flex: 1, display: "grid", placeItems: "center", height: "100%" }}>
            <div
              style={{
                width: "50%",
                height: "100%",
                borderRadius: 6,
                background: "rgba(178,34,34,0.12)",
                display: "flex",
                alignItems: "flex-end",
              }}
            >
              <div
                style={{
                  width: "100%",
                  height: `${Math.max(0, Math.min(1, stat.value)) * 100}%`,
                  borderRadius: 6,
                  background: "var(--red)",
                  transition: "height 0.4s",
                }}
              />
            </div>
          </div>
        ))}
      </div>
      <div style={{ display: "flex", marginTop: 8 }}>
        {data.map((stat, index) => (
          <div
            key={`${stat.day}-label-${index}`}
            className="muted"
            style={{ flex: 1, textAlign: "center", fontSize: 11, fontWeight: 500 }}
          >
            {stat.day}
          </div>
        ))}
      </div>
    </div>
  );
}

export function StatsScreen() {
  const [stats, setStats] = useState<ActivityStatsResponse | null>(null);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setStatus("loading");
    try {
      setStats(await api.getMyStats());
      setStatus("ready");
    } catch (err) {
      setError((err as Error).message);
      setStatus("error");
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="screen">
      <TopBar title="Learning Stats" />

      {status === "loading" && <LoadingBox />}
      {status === "error" && <ErrorBox message={error} onRetry={() => void load()} />}

      {status === "ready" && stats && (
        <div className="screen screen--scroll" style={{ padding: 16, gap: 12 }}>
          <div
            style={{
              background: "var(--red)",
              borderRadius: 16,
              padding: 24,
              color: "#fff",
              textAlign: "center",
            }}
          >
            <div style={{ fontSize: 13, fontWeight: 600, opacity: 0.8, letterSpacing: 0.4 }}>Vocabulary Mastered</div>
            <div style={{ fontSize: 56, fontWeight: 800, letterSpacing: -1, margin: "8px 0 4px" }}>
              {stats.vocabulary_mastered}
            </div>
            <div style={{ fontSize: 12, opacity: 0.7 }}>words learned across all languages</div>
          </div>

          <div style={{ display: "flex", gap: 12, marginTop: 12 }}>
            <QuickStat
              icon={<FlameIcon size={20} color="#FF6B35" />}
              tint="#FF6B35"
              value={String(stats.streak_days)}
              unit="Day"
              label="Streak"
            />
            <QuickStat
              icon={<PlayCircleIcon size={20} color="#4682B4" />}
              tint="#4682B4"
              value={`${stats.hours_watched}h`}
              label="Hours Watched"
            />
          </div>

          <div className="card" style={{ padding: 20, marginTop: 12 }}>
            <p className="section-title" style={{ margin: 0 }}>
              Weekly Activity
            </p>
            <p className="muted" style={{ margin: "4px 0 20px", fontSize: 12 }}>
              Minutes studied per day
            </p>
            <BarChart data={stats.weekly_activity} />
          </div>

          <div className="card" style={{ padding: 20, marginTop: 12 }}>
            <p className="section-title" style={{ margin: "0 0 16px" }}>
              Target Languages
            </p>
            {stats.target_languages.length === 0 && (
              <p className="muted" style={{ margin: 0 }}>
                Save a few words to see your language breakdown.
              </p>
            )}
            {stats.target_languages.map((lang, index) => (
              <div key={lang.name}>
                {index > 0 && <hr className="divider" style={{ margin: "14px 0" }} />}
                <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
                  <span style={{ fontSize: 24 }}>{lang.flag}</span>
                  <div style={{ flex: 1 }}>
                    <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                      <span style={{ fontSize: 14, fontWeight: 600 }}>{lang.name}</span>
                      <span style={{ fontSize: 13, fontWeight: 700, color: "var(--red)" }}>{lang.percent}%</span>
                    </div>
                    <div style={{ marginTop: 6 }}>
                      <ProgressBar value={lang.progress} />
                    </div>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
