import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/reelang";
import type { WordResponse } from "../api/types";
import { wordProgress } from "../lib/format";
import { speak, ttsSupported } from "../lib/tts";
import { useWordsBadge } from "../lib/wordsBadge";
import { useToast } from "../lib/toast";
import { TrashIcon, VolumeIcon } from "../components/Icons";
import { EmptyBox, ErrorBox, LoadingBox, Modal, ProgressBar, Tabs } from "../components/common";

const tabs = ["All", "Learning", "Mastered"];

function WordCard({
  word,
  onOpen,
  onDelete,
}: {
  word: WordResponse;
  onOpen: () => void;
  onDelete: () => void;
}) {
  const mastered = word.status.toLowerCase() === "mastered";
  const color = mastered ? "var(--green)" : "var(--red)";

  return (
    <div className="card" style={{ display: "flex", alignItems: "center", padding: 14, gap: 12 }}>
      <button onClick={onOpen} style={{ flex: 1, textAlign: "left", minWidth: 0 }}>
        <div style={{ fontSize: 16, fontWeight: 700 }}>{word.term}</div>
        <div className="muted" style={{ margin: "3px 0 10px", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
          {word.definition || "No definition yet"}
        </div>
        <ProgressBar value={wordProgress(word.repetitions, word.status)} color={color} />
      </button>

      {ttsSupported && (
        <button
          className="icon-btn"
          style={{ background: "var(--cream)" }}
          onClick={() => speak(word.term, word.language)}
          aria-label={`Pronounce ${word.term}`}
        >
          <VolumeIcon size={20} color="var(--text-secondary)" />
        </button>
      )}
      <button className="icon-btn" onClick={onDelete} aria-label={`Delete ${word.term}`}>
        <TrashIcon size={20} color="var(--text-secondary)" />
      </button>
    </div>
  );
}

export function WordsScreen() {
  const navigate = useNavigate();
  const toast = useToast();
  const { refresh: refreshBadge } = useWordsBadge();

  const [words, setWords] = useState<WordResponse[]>([]);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [error, setError] = useState("");
  const [tab, setTab] = useState(0);
  const [streak, setStreak] = useState(0);
  const [pendingDelete, setPendingDelete] = useState<WordResponse | null>(null);

  const load = useCallback(async () => {
    setStatus("loading");
    try {
      setWords(await api.listWords());
      setStatus("ready");
    } catch (err) {
      setError((err as Error).message);
      setStatus("error");
    }
  }, []);

  useEffect(() => {
    void load();
    api
      .getMyProfile()
      .then((profile) => setStreak(profile.streak_days))
      .catch(() => undefined);
  }, [load]);

  async function confirmDelete() {
    const word = pendingDelete;
    setPendingDelete(null);
    if (!word) return;
    const previous = words;
    setWords((prev) => prev.filter((item) => item.id !== word.id));
    try {
      await api.deleteWord(word.id);
      refreshBadge();
    } catch {
      setWords(previous);
      toast("Could not delete the word.");
    }
  }

  function exportList() {
    const csv = ["term,definition,status,language,repetitions,next_review", ...words.map((word) =>
      [word.term, word.definition ?? "", word.status, word.language, word.repetitions, word.next_review ?? ""]
        .map((field) => `"${String(field).replace(/"/g, '""')}"`)
        .join(","),
    )].join("\n");

    const blob = new Blob([csv], { type: "text/csv;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = "reelang-words.csv";
    link.click();
    URL.revokeObjectURL(url);
    toast("Exported as CSV");
  }

  const filtered = words.filter((word) => {
    const value = word.status.toLowerCase();
    if (tab === 1) return value === "learning";
    if (tab === 2) return value === "mastered";
    return true;
  });

  return (
    <div className="screen">
      <div className="topbar" style={{ justifyContent: "space-between" }}>
        <span style={{ fontSize: 22, fontWeight: 700 }}>My Words</span>
        <span
          style={{
            padding: "6px 12px",
            borderRadius: 20,
            background: "var(--red-tint)",
            color: "var(--red)",
            fontSize: 13,
            fontWeight: 600,
          }}
        >
          🔥 {streak} days
        </span>
      </div>

      <Tabs tabs={tabs} selected={tab} onSelect={setTab} />

      <div className="screen screen--scroll" style={{ padding: "12px 16px", gap: 10 }}>
        {status === "loading" && <LoadingBox />}
        {status === "error" && <ErrorBox message={error} onRetry={() => void load()} />}
        {status === "ready" && filtered.length === 0 && (
          <EmptyBox
            title="Nothing here yet"
            subtitle="Tap words in the subtitles while watching reels to build your vocabulary."
          />
        )}
        {status === "ready" &&
          filtered.map((word) => (
            <div key={word.id} style={{ marginBottom: 10 }}>
              <WordCard
                word={word}
                onOpen={() => navigate(`/words/${word.id}`)}
                onDelete={() => setPendingDelete(word)}
              />
            </div>
          ))}
      </div>

      <hr className="divider" />
      <div style={{ display: "flex", gap: 12, padding: "12px 16px", background: "var(--surface)" }}>
        <button className="btn btn--outline" style={{ flex: 1 }} onClick={exportList} disabled={words.length === 0}>
          Export List
        </button>
        <button className="btn btn--primary" style={{ flex: 1 }} onClick={() => navigate("/practice")}>
          Practice Now
        </button>
      </div>

      {pendingDelete && (
        <Modal
          title="Delete word?"
          confirmLabel="Delete"
          onConfirm={() => void confirmDelete()}
          onDismiss={() => setPendingDelete(null)}
        >
          <p className="muted" style={{ margin: 0 }}>
            &quot;{pendingDelete.term}&quot; will be removed from your vocabulary.
          </p>
        </Modal>
      )}
    </div>
  );
}
