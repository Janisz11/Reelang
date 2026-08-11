/**
 * Mirrors backend/app/schemas.py and the inline models in app/routers/.
 * Note: the Kotlin models in the Android app carry extra fields the API never
 * returns (Gson silently fills them from defaults) — these types follow the
 * server, not the Kotlin data classes.
 */

export interface ReelResponse {
  id: string;
  youtube_id: string | null;
  title: string;
  channel_name: string | null;
  thumbnail_url: string | null;
  owner_user_id: string | null;
  duration_ms: number | null;
  language: string;
  level: string | null;
  topic: string | null;
  tags: string | null;
  likes_count: number;
  is_liked: boolean;
  is_saved: boolean;
  created_at: string;
}

export interface CaptionSegment {
  id: string;
  reel_id: string;
  start_ms: number;
  end_ms: number;
  original_text: string;
  translated_text: string | null;
}

export interface ReelDetailResponse extends ReelResponse {
  segments: CaptionSegment[];
}

export interface WordResponse {
  id: string;
  term: string;
  definition: string | null;
  language: string;
  status: string;
  reel_id: string | null;
  segment_id: string | null;
  created_at: string;
  repetitions: number;
  easiness: number;
  interval_days: number;
  next_review: string | null;
}

export interface WordLookupResponse {
  term: string;
  clean_term: string;
  language: string;
  definition: string | null;
  translation: string | null;
  target_lang: string;
}

export interface ProfileResponse {
  user_id: string;
  username: string;
  bio: string | null;
  avatar_initials: string | null;
  followers_count: number;
  following_count: number;
  total_likes: number;
  level: number;
  streak_days: number;
  is_following: boolean;
}

export interface DayStat {
  day: string;
  value: number;
}

export interface LanguageStat {
  flag: string;
  name: string;
  progress: number;
  percent: number;
}

export interface ActivityStatsResponse {
  vocabulary_mastered: number;
  streak_days: number;
  hours_watched: number;
  weekly_activity: DayStat[];
  target_languages: LanguageStat[];
}

export interface LikeResponse {
  liked: boolean;
  likes_count: number;
}

export interface SaveResponse {
  saved: boolean;
  saves_count: number;
}

export interface FollowResponse {
  following: boolean;
}

export interface ReelUploadResponse {
  id: string;
  title: string;
  language: string;
  stream_url: string;
  tags: string | null;
}
