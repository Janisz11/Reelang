import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../api/reelang";
import type { ProfileResponse, ReelResponse } from "../api/types";
import { initialsFrom } from "../lib/format";
import { Avatar, Spinner, Tabs } from "../components/common";

const languages = ["", "EN", "ES", "FR", "DE", "PL"];
const levels = ["", "A1", "A2", "B1", "B2", "C1", "C2"];

function ChipRow({
  label,
  options,
  selected,
  onSelect,
}: {
  label: string;
  options: string[];
  selected: string;
  onSelect: (value: string) => void;
}) {
  return (
    <div>
      <p className="muted" style={{ margin: "0 0 8px" }}>
        {label}
      </p>
      <div style={{ display: "flex", gap: 8, overflowX: "auto", paddingBottom: 4 }}>
        {options.map((option) => (
          <button
            key={option || "all"}
            className={`chip${selected === option ? " chip--active" : ""}`}
            onClick={() => onSelect(option)}
          >
            {option || "All"}
          </button>
        ))}
      </div>
    </div>
  );
}

export function SearchScreen() {
  const navigate = useNavigate();

  const [tab, setTab] = useState(0);
  const [language, setLanguage] = useState("");
  const [level, setLevel] = useState("");
  const [tags, setTags] = useState("");
  const [reels, setReels] = useState<ReelResponse[]>([]);
  const [reelsLoading, setReelsLoading] = useState(true);

  const [profileQuery, setProfileQuery] = useState("");
  const [profiles, setProfiles] = useState<ProfileResponse[]>([]);
  const [profilesLoading, setProfilesLoading] = useState(false);

  const searchReels = useCallback(
    async (nextLanguage = language, nextLevel = level, nextTags = tags) => {
      setReelsLoading(true);
      try {
        setReels(
          await api.listReels({
            language: nextLanguage.toLowerCase() || undefined,
            level: nextLevel || undefined,
            tags: nextTags || undefined,
          }),
        );
      } catch {
        setReels([]);
      }
      setReelsLoading(false);
    },
    [language, level, tags],
  );

  useEffect(() => {
    void searchReels("", "", "");
    // Initial unfiltered load only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (tab !== 1) return;
    const query = profileQuery.trim();
    if (!query) {
      setProfiles([]);
      return;
    }
    setProfilesLoading(true);
    const id = window.setTimeout(() => {
      api
        .searchProfiles(query)
        .then(setProfiles)
        .catch(() => setProfiles([]))
        .finally(() => setProfilesLoading(false));
    }, 300);
    return () => window.clearTimeout(id);
  }, [profileQuery, tab]);

  function openReel(reel: ReelResponse) {
    const ordered = [reel.id, ...reels.map((item) => item.id).filter((id) => id !== reel.id)];
    navigate(`/feed/from-search/${ordered.join(",")}`);
  }

  return (
    <div className="screen screen--scroll" style={{ padding: 16, gap: 16 }}>
      <h1 style={{ margin: 0, fontSize: 24, fontWeight: 700 }}>Discover</h1>

      <Tabs tabs={["Reels", "Profiles"]} selected={tab} onSelect={setTab} />

      {tab === 0 ? (
        <div style={{ display: "grid", gap: 16, marginTop: 16 }}>
          <ChipRow
            label="Language"
            options={languages}
            selected={language}
            onSelect={(value) => {
              setLanguage(value);
              void searchReels(value, level, tags);
            }}
          />
          <ChipRow
            label="Level"
            options={levels}
            selected={level}
            onSelect={(value) => {
              setLevel(value);
              void searchReels(language, value, tags);
            }}
          />

          <div className="input-wrap">
            <input
              className="input input--with-action"
              placeholder="Search by tag — e.g. sport"
              value={tags}
              onChange={(event) => setTags(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === "Enter") void searchReels();
              }}
            />
            <button className="input-wrap__action" onClick={() => void searchReels()}>
              Search
            </button>
          </div>

          {reelsLoading ? (
            <div style={{ display: "grid", placeItems: "center", padding: 24 }}>
              <Spinner />
            </div>
          ) : (
            <>
              <p className="muted" style={{ margin: 0 }}>
                {reels.length} reels
              </p>
              {reels.map((reel) => (
                <button key={reel.id} className="card" style={{ padding: 12, textAlign: "left" }} onClick={() => openReel(reel)}>
                  <div style={{ fontWeight: 600 }}>{reel.title ?? "Untitled"}</div>
                  {reel.tags && <div style={{ fontSize: 12, color: "var(--red)", marginTop: 4 }}>{reel.tags}</div>}
                  <div className="muted" style={{ fontSize: 12 }}>
                    {reel.language.toUpperCase()}
                    {reel.level ? ` · ${reel.level}` : ""}
                  </div>
                </button>
              ))}
            </>
          )}
        </div>
      ) : (
        <div style={{ display: "grid", gap: 12, marginTop: 16 }}>
          <input
            className="input"
            placeholder="Search users — e.g. alex"
            value={profileQuery}
            onChange={(event) => setProfileQuery(event.target.value)}
          />

          {profilesLoading && (
            <div style={{ display: "grid", placeItems: "center", padding: 24 }}>
              <Spinner />
            </div>
          )}

          {!profilesLoading &&
            profiles.map((profile) => (
              <button
                key={profile.user_id}
                className="card"
                style={{ display: "flex", alignItems: "center", gap: 12, padding: 12, textAlign: "left" }}
                onClick={() => navigate(`/profile/${profile.user_id}`)}
              >
                <Avatar initials={profile.avatar_initials ?? initialsFrom(profile.username)} />
                <div>
                  <div style={{ fontWeight: 600 }}>{profile.username}</div>
                  <div className="muted" style={{ fontSize: 12 }}>
                    LVL {profile.level} · {profile.followers_count} followers
                  </div>
                </div>
              </button>
            ))}

          {!profilesLoading && profileQuery.trim() && profiles.length === 0 && (
            <p className="muted" style={{ textAlign: "center", padding: 16 }}>
              No users match &quot;{profileQuery}&quot;.
            </p>
          )}
        </div>
      )}
    </div>
  );
}
