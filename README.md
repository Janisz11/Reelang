# ReeLang - Language Learning Through Short Videos

## Overview

ReeLang is a mobile application that teaches foreign languages through short-form video content. Users scroll through a TikTok-style vertical feed of language-learning videos, tap unfamiliar words to save them, and then practice their vocabulary using a spaced-repetition flashcard system. An AI agent continuously curates personalized video feeds by discovering relevant YouTube Shorts for each user's target language and level.

---

## Features

- **Vertical video feed** — swipe through language-learning Shorts; supports both YouTube-hosted and self-uploaded videos
- **Interactive captions** — tap any word in the subtitle overlay to look up its definition and save it instantly
- **Word lookup** — on-demand dictionary + translation lookup (source language → English)
- **Vocabulary list** — browse saved words filtered by All / Learning / Mastered tabs
- **Spaced-repetition practice** — SM-2 algorithm schedules flashcard reviews; tap/flip cards to reveal translations
- **AI-curated feed** — background agent queries YouTube Data API, imports relevant Shorts, and fills each user's personal feed queue
- **User profiles** — followers, following, total likes, level, streak; follow/unfollow other users
- **Post creation** — upload your own video or image reel directly from the app
- **Detailed stats** — vocabulary count, streak days, hours watched, weekly activity bar chart, target language breakdown
- **Firebase Authentication** — email/password + Google Sign-In
- **Offline cache** — Room database caches words, reels, captions, and profiles for offline access
- **Streak tracking** — daily activity logging increments a learning streak counter
- **Search** — search YouTube for videos by query and language, import directly into the feed
- **Private gallery** — personal photos/videos stored in device assets

---

## Tech Stack

### Android
- Kotlin + Jetpack Compose
- MVVM Architecture (ViewModel + StateFlow)
- Room Database (offline cache)
- Firebase Authentication (email/password + Google)
- Retrofit2 + OkHttp (API client with `AuthInterceptor`)
- ExoPlayer (local video playback)
- YouTube Android Player API (YouTube video playback)
- Coil (image loading)
- Navigation Compose

### Backend
- FastAPI (Python 3.12)
- PostgreSQL 16
- SQLAlchemy 2.0 + Alembic migrations
- yt-dlp (YouTube video download)
- Groq Whisper API (audio transcription)
- deep-translator (caption translation)
- Cloudflare R2 / boto3 (video + thumbnail storage)
- uvicorn (ASGI server)
- pytest + FastAPI TestClient (testing)

### AI Agent
- FastAPI microservice (port 8001)
- APScheduler `AsyncIOScheduler` (runs every 30 minutes)
- YouTube Data API v3 (video discovery)
- httpx (async HTTP to backend)
- User profiler (builds language/level/tag profile from DB signals)

---

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  Android App (Kotlin)                   │
│  Firebase Auth → Retrofit2 → ReelangApi interface       │
│  Room DB (offline cache for words/reels/captions)       │
└───────────────────┬─────────────────────────────────────┘
                    │ HTTPS  (BASE_URL: railway.app/api/v1/)
                    ▼
┌─────────────────────────────────────────────────────────┐
│               FastAPI Backend (Python)                  │
│  /reels  /words  /profiles  /feed  /activity  /search   │
│  PostgreSQL (SQLAlchemy)  │  Cloudflare R2 (boto3)       │
│  Groq Whisper             │  yt-dlp                      │
└───────────────────┬─────────────────────────────────────┘
                    │ HTTP (internal Docker network)
                    ▼
┌─────────────────────────────────────────────────────────┐
│            ReeLang AI Agent (Python)                    │
│  APScheduler (30 min)  →  user_profiler                 │
│  YouTube Data API v3   →  feed_curator                  │
│  Trigger endpoint: POST /trigger/{user_id}              │
└─────────────────────────────────────────────────────────┘
                    │ shared PostgreSQL
                    ▼
              PostgreSQL 16
              user_feed_queue table (bridge between AI and backend)
```

---

## Screens

| Screen | Description |
|--------|-------------|
| `AuthScreen` | Sign in / Register with email+password or Google Sign-In |
| `OnboardingScreen` | Language and CEFR level selection after first login |
| `ReelsScreen` | Main vertical-pager feed; auto-loads captions, syncs activity, marks consumed |
| `SearchScreen` | Search YouTube by query and language; import results to feed |
| `WordsScreen` | Vocabulary list with All / Learning / Mastered tabs; swipe-to-delete |
| `WordDetailScreen` | Full word details — definition, translation, source reel, review history |
| `PracticeScreen` | Flashcard session; flip to reveal translation; Known / Don't know buttons feed SM-2 |
| `ProfileScreen` | User profile with Posts / Saved / Private gallery tabs; follow button for other users |
| `StatsScreen` | Weekly activity bar chart, streak days, hours watched, vocabulary count, language progress |
| `SettingsScreen` | App settings and logout |
| `CreateReelScreen` | Pick video/image from gallery, set title + language, upload to backend |

---

## Database Schema

### PostgreSQL (backend)

| Table | Purpose |
|-------|---------|
| `reels` | Videos — youtube_id, title, channel, language, level, topic, tags, r2_key, likes_count |
| `caption_segments` | Subtitle segments — reel_id FK, start_ms, end_ms, original_text, translated_text |
| `words` | User vocabulary — user_id, term, definition, language, SM-2 fields (repetitions, easiness, interval_days, next_review), status |
| `profiles` | User profiles — username, bio, avatar_initials, followers_count, following_count, total_likes, level, streak_days |
| `follows` | Follow relationships — follower_id, following_id (composite PK) |
| `reel_likes` | Like records — user_id + reel_id (composite PK) |
| `saved_reels` | Saved reel records — user_id + reel_id (composite PK) |
| `activity_logs` | Daily activity — user_id, date, watch_time_ms, reels_watched, words_saved |
| `user_feed_queue` | AI-curated queue — user_id, reel_id, score, consumed, added_at |

### Alembic Migrations

| Version | Change |
|---------|--------|
| 0001_initial | Initial schema (reels, caption_segments, words) |
| 0002 | file_path nullable, youtube_id |
| 0003 | tags column on reels |
| 0004 | language normalization |
| 0005 | profiles + follows tables |
| 0006 | reel_likes + likes_count |
| 0007 | user_feed_queue |
| 0008 | saved_reels |
| 0009 | SM-2 fields (repetitions, easiness, interval_days, next_review) |
| 0010 | r2_key + r2_thumb_key on reels |

---

## API Endpoints

All endpoints are prefixed with `/api/v1/`.

### Reels
| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/reels/import` | Import a YouTube video as a reel (async transcription) |
| `POST` | `/reels/upload` | Upload a local video/image file |
| `GET` | `/reels` | List reels (filters: language, level, topic, tags, user_id) |
| `GET` | `/reels/saved` | Get user's saved reels |
| `GET` | `/reels/user/{user_id}` | Get reels uploaded by a user |
| `GET` | `/reels/{reel_id}` | Get reel detail with caption segments |
| `GET` | `/reels/{reel_id}/stream` | Stream video (HTTP range requests / R2 redirect) |
| `GET` | `/reels/{reel_id}/thumbnail` | Serve thumbnail (local or R2 presigned URL) |
| `GET` | `/reels/{reel_id}/captions` | Get caption segments (auto-translate if needed) |
| `POST` | `/reels/{reel_id}/transcribe` | Manually trigger Groq Whisper transcription |
| `POST` | `/reels/{reel_id}/like` | Toggle like |
| `POST` | `/reels/{reel_id}/save` | Toggle save |
| `DELETE` | `/reels/{reel_id}` | Delete reel (owner only, removes R2 files) |

### Words
| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/words` | Save a word (auto-fetches definition) |
| `GET` | `/words` | List words (`due_only=true` for SM-2 review queue) |
| `GET` | `/words/lookup` | Look up definition + translation without saving |
| `GET` | `/words/{word_id}` | Get word detail |
| `POST` | `/words/{word_id}/review` | Submit SM-2 review (quality 0–5) |
| `DELETE` | `/words/{word_id}` | Delete word |

### Profiles
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/profiles/me` | Get own profile (creates one if missing) |
| `PUT` | `/profiles/me` | Update username, bio, avatar_initials |
| `GET` | `/profiles/me/stats` | Vocabulary count, streak, hours watched, weekly activity, language breakdown |
| `GET` | `/profiles/search` | Search profiles by username |
| `GET` | `/profiles/{user_id}` | Get another user's profile |
| `POST` | `/profiles/{user_id}/follow` | Toggle follow/unfollow |

### Feed
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/feed` | Get personalized feed from `user_feed_queue`; falls back to latest reels |
| `POST` | `/feed/consumed/{reel_id}` | Mark reel as consumed in queue |
| `POST` | `/feed/refill` | Check queue size; triggers AI agent if < 5 remaining |

### Activity
| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/activity/log` | Log watch_time_ms, reels_watched, words_saved; updates streak |

### Search
| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/search` | Search YouTube for videos with captions |
| `GET` | `/search/details` | Get video details (duration, view count) by YouTube ID |

---

## Room Database (Android — 5 entities)

| Entity | Table | Purpose |
|--------|-------|---------|
| `WordEntity` | `words` | Cached vocabulary with SM-2 fields |
| `ReelEntity` | `reels` | Cached reel metadata |
| `CaptionEntity` | `caption_segments` | Cached caption segments |
| `UserProfileEntity` | `user_profiles` | Cached user profile |
| `PracticeSessionEntity` | `practice_sessions` | Local practice session history |

---

## Tests

The backend has integration tests using `pytest` and FastAPI's `TestClient` against an in-memory SQLite database.

### Test files
- `tests/test_api_reels.py` — reel CRUD, upload, like/unlike toggle, captions
- `tests/test_api_words.py` — word save, lookup, SM-2 review
- `tests/test_api_profiles.py` — profile creation, update, follow/unfollow
- `tests/test_api_feed.py` — feed retrieval, consumed marking

### Running tests
```bash
cd backend
python -m pytest tests/ -v
```

---

## Setup & Running Locally

### Prerequisites
- Python 3.12
- Docker + Docker Compose
- Android Studio (Flamingo or newer)
- JDK 17

### Backend (Docker Compose)

```bash
# 1. Copy environment file
cp backend/.env.example backend/.env
# 2. Fill in: GROQ_API_KEY, YOUTUBE_API_KEY, R2 credentials

# 3. Start all services
cd backend
docker-compose up --build

# Backend runs on http://localhost:8000
# AI agent runs on http://localhost:8001
```

### Backend (without Docker)

```bash
cd backend
python -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt

# Set DATABASE_URL to your PostgreSQL instance in .env
alembic upgrade head
uvicorn app.main:app --reload --port 8000
```

### Android App

1. Open `android/` in Android Studio
2. Add `google-services.json` (from Firebase Console) to `android/app/`
3. In `ApiClient.kt` update `BASE_URL` if running a local backend
4. Run on emulator or device (API 26+)

### Environment Variables

| Variable | Service | Description |
|----------|---------|-------------|
| `DATABASE_URL` | backend, AI | PostgreSQL connection string |
| `GROQ_API_KEY` | backend | Groq Whisper transcription |
| `YOUTUBE_API_KEY` | backend, AI | YouTube Data API v3 |
| `AWS_ACCESS_KEY_ID` | backend | Cloudflare R2 key |
| `AWS_SECRET_ACCESS_KEY` | backend | Cloudflare R2 secret |
| `R2_BUCKET_NAME` | backend | R2 bucket name |
| `R2_ENDPOINT_URL` | backend | R2 endpoint (Cloudflare account URL) |
| `BACKEND_URL` | AI | Internal URL to backend (default: `http://api:8000`) |
| `SCHEDULER_INTERVAL_MINUTES` | AI | Feed curation interval (default: 30) |

---

## Deployment

### Railway

The backend and AI agent are deployed on [Railway](https://railway.app):

- **Backend service** — Docker build from `backend/`, exposes port 8000
- **AI agent service** — Docker build from `reelang_ai/`, exposes port 8001
- **PostgreSQL** — Railway-managed PostgreSQL 16 instance
- Production URL: `https://reelang-production.up.railway.app/api/v1/`

### Cloudflare R2

Videos and thumbnails are stored in Cloudflare R2 (S3-compatible object storage):
- `reels/<reel_id>.mp4` — video files
- `thumbnails/<reel_id>.jpg` — thumbnail images
- Access via pre-signed URLs generated with `boto3`
