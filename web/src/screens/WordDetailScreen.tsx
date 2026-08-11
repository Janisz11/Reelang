import { useCallback, useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { api } from "../api/reelang";
import type { WordLookupResponse, WordResponse } from "../api/types";
import { wordProgress } from "../lib/format";
import { speak, ttsSupported } from "../lib/tts";
import { VolumeIcon } from "../components/Icons";
import { ErrorBox, LoadingBox, TopBar } from "../components/common";

interface Detail {
  word: WordResponse;
  lookup: WordLookupResponse | null;
}

function Flashcard({ detail }: { detail: Detail }) {
  const [flipped, setFlipped] = useState(false);
  const translation = detail.lookup?.translation ?? "";
  const definition = detail.lookup?.definition ?? detail.word.definition ?? "";

  return (
    <div style={{ padding: "0 16px" }}>
      <p className="section-title" style={{ fontSize: 17, margin: "0 0 10px" }}>
        Flashcard
      </p>
      <button
        onClick={() => setFlipped((value) => !value)}
        style={{ width: "100%", height: 160, perspective: 1000, display: "block" }}
      >
        <div
          style={{
            position: "relative",
            width: "100%",
            height: "100%",
            transformStyle: "preserve-3d",
            transform: flipped ? "rotateY(180deg)" : "none",
            transition: "transform 0.4s cubic-bezier(0.4, 0, 0.2, 1)",
          }}
        >
          <div
            style={{
              position: "absolute",
              inset: 0,
              backfaceVisibility: "hidden",
              borderRadius: 16,
              background: "var(--red)",
              color: "#fff",
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              gap: 8,
              boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
            }}
          >
            <span style={{ fontSize: 32, fontWeight: 800 }}>{detail.word.term}</span>
            <span style={{ fontSize: 12, opacity: 0.7, letterSpacing: 1 }}>{detail.word.language.toUpperCase()}</span>
            <span style={{ fontSize: 11, opacity: 0.5 }}>tap to flip</span>
          </div>
          <div
            style={{
              position: "absolute",
              inset: 0,
              backfaceVisibility: "hidden",
              transform: "rotateY(180deg)",
              borderRadius: 16,
              background: "var(--surface)",
              padding: 16,
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              gap: 8,
              boxShadow: "0 4px 12px rgba(0,0,0,0.15)",
            }}
          >
            <span style={{ fontSize: 28, fontWeight: 800, textAlign: "center" }}>
              {translation || "No translation available"}
            </span>
            <span className="muted" style={{ fontSize: 12, letterSpacing: 1 }}>
              EN
            </span>
            {definition && (
              <span className="muted" style={{ fontSize: 13, textAlign: "center" }}>
                {definition}
              </span>
            )}
          </div>
        </div>
      </button>
      <p className="muted" style={{ fontSize: 11, textAlign: "center", marginTop: 8 }}>
        {flipped ? "showing translation" : "showing original"}
      </p>
    </div>
  );
}

export function WordDetailScreen() {
  const { wordId = "" } = useParams();
  const [detail, setDetail] = useState<Detail | null>(null);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    setStatus("loading");
    try {
      const word = await api.getWord(wordId);
      const lookup = await api.lookupWord(word.term, word.language).catch(() => null);
      setDetail({ word, lookup });
      setStatus("ready");
    } catch (err) {
      setError((err as Error).message);
      setStatus("error");
    }
  }, [wordId]);

  useEffect(() => {
    void load();
  }, [load]);

  return (
    <div className="screen">
      <TopBar title="Back to feed" />
      {status === "loading" && <LoadingBox />}
      {status === "error" && <ErrorBox message={error} onRetry={() => void load()} />}
      {status === "ready" && detail && (
        <div className="screen screen--scroll">
          <div style={{ background: "var(--surface)", padding: 20 }}>
            <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
              <h1 style={{ margin: 0, fontSize: 36, fontWeight: 800 }}>{detail.word.term}</h1>
              {ttsSupported && (
                <button
                  className="icon-btn"
                  style={{ background: "var(--cream)", borderRadius: 12, width: 44, height: 44 }}
                  onClick={() => speak(detail.word.term, detail.word.language)}
                  aria-label="Pronounce"
                >
                  <VolumeIcon size={22} color="var(--red)" />
                </button>
              )}
            </div>

            <hr className="divider" style={{ margin: "16px 0 14px" }} />

            <p className="field__label" style={{ margin: "0 0 8px" }}>
              TRANSLATIONS
            </p>
            {detail.lookup?.translation ? (
              <span
                style={{
                  display: "inline-flex",
                  gap: 6,
                  alignItems: "center",
                  padding: "7px 12px",
                  borderRadius: 10,
                  background: "var(--cream)",
                  border: "1px solid var(--border)",
                }}
              >
                <span className="muted" style={{ fontSize: 11, fontWeight: 600 }}>
                  {detail.lookup.target_lang.toUpperCase()}
                </span>
                <span className="muted">·</span>
                <span style={{ fontSize: 14, fontWeight: 700 }}>{detail.lookup.translation}</span>
              </span>
            ) : (
              <p className="muted" style={{ margin: 0 }}>
                No translation available.
              </p>
            )}
          </div>

          <div style={{ height: 8 }} />
          <Flashcard detail={detail} />

          <div style={{ padding: 16 }}>
            <p className="section-title" style={{ fontSize: 17, margin: "0 0 10px" }}>
              Review status
            </p>
            <div className="card" style={{ padding: 16, display: "grid", gap: 8 }}>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span className="muted">Status</span>
                <span style={{ fontWeight: 600, textTransform: "capitalize" }}>{detail.word.status}</span>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span className="muted">Progress</span>
                <span style={{ fontWeight: 600 }}>
                  {Math.round(wordProgress(detail.word.repetitions, detail.word.status) * 100)}%
                </span>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span className="muted">Repetitions</span>
                <span style={{ fontWeight: 600 }}>
                  {detail.word.repetitions} · easiness {detail.word.easiness.toFixed(2)}
                </span>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span className="muted">Next review</span>
                <span style={{ fontWeight: 600 }}>
                  {detail.word.next_review ? new Date(detail.word.next_review).toLocaleDateString() : "Due now"}
                </span>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span className="muted">Language</span>
                <span style={{ fontWeight: 600 }}>{detail.word.language.toUpperCase()}</span>
              </div>
              <div style={{ display: "flex", justifyContent: "space-between" }}>
                <span className="muted">Saved</span>
                <span style={{ fontWeight: 600 }}>{new Date(detail.word.created_at).toLocaleDateString()}</span>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
