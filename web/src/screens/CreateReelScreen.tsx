import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/reelang";
import { availableLanguages } from "./OnboardingScreen";
import { compressImage, validateFile } from "../lib/fileValidator";
import { useToast } from "../lib/toast";
import { UploadIcon } from "../components/Icons";
import { Spinner, TopBar } from "../components/common";

export function CreateReelScreen() {
  const navigate = useNavigate();
  const toast = useToast();
  const inputRef = useRef<HTMLInputElement>(null);

  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [language, setLanguage] = useState("es");
  const [tags, setTags] = useState("");
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!file) {
      setPreviewUrl(null);
      return;
    }
    const url = URL.createObjectURL(file);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  async function pickFile(selected: File) {
    setError(null);
    const result = validateFile(selected.type, selected.size);

    if (result.kind === "error") {
      setError(result.message);
      setFile(null);
      return;
    }

    if (result.kind === "needs-compression") {
      toast("Compressing image…");
      const compressed = await compressImage(selected);
      if (compressed.size > 2 * 1024 * 1024) {
        setError("Image is still too large after compression. Please choose a smaller file.");
        setFile(null);
        return;
      }
      setFile(compressed);
      return;
    }

    setFile(selected);
  }

  async function upload() {
    if (!file || !title.trim()) return;
    setUploading(true);
    setError(null);
    try {
      await api.uploadReel(file, title.trim(), language, tags.trim());
      toast("Reel uploaded — transcription is running in the background");
      navigate("/profile", { replace: true });
    } catch (err) {
      setError((err as Error).message);
    } finally {
      setUploading(false);
    }
  }

  const isVideo = file?.type.startsWith("video/") ?? false;

  return (
    <div className="screen">
      <TopBar title="Create Reel" />

      <div className="screen screen--scroll" style={{ padding: 16, gap: 16 }}>
        <button
          onClick={() => inputRef.current?.click()}
          style={{
            width: "100%",
            aspectRatio: "9 / 12",
            borderRadius: 16,
            border: "2px dashed var(--border)",
            background: "var(--surface)",
            display: "grid",
            placeItems: "center",
            overflow: "hidden",
            position: "relative",
          }}
        >
          {previewUrl ? (
            isVideo ? (
              <video src={previewUrl} controls playsInline style={{ width: "100%", height: "100%", objectFit: "contain" }} />
            ) : (
              <img src={previewUrl} alt="" style={{ width: "100%", height: "100%", objectFit: "contain" }} />
            )
          ) : (
            <div style={{ display: "grid", justifyItems: "center", gap: 8, color: "var(--text-secondary)" }}>
              <UploadIcon size={36} color="var(--text-secondary)" />
              <span style={{ fontWeight: 600 }}>Pick a video or image</span>
              <span style={{ fontSize: 12 }}>Video up to 100 MB · images are compressed to 2 MB</span>
            </div>
          )}
        </button>

        <input
          ref={inputRef}
          type="file"
          accept="video/*,image/*"
          hidden
          onChange={(event) => {
            const selected = event.target.files?.[0];
            if (selected) void pickFile(selected);
            event.target.value = "";
          }}
        />

        <div className="field">
          <label className="field__label" htmlFor="title">
            Title
          </label>
          <input
            id="title"
            className="input"
            value={title}
            onChange={(event) => setTitle(event.target.value)}
            placeholder="What is this reel about?"
          />
        </div>

        <div className="field">
          <label className="field__label" htmlFor="language">
            Language
          </label>
          <select id="language" className="input" value={language} onChange={(event) => setLanguage(event.target.value)}>
            {availableLanguages.map((item) => (
              <option key={item.code} value={item.code}>
                {item.flag} {item.name}
              </option>
            ))}
            <option value="en">🇬🇧 English</option>
            <option value="pl">🇵🇱 Polish</option>
          </select>
        </div>

        <div className="field">
          <label className="field__label" htmlFor="tags">
            Tags
          </label>
          <input
            id="tags"
            className="input"
            value={tags}
            onChange={(event) => setTags(event.target.value)}
            placeholder="comma,separated,tags"
          />
        </div>

        {error && <p className="error-text" style={{ margin: 0 }}>{error}</p>}

        <button
          className="btn btn--primary btn--full"
          disabled={!file || !title.trim() || uploading}
          onClick={() => void upload()}
        >
          {uploading ? <Spinner light small /> : "Upload reel"}
        </button>

        <p className="muted" style={{ fontSize: 12, textAlign: "center", margin: 0 }}>
          After upload the backend generates a thumbnail and runs Whisper transcription, so captions appear a little
          later.
        </p>
      </div>
    </div>
  );
}
