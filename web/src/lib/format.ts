/** Mirrors formatCount / levelColor / bgColorsFor / sceneEmojiFor from the Android app. */

export function formatCount(n: number): string {
  if (n >= 1_000_000) return `${Math.floor(n / 1_000_000)}.${Math.floor((n % 1_000_000) / 100_000)}M`;
  if (n >= 1_000) return `${Math.floor(n / 1_000)}.${Math.floor((n % 1_000) / 100)}K`;
  return String(n);
}

export function levelColor(level: string | null | undefined): string {
  switch (level) {
    case "A1":
    case "A2":
      return "#43A047";
    case "B1":
    case "B2":
      return "#1E88E5";
    case "C1":
    case "C2":
      return "#8E24AA";
    default:
      return "#757575";
  }
}

export function bgGradientFor(language: string): string {
  const stops: Record<string, [string, string]> = {
    es: ["#1A1A2E", "#16213E"],
    fr: ["#2D1B1B", "#4A1A00"],
    ja: ["#0D1B2A", "#1B263B"],
    de: ["#1A0A2E", "#2D1B4E"],
    it: ["#1A1500", "#3D2B00"],
  };
  const [from, to] = stops[language.toLowerCase()] ?? ["#1A1A1A", "#2D2D2D"];
  return `linear-gradient(to bottom, ${from}, ${to})`;
}

export function bgColorFor(language: string): string {
  const colors: Record<string, string> = {
    es: "#1A1A2E",
    fr: "#2D1B1B",
    ja: "#0D1B2A",
    de: "#1A0A2E",
    it: "#1A1500",
  };
  return colors[language.toLowerCase()] ?? "#1A1A1A";
}

export function sceneEmojiFor(language: string): string {
  const emoji: Record<string, string> = {
    es: "☀️",
    fr: "🥐",
    ja: "🚉",
    de: "🎵",
    it: "🏛️",
  };
  return emoji[language.toLowerCase()] ?? "🌍";
}

export function initialsFrom(value: string): string {
  const trimmed = value.trim();
  if (!trimmed) return "U";
  const parts = trimmed.split(/\s+/);
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
  return trimmed.slice(0, 2).toUpperCase();
}

export const cleanTerm = (word: string) => word.trim().replace(/^[.,!?;:"'«»¿¡()]+|[.,!?;:"'«»¿¡()]+$/g, "");

/**
 * The API exposes no progress field; words.py promotes a word to "mastered"
 * once repetitions reaches 3, so that is the natural denominator.
 */
export const MASTERY_REPETITIONS = 3;

export function wordProgress(repetitions: number, status: string): number {
  if (status.toLowerCase() === "mastered") return 1;
  return Math.min(repetitions / MASTERY_REPETITIONS, 1);
}
