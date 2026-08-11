import { useState } from "react";
import type { CaptionSegment } from "../api/types";
import { cleanTerm } from "../lib/format";

export function activeCaptionAt(captions: CaptionSegment[], timeMs: number): CaptionSegment | undefined {
  return captions.find((caption) => timeMs >= caption.start_ms && timeMs <= caption.end_ms);
}

function ClickableWord({ word, onClick }: { word: string; onClick: (term: string) => void }) {
  const [flash, setFlash] = useState(false);
  const term = cleanTerm(word);

  return (
    <button
      onClick={(event) => {
        event.stopPropagation();
        if (!term) return;
        setFlash(true);
        window.setTimeout(() => setFlash(false), 400);
        onClick(term);
      }}
      style={{
        color: flash ? "var(--gold)" : "#fff",
        fontWeight: 700,
        fontSize: 16,
        padding: "0 1px",
        transition: "color 0.3s",
      }}
    >
      {word}
    </button>
  );
}

export function CaptionOverlay({
  caption,
  onWordClick,
}: {
  caption: CaptionSegment;
  onWordClick: (term: string) => void;
}) {
  const words = (caption.original_text ?? "").split(" ").filter(Boolean);
  if (words.length === 0) return null;

  return (
    <div
      style={{
        position: "absolute",
        left: 16,
        right: 16,
        bottom: 96,
        padding: "8px 12px",
        borderRadius: 8,
        background: "rgba(0,0,0,0.6)",
        textAlign: "center",
        zIndex: 5,
      }}
      onClick={(event) => event.stopPropagation()}
    >
      <div style={{ display: "flex", flexWrap: "wrap", justifyContent: "center" }}>
        {words.map((word, index) => (
          <ClickableWord key={`${word}-${index}`} word={word} onClick={onWordClick} />
        ))}
      </div>
      {caption.translated_text && (
        <p style={{ margin: "4px 0 0", color: "#d3d3d3", fontSize: 13 }}>{caption.translated_text}</p>
      )}
    </div>
  );
}
