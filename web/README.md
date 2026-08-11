# ReeLang Web

Browser client for ReeLang, built on the same FastAPI backend and Firebase project as the Android app. Every screen of the mobile app has a web counterpart; the palette, layout, and interaction model are ported from the Jetpack Compose UI.

- **Stack** — React 18 + TypeScript + Vite, React Router 6, Firebase Web SDK (Auth only)
- **State** — component state plus three small contexts (session, toast, words badge). No Redux, no data-fetching library.
- **Styling** — hand-written CSS with the Compose palette as custom properties (`src/theme.css`). No UI framework.

---

## Screen parity with Android

| Android screen | Web route | Notes |
|---|---|---|
| `AuthScreen` | `/auth` | Email/password, Google popup, optional demo button |
| `OnboardingScreen` | `/onboarding` | Language + level, stored in `localStorage` |
| `ReelsScreen` | `/feed` | Scroll-snap vertical pager |
| `ReelsScreen` (from search) | `/feed/from-search/:reelIds` | Priority ordering preserved |
| `ReelsScreen` (saved reel) | `/reel/:reelId` | Single reel |
| `ReelsScreen` (user reels) | `/user-reels/:userId/:startReelId` | Starts on the tapped reel |
| `SearchScreen` | `/search` | Reels tab (language/level/tag filters) + Profiles tab |
| `WordsScreen` | `/words` | All / Learning / Mastered, delete, CSV export |
| `WordDetailScreen` | `/words/:wordId` | Lookup, flip card, SM-2 review state |
| `PracticeScreen` | `/practice` | Flashcards feeding SM-2 (`quality` 4 / 1) |
| `ProfileScreen` | `/profile`, `/profile/:userId` | Posts / Saved / Private tabs, follow button |
| `StatsScreen` | `/stats` | Weekly bar chart, streak, hours, language breakdown |
| `SettingsScreen` | `/settings` | Edit profile, sign out |
| `CreateReelScreen` | `/create` | Upload with the same size limits as `FileValidator.kt` |

### Where the web build deliberately differs

- **Private gallery** (`ProfileScreen` tab 3) reads files from Android device storage. A browser has no equivalent, so the tab explains that instead of showing a grid.
- **Video playback** uses the YouTube IFrame API instead of the Android YouTube Player, and a plain `<video>` element instead of ExoPlayer. Reels start **muted** because browsers block audible autoplay — a "Tap for sound" chip unmutes.
- **Room offline cache** has no counterpart. All reads go to the API.
- **Text-to-speech** uses the Web Speech API instead of Android TTS.
- **UI language** is English throughout. The Android `SearchScreen` mixes Polish labels into an otherwise English app; the web build uses English consistently.

### API contract note

The Kotlin models in `android/.../network/models/` declare fields the backend never returns (`avatar_emoji`, `original_text`, `clickable_word`, `likes`, `saves`, `streak_days`, `progress`, `saved_at`, …). Gson fills them from defaults, so the app compiles and shows zeros. The TypeScript types in `src/api/types.ts` follow `backend/app/schemas.py` instead, and derive the missing values:

- avatar emoji ← `sceneEmojiFor(language)`
- owner username / initials ← a separate `GET /profiles/{id}` call
- streak ← `GET /profiles/me`
- word progress ← `repetitions / 3` (`words.py` promotes to `mastered` at 3)

`POST /words` also takes no `definition` field — the backend fetches it — so the web client sends only `term`, `language`, `reel_id`.

---

## Local development

```bash
cd web
npm install
cp .env.example .env.local   # then fill in the Firebase values
npm run dev                  # http://localhost:5173
```

```bash
npm run build       # tsc --noEmit && vite build
npm run typecheck   # types only
```

### Environment variables

See `.env.example`. `VITE_API_BASE_URL` defaults to the Railway deployment. The Firebase values come from **Firebase Console → Project settings → Your apps → Web app**.

---

## Deployment

The frontend is a static bundle; the backend stays on Railway.

### 1. Vercel

- Import the repo, set **Root Directory** to `web/`
- Framework preset: Vite (`vercel.json` already pins build command, output dir, and the SPA rewrite)
- Add every `VITE_*` variable from `.env.example` under Settings → Environment Variables

### 2. Backend CORS (required)

The API currently rejects browser origins — `CORS_ALLOWED_ORIGINS` is empty, so preflight returns `400 Disallowed CORS origin`. In the Railway backend service add:

```
CORS_ALLOWED_ORIGINS=https://<your-app>.vercel.app,http://localhost:5173
```

No code change is needed; `app/main.py` already reads this variable.

### 3. Firebase (required)

- **Authentication → Sign-in method** — enable **Email/Password**. It is currently disabled: the Identity Toolkit returns `PASSWORD_LOGIN_DISABLED`, which blocks sign-in, registration, and the demo account.
- **Authentication → Settings → Authorized domains** — add the Vercel domain, otherwise sign-in fails on the deployed site.
- **Google sign-in** on the web needs the Web OAuth client, which exists in the project already (`client_type: 3` in `google-services.json`).

### 4. Demo account

Create one user in Firebase Auth, seed it by using the app (save a handful of words, like a few reels), then set `VITE_DEMO_EMAIL` / `VITE_DEMO_PASSWORD` in Vercel. The sign-in screen then shows a **"Try the demo — no signup"** button; leaving the variables blank hides it.

---

## Project layout

```
src/
  api/          client.ts (fetch + Firebase bearer token), reelang.ts (endpoints), types.ts
  components/   Icons, common widgets, BottomNav, ReelPlayer, CaptionOverlay
  lib/          session, toast, wordsBadge, format, tts, youtube, fileValidator
  screens/      one file per screen
  theme.css     palette + shared classes
  App.tsx       routes and auth guards
```
