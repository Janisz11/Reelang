import { useState } from "react";
import { useNavigate } from "react-router-dom";

export const availableLanguages = [
  { code: "es", name: "Spanish", flag: "🇪🇸" },
  { code: "fr", name: "French", flag: "🇫🇷" },
  { code: "de", name: "German", flag: "🇩🇪" },
  { code: "ja", name: "Japanese", flag: "🇯🇵" },
  { code: "it", name: "Italian", flag: "🇮🇹" },
  { code: "pt", name: "Portuguese", flag: "🇵🇹" },
];

const availableLevels = [
  { id: "beginner", label: "Beginner", emoji: "🌱" },
  { id: "intermediate", label: "Intermediate", emoji: "🚀" },
  { id: "advanced", label: "Advanced", emoji: "🏆" },
];

const PREFS_KEY = "reelang.preferences";

export interface Preferences {
  language: string;
  level: string;
}

export function loadPreferences(): Preferences | null {
  try {
    const raw = localStorage.getItem(PREFS_KEY);
    return raw ? (JSON.parse(raw) as Preferences) : null;
  } catch {
    return null;
  }
}

export function OnboardingScreen() {
  const navigate = useNavigate();
  const [language, setLanguage] = useState<string | null>(null);
  const [level, setLevel] = useState<string | null>(null);
  const canProceed = Boolean(language && level);

  function proceed() {
    if (!canProceed) return;
    localStorage.setItem(PREFS_KEY, JSON.stringify({ language, level }));
    navigate("/feed", { replace: true });
  }

  return (
    <div className="screen screen--scroll" style={{ padding: "48px 24px", textAlign: "center" }}>
      <div style={{ fontSize: 52, lineHeight: 1 }}>🌎</div>
      <h1 style={{ margin: "20px 0 8px", fontSize: 30, fontWeight: 900, lineHeight: 1.2 }}>
        Learn by watching
        <br />
        reels
      </h1>
      <p className="muted" style={{ margin: "0 0 32px", fontSize: 14 }}>
        Pick your target language to start your journey.
      </p>

      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12 }}>
        {availableLanguages.map((item) => {
          const selected = language === item.code;
          return (
            <button
              key={item.code}
              onClick={() => setLanguage(selected ? null : item.code)}
              style={{
                padding: "14px 8px",
                borderRadius: 12,
                background: selected ? "var(--red-tint)" : "var(--surface)",
                border: `${selected ? 2 : 1}px solid ${selected ? "var(--red)" : "var(--border)"}`,
                color: selected ? "var(--red)" : "var(--text-primary)",
                fontWeight: selected ? 600 : 400,
                fontSize: 14,
                transition: "all 0.2s",
              }}
            >
              {item.flag} {item.name}
            </button>
          );
        })}
      </div>

      <p style={{ textAlign: "left", margin: "32px 0 12px", fontSize: 16, fontWeight: 600 }}>Choose your level</p>

      <div style={{ display: "flex", gap: 10 }}>
        {availableLevels.map((item) => {
          const selected = level === item.id;
          return (
            <button
              key={item.id}
              onClick={() => setLevel(selected ? null : item.id)}
              style={{
                flex: 1,
                padding: "10px 4px",
                borderRadius: 50,
                background: selected ? "var(--red)" : "var(--surface)",
                border: `1px solid ${selected ? "var(--red)" : "var(--border)"}`,
                color: selected ? "#fff" : "var(--text-primary)",
                fontWeight: selected ? 600 : 400,
                fontSize: 12,
                whiteSpace: "nowrap",
                transition: "all 0.2s",
              }}
            >
              {item.emoji} {item.label}
            </button>
          );
        })}
      </div>

      <button
        className="btn btn--primary btn--full"
        style={{ marginTop: 40, height: 56, borderRadius: 16, fontSize: 16, fontWeight: 700 }}
        disabled={!canProceed}
        onClick={proceed}
      >
        Let&apos;s go! →
      </button>
    </div>
  );
}
