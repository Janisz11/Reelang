import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../api/reelang";
import { streamUrl } from "../api/client";
import type { CaptionSegment, ProfileResponse, ReelResponse } from "../api/types";
import { enqueueEvent } from "../lib/eventTracking";
import { bgGradientFor, formatCount, initialsFrom, levelColor, sceneEmojiFor } from "../lib/format";
import { useSession } from "../lib/session";
import { useToast } from "../lib/toast";
import { useWordsBadge } from "../lib/wordsBadge";
import { CaptionOverlay, activeCaptionAt } from "../components/CaptionOverlay";
import { VideoReel, YouTubeReel } from "../components/ReelPlayer";
import { BookmarkIcon, HeartIcon, ShareIcon } from "../components/Icons";
import { EmptyBox, ErrorBox, LoadingBox } from "../components/common";

const IMPRESSION_DWELL_MS = 500;

type FeedMode =
  | { kind: "feed" }
  | { kind: "search"; reelIds: string[] }
  | { kind: "single"; reelId: string }
  | { kind: "user"; userId: string; startReelId: string };

function ReelTopBar({
  reel,
  owner,
  streakDays,
  onChannelClick,
}: {
  reel: ReelResponse;
  owner: ProfileResponse | undefined;
  streakDays: number;
  onChannelClick: (userId: string) => void;
}) {
  const isUploaded = reel.youtube_id === null;
  const name = reel.channel_name || owner?.username || (reel.owner_user_id ? "User" : "Unknown");
  const ownerInitials = owner?.avatar_initials ?? (owner ? initialsFrom(owner.username) : null);

  return (
    <div
      style={{
        position: "absolute",
        top: 0,
        left: 0,
        right: 0,
        zIndex: 6,
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        padding: "12px 16px",
        background: "linear-gradient(to bottom, rgba(0,0,0,0.65), transparent)",
        paddingBottom: 40,
      }}
    >
      <button
        style={{ display: "flex", alignItems: "center", gap: 8 }}
        onClick={(event) => {
          event.stopPropagation();
          if (reel.owner_user_id) onChannelClick(reel.owner_user_id);
        }}
      >
        <div
          style={{
            width: 36,
            height: 36,
            borderRadius: "50%",
            border: "1.5px solid rgba(255,255,255,0.6)",
            background: isUploaded ? "var(--red)" : "rgba(255,255,255,0.15)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: "#fff",
            fontSize: isUploaded && ownerInitials ? 13 : 18,
            fontWeight: 800,
          }}
        >
          {isUploaded && ownerInitials ? ownerInitials : sceneEmojiFor(reel.language)}
        </div>
        <div style={{ textAlign: "left" }}>
          <div style={{ fontSize: 13, fontWeight: 600, color: "#fff" }}>{name}</div>
          {reel.owner_user_id && !reel.channel_name && (
            <div style={{ fontSize: 10, color: "rgba(255,255,255,0.6)" }}>tap to view profile</div>
          )}
        </div>
        {reel.level && (
          <span
            style={{
              padding: "2px 6px",
              borderRadius: 4,
              background: levelColor(reel.level),
              color: "#fff",
              fontSize: 10,
              fontWeight: 700,
            }}
          >
            {reel.level}
          </span>
        )}
      </button>

      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: 4,
          padding: "5px 10px",
          borderRadius: 8,
          background: "rgba(230,81,0,0.9)",
          color: "#fff",
        }}
      >
        <span style={{ fontSize: 12 }}>🔥</span>
        <div style={{ lineHeight: 1.1 }}>
          <div style={{ fontSize: 10, fontWeight: 900 }}>{streakDays} DAY</div>
          <div style={{ fontSize: 8, fontWeight: 700, opacity: 0.85 }}>STREAK</div>
        </div>
      </div>
    </div>
  );
}

function ActionButton({
  children,
  count,
  onClick,
}: {
  children: React.ReactNode;
  count?: string;
  onClick: () => void;
}) {
  const [bounce, setBounce] = useState(false);
  return (
    <button
      onClick={(event) => {
        event.stopPropagation();
        setBounce(true);
        window.setTimeout(() => setBounce(false), 220);
        onClick();
      }}
      style={{
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        gap: 3,
        transform: bounce ? "scale(1.35)" : "scale(1)",
        transition: "transform 0.2s cubic-bezier(0.34, 1.56, 0.64, 1)",
      }}
    >
      {children}
      {count !== undefined && <span style={{ color: "#fff", fontSize: 12, fontWeight: 500 }}>{count}</span>}
    </button>
  );
}

function ReelCard({
  reel,
  owner,
  captions,
  isActive,
  streakDays,
  onLike,
  onSave,
  onShare,
  onWordClick,
  onChannelClick,
}: {
  reel: ReelResponse;
  owner: ProfileResponse | undefined;
  captions: CaptionSegment[];
  isActive: boolean;
  streakDays: number;
  onLike: () => void;
  onSave: () => void;
  onShare: () => void;
  onWordClick: (term: string) => void;
  onChannelClick: (userId: string) => void;
}) {
  const [timeMs, setTimeMs] = useState(0);
  const caption = activeCaptionAt(captions, timeMs);
  const handleTime = useCallback((ms: number) => setTimeMs(ms), []);

  return (
    <section
      style={{
        position: "relative",
        height: "100%",
        width: "100%",
        flexShrink: 0,
        scrollSnapAlign: "start",
        background: bgGradientFor(reel.language),
        overflow: "hidden",
      }}
    >
      {reel.youtube_id ? (
        <YouTubeReel youtubeId={reel.youtube_id} isActive={isActive} onTimeUpdate={handleTime} />
      ) : (
        <VideoReel
          streamUrl={streamUrl(reel.id)}
          isActive={isActive}
          onTimeUpdate={handleTime}
          reelId={reel.id}
        />
      )}

      <ReelTopBar reel={reel} owner={owner} streakDays={streakDays} onChannelClick={onChannelClick} />

      <div
        style={{
          position: "absolute",
          right: 16,
          top: "50%",
          transform: "translateY(-50%)",
          display: "flex",
          flexDirection: "column",
          gap: 28,
          zIndex: 6,
        }}
      >
        <ActionButton count={formatCount(reel.likes_count)} onClick={onLike}>
          <HeartIcon size={32} filled={reel.is_liked} color={reel.is_liked ? "var(--red-light)" : "#fff"} />
        </ActionButton>
        <ActionButton onClick={onSave}>
          <BookmarkIcon size={32} filled={reel.is_saved} color={reel.is_saved ? "var(--gold)" : "#fff"} />
        </ActionButton>
        <ActionButton onClick={onShare}>
          <ShareIcon size={30} color="#fff" />
        </ActionButton>
      </div>

      {caption && <CaptionOverlay caption={caption} onWordClick={onWordClick} />}

      {captions.length === 0 && isActive && (
        <div
          style={{
            position: "absolute",
            left: 16,
            right: 16,
            bottom: 96,
            padding: "8px 12px",
            borderRadius: 8,
            background: "rgba(0,0,0,0.45)",
            color: "rgba(255,255,255,0.75)",
            fontSize: 12,
            textAlign: "center",
          }}
        >
          No captions for this reel yet — transcription may still be running.
        </div>
      )}
    </section>
  );
}

export function ReelsScreen({ mode }: { mode: FeedMode }) {
  const navigate = useNavigate();
  const toast = useToast();
  const { userId } = useSession();
  const { refresh: refreshBadge } = useWordsBadge();

  const [reels, setReels] = useState<ReelResponse[]>([]);
  const [owners, setOwners] = useState<Record<string, ProfileResponse>>({});
  const [captionsMap, setCaptionsMap] = useState<Record<string, CaptionSegment[]>>({});
  const [activeIndex, setActiveIndex] = useState(0);
  const [status, setStatus] = useState<"loading" | "ready" | "error">("loading");
  const [error, setError] = useState("");
  const [streakDays, setStreakDays] = useState(0);

  const containerRef = useRef<HTMLDivElement>(null);
  const watchStart = useRef(Date.now());
  const watchedIds = useRef(new Set<string>());
  const consumed = useRef(new Set<string>());

  const load = useCallback(async () => {
    setStatus("loading");
    try {
      let data: ReelResponse[];
      switch (mode.kind) {
        case "single": {
          data = [await api.getReel(mode.reelId)];
          break;
        }
        case "user": {
          const all = await api.getUserReels(mode.userId);
          const start = all.findIndex((reel) => reel.id === mode.startReelId);
          data = start > 0 ? [...all.slice(start), ...all.slice(0, start)] : all;
          break;
        }
        case "search": {
          const all = await api.listReels();
          const priority = mode.reelIds
            .map((id) => all.find((reel) => reel.id === id))
            .filter((reel): reel is ReelResponse => Boolean(reel));
          const rest = all.filter((reel) => !mode.reelIds.includes(reel.id));
          data = [...priority, ...rest];
          break;
        }
        default: {
          data = await api.getFeed(10);
          if (data.length === 0) data = await api.listReels();
          void api.refillFeed().catch(() => undefined);
        }
      }
      setReels(data);
      setStatus("ready");
    } catch (err) {
      setError((err as Error).message);
      setStatus("error");
    }
  }, [mode]);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    api
      .getMyProfile()
      .then((profile) => setStreakDays(profile.streak_days))
      .catch(() => undefined);
  }, []);

  // An impression needs the card to hold the viewport for a beat, so a scroll-through
  // that passes over a reel never counts.
  useEffect(() => {
    const reel = reels[activeIndex];
    if (!reel) return;
    const id = window.setTimeout(
      () => enqueueEvent("reel_impression", reel.id),
      IMPRESSION_DWELL_MS,
    );
    return () => window.clearTimeout(id);
  }, [activeIndex, reels]);

  // Load captions for the visible reel and mark the previous one consumed.
  useEffect(() => {
    const reel = reels[activeIndex];
    if (!reel) return;
    watchedIds.current.add(reel.id);

    if (!(reel.id in captionsMap)) {
      api
        .getCaptions(reel.id)
        .then((captions) => setCaptionsMap((prev) => ({ ...prev, [reel.id]: captions })))
        .catch(() => setCaptionsMap((prev) => ({ ...prev, [reel.id]: [] })));
    }

    // The reel payload carries no owner username, so resolve it separately.
    const ownerId = reel.owner_user_id;
    if (ownerId && !(ownerId in owners)) {
      api
        .getProfile(ownerId)
        .then((profile) => setOwners((prev) => ({ ...prev, [ownerId]: profile })))
        .catch(() => undefined);
    }

    const previous = reels[activeIndex - 1];
    if (previous && !consumed.current.has(previous.id)) {
      consumed.current.add(previous.id);
      void api.markConsumed(previous.id).catch(() => undefined);
    }
  }, [activeIndex, reels, captionsMap, owners]);

  // Periodic activity sync, mirroring ReelsScreen's 30s cadence.
  useEffect(() => {
    function sync() {
      const elapsed = Date.now() - watchStart.current;
      const watched = watchedIds.current.size;
      if (elapsed < 1000 || watched === 0) return;
      void api.logActivity(elapsed, watched, 0).catch(() => undefined);
      watchStart.current = Date.now();
      watchedIds.current.clear();
    }

    const id = window.setInterval(sync, 30_000);
    return () => {
      window.clearInterval(id);
      sync();
    };
  }, []);

  // Track which card fills the viewport.
  useEffect(() => {
    const container = containerRef.current;
    if (!container || status !== "ready") return;

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting && entry.intersectionRatio > 0.6) {
            const index = Number((entry.target as HTMLElement).dataset.index);
            if (!Number.isNaN(index)) setActiveIndex(index);
          }
        }
      },
      { root: container, threshold: [0.6] },
    );

    for (const child of Array.from(container.children)) observer.observe(child);
    return () => observer.disconnect();
  }, [status, reels.length]);

  const toggleLike = useCallback(async (reel: ReelResponse) => {
    try {
      const result = await api.toggleLike(reel.id);
      enqueueEvent(result.liked ? "like" : "unlike", reel.id);
      setReels((prev) =>
        prev.map((item) =>
          item.id === reel.id ? { ...item, is_liked: result.liked, likes_count: result.likes_count } : item,
        ),
      );
    } catch {
      toast("Could not update the like.");
    }
  }, [toast]);

  const toggleSave = useCallback(async (reel: ReelResponse) => {
    try {
      const result = await api.toggleSave(reel.id);
      enqueueEvent(result.saved ? "save" : "unsave", reel.id);
      setReels((prev) => prev.map((item) => (item.id === reel.id ? { ...item, is_saved: result.saved } : item)));
      toast(result.saved ? "Saved to your profile" : "Removed from saved");
    } catch {
      toast("Could not update the save.");
    }
  }, [toast]);

  const saveWord = useCallback(
    async (term: string, reel: ReelResponse) => {
      try {
        await api.saveWord(term, reel.language, reel.id);
        toast(`"${term}" saved to your words`);
        refreshBadge();
      } catch (err) {
        toast((err as Error).message || `Could not save "${term}"`);
      }
    },
    [toast, refreshBadge],
  );

  const share = useCallback(
    async (reel: ReelResponse) => {
      const url = `${window.location.origin}/reel/${reel.id}`;
      enqueueEvent("share", reel.id);
      try {
        if (navigator.share) await navigator.share({ title: reel.title ?? "ReeLang", url });
        else {
          await navigator.clipboard.writeText(url);
          toast("Link copied to clipboard");
        }
      } catch {
        // user dismissed the share sheet
      }
    },
    [toast],
  );

  const openProfile = useCallback(
    (ownerId: string) => {
      if (ownerId !== userId) navigate(`/profile/${ownerId}`);
      else navigate("/profile");
    },
    [navigate, userId],
  );

  const content = useMemo(() => {
    if (status === "loading") return <LoadingBox />;
    if (status === "error") return <ErrorBox message={error} onRetry={() => void load()} />;
    if (reels.length === 0) {
      return (
        <EmptyBox
          title="No reels yet"
          subtitle="The AI agent fills your feed every 30 minutes. Try the Search tab in the meantime."
          action={
            <button className="btn btn--primary" onClick={() => navigate("/search")}>
              Go to Search
            </button>
          }
        />
      );
    }
    return null;
  }, [status, error, reels.length, load, navigate]);

  if (content) {
    return <div className="screen" style={{ background: "#000", color: "#fff" }}>{content}</div>;
  }

  return (
    <div
      ref={containerRef}
      className="screen"
      style={{
        background: "#000",
        overflowY: "auto",
        scrollSnapType: "y mandatory",
        scrollbarWidth: "none",
      }}
    >
      {reels.map((reel, index) => (
        <div key={reel.id} data-index={index} style={{ height: "100%", flexShrink: 0 }}>
          <ReelCard
            reel={reel}
            owner={reel.owner_user_id ? owners[reel.owner_user_id] : undefined}
            captions={captionsMap[reel.id] ?? []}
            isActive={index === activeIndex}
            streakDays={streakDays}
            onLike={() => void toggleLike(reel)}
            onSave={() => void toggleSave(reel)}
            onShare={() => void share(reel)}
            onWordClick={(term) => void saveWord(term, reel)}
            onChannelClick={openProfile}
          />
        </div>
      ))}
    </div>
  );
}

const FEED_MODE: FeedMode = { kind: "feed" };

export function FeedRoute() {
  return <ReelsScreen mode={FEED_MODE} />;
}

export function SearchFeedRoute() {
  const { reelIds = "" } = useParams();
  const mode = useMemo<FeedMode>(
    () => ({ kind: "search", reelIds: reelIds.split(",").filter(Boolean) }),
    [reelIds],
  );
  return <ReelsScreen mode={mode} />;
}

export function SingleReelRoute() {
  const { reelId = "" } = useParams();
  const mode = useMemo<FeedMode>(() => ({ kind: "single", reelId }), [reelId]);
  return <ReelsScreen mode={mode} />;
}

export function UserReelsRoute() {
  const { userId = "", startReelId = "" } = useParams();
  const mode = useMemo<FeedMode>(() => ({ kind: "user", userId, startReelId }), [userId, startReelId]);
  return <ReelsScreen mode={mode} />;
}
