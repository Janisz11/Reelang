import { http } from "./client";
import type {
  ActivityStatsResponse,
  CaptionSegment,
  FollowResponse,
  LikeResponse,
  ProfileResponse,
  ReelDetailResponse,
  ReelResponse,
  ReelUploadResponse,
  SaveResponse,
  WordLookupResponse,
  WordResponse,
} from "./types";

export const api = {
  // ── Reels ──────────────────────────────────────────────────────────────────
  listReels: (filters: { language?: string; level?: string; topic?: string; tags?: string; user_id?: string } = {}) =>
    http.get<ReelResponse[]>("reels", filters),

  getReel: (reelId: string) => http.get<ReelDetailResponse>(`reels/${reelId}`),

  getUserReels: (userId: string) => http.get<ReelResponse[]>(`reels/user/${userId}`),

  getSavedReels: () => http.get<ReelResponse[]>("reels/saved"),

  getCaptions: (reelId: string) => http.get<CaptionSegment[]>(`reels/${reelId}/captions`),

  toggleLike: (reelId: string) => http.post<LikeResponse>(`reels/${reelId}/like`),

  toggleSave: (reelId: string) => http.post<SaveResponse>(`reels/${reelId}/save`),

  deleteReel: (reelId: string) => http.del<void>(`reels/${reelId}`),

  uploadReel: (file: File, title: string, language: string, tags: string) => {
    const form = new FormData();
    form.append("file", file);
    form.append("title", title);
    form.append("language", language);
    form.append("tags", tags);
    return http.post<ReelUploadResponse>("reels/upload", { formData: form });
  },

  // ── Feed ───────────────────────────────────────────────────────────────────
  getFeed: (limit = 10) => http.get<ReelResponse[]>("feed", { limit }),

  markConsumed: (reelId: string) => http.post<Record<string, unknown>>(`feed/consumed/${reelId}`),

  refillFeed: () => http.post<void>("feed/refill"),

  // ── Words ──────────────────────────────────────────────────────────────────
  listWords: (dueOnly = false) => http.get<WordResponse[]>("words", { due_only: dueOnly }),

  getWord: (wordId: string) => http.get<WordResponse>(`words/${wordId}`),

  /** The backend fetches the definition itself — WordCreateRequest takes no definition field. */
  saveWord: (term: string, language: string, reelId?: string, segmentId?: string) =>
    http.post<WordResponse>("words", {
      body: { term, language, reel_id: reelId ?? null, segment_id: segmentId ?? null },
    }),

  lookupWord: (term: string, language: string, targetLang = "en") =>
    http.get<WordLookupResponse>("words/lookup", { term, language, target_lang: targetLang }),

  reviewWord: (wordId: string, quality: number) =>
    http.post<WordResponse>(`words/${wordId}/review`, { body: { quality } }),

  deleteWord: (wordId: string) => http.del<void>(`words/${wordId}`),

  // ── Profiles ───────────────────────────────────────────────────────────────
  getMyProfile: () => http.get<ProfileResponse>("profiles/me"),

  updateMyProfile: (body: { username?: string; bio?: string; avatar_initials?: string }) =>
    http.put<ProfileResponse>("profiles/me", { body }),

  getMyStats: () => http.get<ActivityStatsResponse>("profiles/me/stats"),

  searchProfiles: (q: string) => http.get<ProfileResponse[]>("profiles/search", { q }),

  getProfile: (userId: string) => http.get<ProfileResponse>(`profiles/${userId}`),

  followUser: (userId: string) => http.post<FollowResponse>(`profiles/${userId}/follow`),

  // ── Activity ───────────────────────────────────────────────────────────────
  logActivity: (watchTimeMs: number, reelsWatched: number, wordsSaved: number) =>
    http.post<void>("activity/log", {
      body: { watch_time_ms: watchTimeMs, reels_watched: reelsWatched, words_saved: wordsSaved },
    }),
};
