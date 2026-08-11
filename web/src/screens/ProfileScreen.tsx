import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { api } from "../api/reelang";
import { thumbnailUrl } from "../api/client";
import type { ProfileResponse, ReelResponse } from "../api/types";
import { bgColorFor, initialsFrom, sceneEmojiFor } from "../lib/format";
import { useSession } from "../lib/session";
import { useToast } from "../lib/toast";
import { ChartIcon, ChevronRightIcon, PlusIcon, SettingsIcon } from "../components/Icons";
import { Avatar, LoadingBox, Modal, Tabs } from "../components/common";

const profileTabs = ["Posts", "Saved", "Private"];

function ThumbnailGrid({
  reels,
  onOpen,
  onDelete,
  emptyText,
}: {
  reels: ReelResponse[];
  onOpen: (reel: ReelResponse) => void;
  onDelete?: (reel: ReelResponse) => void;
  emptyText: string;
}) {
  if (reels.length === 0) {
    return (
      <p className="muted" style={{ textAlign: "center", padding: 32 }}>
        {emptyText}
      </p>
    );
  }

  return (
    <div style={{ display: "grid", gridTemplateColumns: "repeat(3, 1fr)", gap: 2 }}>
      {reels.map((reel) => {
        const src = reel.thumbnail_url?.startsWith("http") ? reel.thumbnail_url : thumbnailUrl(reel.id);
        return (
          <div key={reel.id} style={{ position: "relative", aspectRatio: "1", background: bgColorFor(reel.language) }}>
            <button
              onClick={() => onOpen(reel)}
              style={{ position: "absolute", inset: 0, display: "grid", placeItems: "center", fontSize: 28 }}
            >
              <img
                src={src}
                alt={reel.title ?? ""}
                loading="lazy"
                onError={(event) => {
                  event.currentTarget.style.display = "none";
                }}
                style={{ position: "absolute", inset: 0, width: "100%", height: "100%", objectFit: "cover" }}
              />
              <span style={{ position: "relative" }}>{sceneEmojiFor(reel.language)}</span>
            </button>
            {onDelete && (
              <button
                onClick={() => onDelete(reel)}
                style={{
                  position: "absolute",
                  top: 4,
                  right: 4,
                  width: 24,
                  height: 24,
                  borderRadius: "50%",
                  background: "rgba(0,0,0,0.55)",
                  color: "#fff",
                  fontSize: 14,
                  lineHeight: 1,
                }}
                aria-label="Delete reel"
              >
                ×
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
}

export function ProfileScreen() {
  const { userId: routeUserId } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const { userId: myUserId, displayName, initials } = useSession();

  const isOther = Boolean(routeUserId && routeUserId !== myUserId);
  const targetId = routeUserId ?? myUserId;

  const [profile, setProfile] = useState<ProfileResponse | null>(null);
  const [posts, setPosts] = useState<ReelResponse[]>([]);
  const [saved, setSaved] = useState<ReelResponse[]>([]);
  const [tab, setTab] = useState(0);
  const [loading, setLoading] = useState(true);
  const [pendingDelete, setPendingDelete] = useState<ReelResponse | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const [profileData, postsData] = await Promise.all([
        isOther ? api.getProfile(targetId) : api.getMyProfile(),
        api.getUserReels(targetId).catch(() => []),
      ]);
      setProfile(profileData);
      setPosts(postsData);
      if (!isOther) setSaved(await api.getSavedReels().catch(() => []));
    } catch (err) {
      toast((err as Error).message);
    }
    setLoading(false);
  }, [isOther, targetId, toast]);

  useEffect(() => {
    void load();
  }, [load]);

  async function toggleFollow() {
    if (!profile) return;
    try {
      const result = await api.followUser(profile.user_id);
      setProfile({
        ...profile,
        is_following: result.following,
        followers_count: profile.followers_count + (result.following ? 1 : -1),
      });
    } catch {
      toast("Could not update follow state.");
    }
  }

  async function confirmDelete() {
    const reel = pendingDelete;
    setPendingDelete(null);
    if (!reel) return;
    try {
      await api.deleteReel(reel.id);
      setPosts((prev) => prev.filter((item) => item.id !== reel.id));
      toast("Reel deleted");
    } catch {
      toast("Could not delete the reel.");
    }
  }

  if (loading) {
    return (
      <div className="screen">
        <LoadingBox />
      </div>
    );
  }

  const name = profile?.username ?? displayName;
  const avatarInitials = profile?.avatar_initials ?? (profile ? initialsFrom(profile.username) : initials);

  return (
    <div className="screen screen--scroll" style={{ position: "relative" }}>
      <div style={{ background: "var(--surface)", padding: "24px 20px", position: "relative", textAlign: "center" }}>
        {!isOther && (
          <button
            className="icon-btn"
            style={{ position: "absolute", top: 12, right: 12 }}
            onClick={() => navigate("/settings")}
            aria-label="Settings"
          >
            <SettingsIcon size={22} color="var(--text-secondary)" />
          </button>
        )}
        <div style={{ display: "flex", justifyContent: "center" }}>
          <Avatar initials={avatarInitials} size={88} />
        </div>
        <h1 style={{ margin: "14px 0 4px", fontSize: 20, fontWeight: 800 }}>{name}</h1>
        <p className="muted" style={{ margin: 0, fontSize: 11, fontWeight: 600, letterSpacing: 0.6 }}>
          LVL {profile?.level ?? 1}
        </p>
        {profile?.bio && <p className="muted" style={{ margin: "10px 0 0", fontSize: 13 }}>{profile.bio}</p>}
      </div>

      <div
        style={{
          display: "flex",
          justifyContent: "space-evenly",
          background: "var(--surface)",
          padding: "16px 0",
          borderBottom: "1px solid var(--border)",
        }}
      >
        {[
          { value: profile?.followers_count ?? 0, label: "Followers" },
          { value: profile?.following_count ?? 0, label: "Following" },
          { value: profile?.total_likes ?? 0, label: "Likes" },
        ].map((stat) => (
          <div key={stat.label} style={{ textAlign: "center" }}>
            <div style={{ fontSize: 18, fontWeight: 800 }}>{stat.value}</div>
            <div className="muted" style={{ fontSize: 12 }}>
              {stat.label}
            </div>
          </div>
        ))}
      </div>

      {isOther && (
        <div style={{ background: "var(--surface)", padding: "12px 20px", borderBottom: "1px solid var(--border)" }}>
          <button
            className={`btn btn--full ${profile?.is_following ? "btn--outline" : "btn--primary"}`}
            onClick={() => void toggleFollow()}
          >
            {profile?.is_following ? "Following" : "Follow"}
          </button>
        </div>
      )}

      {!isOther && (
        <button
          onClick={() => navigate("/stats")}
          style={{
            width: "100%",
            display: "flex",
            alignItems: "center",
            gap: 14,
            padding: "16px 20px",
            background: "var(--surface)",
            textAlign: "left",
          }}
        >
          <span
            style={{
              width: 40,
              height: 40,
              borderRadius: 10,
              background: "var(--red-tint)",
              display: "grid",
              placeItems: "center",
            }}
          >
            <ChartIcon size={22} color="var(--red)" />
          </span>
          <span style={{ flex: 1 }}>
            <span style={{ display: "block", fontSize: 15, fontWeight: 600 }}>Detailed Learning Stats</span>
            <span className="muted" style={{ fontSize: 12 }}>
              Vocabulary, streaks, activity
            </span>
          </span>
          <ChevronRightIcon size={16} color="var(--text-secondary)" />
        </button>
      )}

      <div style={{ height: 2 }} />
      <Tabs tabs={profileTabs} selected={tab} onSelect={setTab} />

      {tab === 0 && (
        <ThumbnailGrid
          reels={posts}
          emptyText={isOther ? "This user hasn't posted yet." : "You haven't posted a reel yet."}
          onOpen={(reel) => navigate(`/user-reels/${targetId}/${reel.id}`)}
          onDelete={isOther ? undefined : (reel) => setPendingDelete(reel)}
        />
      )}

      {tab === 1 &&
        (isOther ? (
          <p className="muted" style={{ textAlign: "center", padding: 32 }}>
            Saved reels are private to each user.
          </p>
        ) : (
          <ThumbnailGrid
            reels={saved}
            emptyText="Nothing saved yet — tap the bookmark on a reel."
            onOpen={(reel) => navigate(`/reel/${reel.id}`)}
          />
        ))}

      {tab === 2 && (
        <p className="muted" style={{ textAlign: "center", padding: 32, lineHeight: 1.6 }}>
          The private gallery reads photos and videos stored on the device itself, so it exists only in the Android
          app. A browser has no equivalent local store to read from.
        </p>
      )}

      {!isOther && (
        <button
          className="btn btn--primary"
          onClick={() => navigate("/create")}
          style={{
            position: "sticky",
            bottom: 16,
            marginLeft: "auto",
            marginRight: 16,
            marginTop: 16,
            borderRadius: 16,
            boxShadow: "0 4px 16px rgba(0,0,0,0.2)",
          }}
        >
          <PlusIcon size={20} />
          Create
        </button>
      )}

      {pendingDelete && (
        <Modal
          title="Delete reel?"
          confirmLabel="Delete"
          onConfirm={() => void confirmDelete()}
          onDismiss={() => setPendingDelete(null)}
        >
          <p className="muted" style={{ margin: 0 }}>
            &quot;{pendingDelete.title ?? "This reel"}&quot; and its stored video will be removed.
          </p>
        </Modal>
      )}
    </div>
  );
}
