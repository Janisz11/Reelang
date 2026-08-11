import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/reelang";
import type { WordResponse } from "../api/types";
import { speak } from "../lib/tts";
import { useWordsBadge } from "../lib/wordsBadge";
import { CheckIcon, CloseIcon } from "../components/Icons";
import { EmptyBox, LoadingBox, ProgressBar, TopBar } from "../components/common";

function shuffle<T>(items: T[]): T[] {
  const copy = [...items];
  for (let i = copy.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [copy[i], copy[j]] = [copy[j], copy[i]];
  }
  return copy;
}

function splitDefinition(definition: string | null) {
  if (!definition) return { translation: null, body: null };
  if (definition.startsWith("Translation: ")) {
    return { translation: definition.slice("Translation: ".length), body: null };
  }
  return { translation: null, body: definition };
}

function Flashcard({ card }: { card: WordResponse }) {
  const [flipped, setFlipped] = useState(false);
  const { translation, body } = splitDefinition(card.definition);

  useEffect(() => {
    setFlipped(false);
    const id = window.setTimeout(() => speak(card.term, card.language), 300);
    return () => window.clearTimeout(id);
  }, [card.id, card.term, card.language]);

  return (
    <button onClick={() => setFlipped((value) => !value)} style={{ width: "100%", height: 280, perspective: 1200, display: "block" }}>
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
            borderRadius: 20,
            background: "var(--red)",
            color: "#fff",
            padding: 24,
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            boxShadow: "0 8px 24px rgba(0,0,0,0.18)",
          }}
        >
          <span style={{ fontSize: 12, opacity: 0.6, letterSpacing: 1 }}>{card.language.toUpperCase()}</span>
          <span style={{ fontSize: 40, fontWeight: 800, margin: "12px 0 24px", textAlign: "center" }}>{card.term}</span>
          <span style={{ fontSize: 12, opacity: 0.5 }}>tap to reveal</span>
        </div>

        <div
          style={{
            position: "absolute",
            inset: 0,
            backfaceVisibility: "hidden",
            transform: "rotateY(180deg)",
            borderRadius: 20,
            background: "var(--surface)",
            padding: 24,
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            justifyContent: "center",
            boxShadow: "0 8px 24px rgba(0,0,0,0.18)",
          }}
        >
          {translation ? (
            <>
              <span className="muted" style={{ fontSize: 12, letterSpacing: 1 }}>
                EN
              </span>
              <span style={{ fontSize: 36, fontWeight: 800, marginTop: 8, textAlign: "center" }}>{translation}</span>
            </>
          ) : body ? (
            <span style={{ fontSize: 16, textAlign: "center", lineHeight: 1.5 }}>{body}</span>
          ) : (
            <span className="muted" style={{ fontSize: 16 }}>
              No translation available
            </span>
          )}
        </div>
      </div>
    </button>
  );
}

export function PracticeScreen() {
  const navigate = useNavigate();
  const { refresh: refreshBadge } = useWordsBadge();

  const [cards, setCards] = useState<WordResponse[]>([]);
  const [index, setIndex] = useState(0);
  const [loading, setLoading] = useState(true);
  const [complete, setComplete] = useState(false);
  const [known, setKnown] = useState(0);
  const [unknown, setUnknown] = useState(0);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setCards(shuffle(await api.listWords()));
    } catch {
      setCards([]);
    }
    setLoading(false);
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  function answer(quality: number) {
    const card = cards[index];
    if (!card) return;

    void api
      .reviewWord(card.id, quality)
      .then(() => refreshBadge())
      .catch(() => undefined);

    if (quality >= 3) setKnown((value) => value + 1);
    else setUnknown((value) => value + 1);

    if (index + 1 >= cards.length) setComplete(true);
    else setIndex(index + 1);
  }

  function restart() {
    setIndex(0);
    setKnown(0);
    setUnknown(0);
    setComplete(false);
    setCards((prev) => shuffle(prev));
  }

  const total = cards.length;

  return (
    <div className="screen">
      <TopBar title="Practice">
        {!complete && total > 0 && (
          <span className="muted">
            {index + 1}/{total}
          </span>
        )}
      </TopBar>
      {!complete && total > 0 && <ProgressBar value={(index + 1) / total} />}

      {loading && <LoadingBox />}

      {!loading && total === 0 && (
        <EmptyBox
          title="No words to practice yet!"
          subtitle="Save words from the feed to start practicing."
          action={
            <button className="btn btn--primary" onClick={() => navigate("/feed")}>
              Go to Feed
            </button>
          }
        />
      )}

      {!loading && total > 0 && !complete && (
        <div className="screen" style={{ padding: 24, justifyContent: "center", gap: 40 }}>
          <Flashcard card={cards[index]} />
          <div style={{ display: "flex", gap: 16 }}>
            <button className="btn btn--neutral" style={{ flex: 1 }} onClick={() => answer(1)}>
              <CloseIcon size={18} />
              Don&apos;t know
            </button>
            <button className="btn btn--primary" style={{ flex: 1 }} onClick={() => answer(4)}>
              <CheckIcon size={18} />
              I know it
            </button>
          </div>
        </div>
      )}

      {complete && (
        <div className="screen" style={{ padding: 32, justifyContent: "center", gap: 32, textAlign: "center" }}>
          <h2 style={{ margin: 0, fontSize: 24, fontWeight: 800 }}>Session Complete!</h2>

          <div className="card" style={{ padding: 24 }}>
            <div style={{ display: "flex", justifyContent: "space-evenly" }}>
              <div>
                <div style={{ fontSize: 40, fontWeight: 800, color: "var(--green)" }}>{known}</div>
                <div className="muted">Known</div>
              </div>
              <div>
                <div style={{ fontSize: 40, fontWeight: 800, color: "var(--red)" }}>{unknown}</div>
                <div className="muted">To review</div>
              </div>
            </div>
            <div style={{ marginTop: 16 }}>
              <ProgressBar value={total ? known / total : 0} color="var(--green)" />
            </div>
            <p className="muted" style={{ marginBottom: 0 }}>
              {total ? Math.round((known / total) * 100) : 0}% mastered
            </p>
          </div>

          <div style={{ display: "grid", gap: 12 }}>
            <button className="btn btn--primary btn--full" onClick={restart}>
              Practice Again
            </button>
            <button className="btn btn--outline btn--full" onClick={() => navigate("/words")}>
              Back to Words
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
