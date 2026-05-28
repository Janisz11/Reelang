# Dokumentacja Techniczna — ReeLang

---

## 1. Opis projektu

ReeLang to aplikacja mobilna na system Android, której celem jest nauka języków obcych poprzez oglądanie krótkich filmów. Użytkownik przewija pionowy feed w stylu TikTok/Reels, ogląda materiały językowe z napisami i może dotknąć dowolnego słowa w napisie, aby natychmiast sprawdzić jego definicję i zapisać je do swojego słownika. Zapisane słowa są następnie powtarzane metodą fiszek z algorytmem spaced-repetition SM-2.

Backend (FastAPI + PostgreSQL) odpowiada za przechowywanie danych, transkrypcję audio (Groq Whisper), tłumaczenia i streaming wideo. Osobny mikroserwis AI (reelang_ai) co 30 minut wyszukuje nowe filmy na YouTube i uzupełnia kolejkę feedu dla każdego użytkownika.

---

## 2. Architektura systemu

```
┌──────────────────────────────────────────────────────────────┐
│                 Aplikacja Android (Kotlin)                   │
│                                                              │
│  Firebase Authentication  ──►  Firebase (Google Cloud)       │
│  Retrofit2 + OkHttp                                          │
│  AuthInterceptor  ──►  dodaje X-User-Id do każdego żądania   │
│  Room Database  ──►  cache offline (5 encji)                 │
│  ExoPlayer  ──►  lokalny streaming wideo                     │
│  YouTube Player API  ──►  filmy z YouTube                   │
└────────────────────┬─────────────────────────────────────────┘
                     │ HTTPS
                     │ https://reelang-production.up.railway.app/api/v1/
                     ▼
┌──────────────────────────────────────────────────────────────┐
│               Backend FastAPI (Python 3.12)                  │
│                                                              │
│  Routery: /reels  /words  /profiles  /feed                   │
│           /activity  /search  /admin                         │
│                                                              │
│  SQLAlchemy 2.0  ──►  PostgreSQL 16                          │
│  yt-dlp          ──►  pobieranie wideo z YouTube             │
│  Groq Whisper    ──►  transkrypcja audio                     │
│  deep-translator ──►  tłumaczenie napisów                    │
│  boto3           ──►  Cloudflare R2 (przechowywanie wideo)   │
│  Alembic         ──►  migracje schematu bazy danych          │
└────────────────────┬────────────────────────────────────────-┘
                     │ HTTP (sieć wewnętrzna Docker)
                     ▼
┌──────────────────────────────────────────────────────────────┐
│              Agent AI — reelang_ai (Python)                  │
│                                                              │
│  FastAPI (port 8001)  ──►  endpoint POST /trigger/{user_id}  │
│  APScheduler AsyncIOScheduler  ──►  co 30 minut              │
│  user_profiler  ──►  buduje profil użytkownika z bazy danych  │
│  feed_curator   ──►  wyszukuje YouTube Shorts i importuje    │
│  youtube_source ──►  YouTube Data API v3                     │
└────────────────────┬─────────────────────────────────────────┘
                     │ współdzielona baza danych
                     ▼
              PostgreSQL 16
              (tabela user_feed_queue łączy AI z backendem)
```

---

## 3. Stos technologiczny

### 3.1 Android

| Technologia | Wersja / szczegóły |
|-------------|-------------------|
| Kotlin | Language |
| Jetpack Compose | UI (Material3) |
| Navigation Compose | Nawigacja ekranów |
| ViewModel + StateFlow | MVVM — warstwa prezentacji |
| Room | Lokalna baza danych SQLite (cache offline) |
| Firebase Authentication | Logowanie (email + Google) |
| Retrofit2 | Klient HTTP REST |
| OkHttp | Interceptory (AuthInterceptor) |
| ExoPlayer (Media3) | Odtwarzanie wideo lokalnego / stream |
| YouTube Android Player | Odtwarzanie filmów z YouTube |
| Coil | Ładowanie obrazów (miniatur) |

### 3.2 Backend

| Technologia | Wersja |
|-------------|--------|
| Python | 3.12 |
| FastAPI | 0.111.0 |
| uvicorn | 0.29.0 (ASGI) |
| SQLAlchemy | 2.0.30 |
| Alembic | 1.13.1 |
| psycopg2-binary | PostgreSQL driver |
| yt-dlp | ≥2025.1.1 (YouTube download) |
| Groq | Whisper API (transkrypcja) |
| deep-translator | 1.11.4 (tłumaczenia) |
| boto3 | 1.34.0 (Cloudflare R2) |
| httpx | 0.27.0 |
| pydantic | 2.7.1 |
| pytest | 8.2.0 |

### 3.3 Agent AI

| Technologia | Opis |
|-------------|------|
| APScheduler | AsyncIOScheduler — harmonogram zadań |
| httpx | Async HTTP (wywołania backendu) |
| YouTube Data API v3 | Wyszukiwanie filmów (search + videos.list) |
| FastAPI | Endpoint /trigger/{user_id} |

### 3.4 Infrastruktura

| Komponent | Rozwiązanie |
|-----------|-------------|
| Hosting backendu | Railway |
| Przechowywanie wideo | Cloudflare R2 (S3-compatible) |
| Baza danych produkcyjna | PostgreSQL 16 (Railway) |
| Konteneryzacja lokalna | Docker Compose |

---

## 4. Ekrany aplikacji

### 4.1 AuthScreen (`auth/AuthScreen.kt`)

Ekran logowania/rejestracji. Zawiera zakładki "Sign In" i "Register". Obsługuje:
- Logowanie email + hasło przez Firebase Auth
- Rejestrację nowego konta
- Logowanie Google (GoogleSignInClient + Firebase Credential)
- Wyświetlanie błędów inline

Po pomyślnym zalogowaniu przekierowuje na `OnboardingScreen` (nowy użytkownik) lub `FeedScreen` (powracający).

### 4.2 OnboardingScreen (`ui/onboarding/OnboardingScreen.kt`)

Wybór języka docelowego (ES/FR/DE/JA/EN/PT/PL) i poziomu CEFR (A1–C2) po pierwszym logowaniu. Dane są zapisywane do preferencji i używane przez feed do filtrowania treści.

### 4.3 ReelsScreen / Feed (`ui/reels/ReelsScreen.kt`)

Główny ekran aplikacji — pionowy pager (VerticalPager) filmów. Funkcje:
- Automatyczne ładowanie napisów dla aktywnego reel'a
- Oznaczanie obejrzanego reel'a jako `consumed` w kolejce feedu
- Periodyczna synchronizacja aktywności co 30 sekund (watch_time_ms, reels_watched)
- Obsługa obu typów wideo: YouTube (YouTubeView) i upload lokalny (ExoPlayer)
- Przyciski: Like, Save, Share
- Tapnięcie słowa w napisie → lookup → zapis do słownika
- Nawigacja do profilu właściciela reel'a

### 4.4 SearchScreen (`ui/search/SearchScreen.kt`)

Wyszukiwanie filmów na YouTube przez API backendu. Pozwala:
- Wpisać zapytanie i filtrować po języku
- Przeglądać wyniki z miniaturami i tytułami
- Uruchomić wybrane filmy w feedzie (`feed_from_search/{reelIds}`)

### 4.5 WordsScreen (`ui/words/WordsScreen.kt`)

Lista zapisanych słów z zakładkami All / Learning / Mastered. Funkcje:
- Swipe w lewo → dialog usunięcia słowa
- Pasek postępu SM-2 przy każdym słowie
- Przycisk TTS (Text-To-Speech) przy każdym słowie
- Przycisk "Practice Now" → nawigacja do PracticeScreen
- Cache w Room — działa offline

### 4.6 WordDetailScreen (`ui/words/WordDetailScreen.kt`)

Szczegóły zapisanego słowa: termin, język, definicja, tłumaczenie, status (learning/mastered), data następnej powtórki.

### 4.7 PracticeScreen (`ui/words/PracticeScreen.kt`)

Sesja fiszek (flashcard). Dla każdego słowa:
- Karta frontowa: termin w języku docelowym (czerwone tło); TTS automatycznie wymawia słowo
- Karta tylna (po tapnięciu): tłumaczenie EN + definicja
- Przyciski: "I know it" (quality=4) i "Don't know" (quality=1) → wywołanie `POST /words/{id}/review`
- Po zakończeniu wyświetla ekran wynikowy (znane / nieznane / %)
- Sesja zapisywana lokalnie do `PracticeSessionEntity` w Room

### 4.8 ProfileScreen (`ui/profile/ProfileScreen.kt`)

Profil użytkownika (własny lub innego). Zawiera:
- Avatar (inicjały), nazwa, poziom
- Liczniki: followers, following, likes
- Przycisk Follow/Following (dla obcych profili)
- Zakładki: Posts (siatka miniatur uploadów) / Saved (zapisane reele) / Private (galeria z assets)
- FAB "Create" → CreateReelScreen (tylko własny profil)
- Long-press na miniaturze → dialog usunięcia (tylko własny profil)

### 4.9 StatsScreen (`ui/profile/StatsScreen.kt`)

Szczegółowe statystyki uczenia się:
- Liczba opanowanych słów, streak dni, godziny oglądania
- Słupkowy wykres aktywności z ostatnich 7 dni
- Breakdown języków (flagi + pasek postępu + %)

### 4.10 SettingsScreen (`ui/profile/SettingsScreen.kt`)

Ustawienia aplikacji i przycisk wylogowania (Firebase signOut + nawigacja do `auth`).

### 4.11 CreateReelScreen (`ui/create/CreateReelScreen.kt`)

Tworzenie/upload własnego reel'a:
- Wybór pliku wideo/obrazu z galerii
- Pola: tytuł, język, tagi
- Multipart upload do `POST /reels/upload`
- Po zapisaniu przekierowuje na profil

---

## 5. Baza danych

### 5.1 PostgreSQL (backend)

#### Tabela `reels`
| Kolumna | Typ | Opis |
|---------|-----|------|
| id | STRING PK | UUID |
| youtube_id | STRING UNIQUE | ID wideo na YouTube (null dla uploadów) |
| title | STRING | Tytuł |
| channel_name | STRING | Nazwa kanału |
| thumbnail_url | STRING | URL miniatury |
| duration_ms | INTEGER | Czas trwania w ms |
| language | STRING | Kod języka (iso 639-1) |
| level | STRING | Poziom CEFR (A1–C2) |
| topic | STRING | Temat |
| tags | STRING | Tagi oddzielone przecinkami |
| file_path | STRING | Lokalna ścieżka (upload) |
| r2_key | STRING | Klucz obiektu w Cloudflare R2 |
| r2_thumb_key | STRING | Klucz miniatury w R2 |
| owner_user_id | STRING | Firebase UID właściciela |
| likes_count | INTEGER | Liczba polubień |
| created_at | DATETIME | Data dodania |

#### Tabela `caption_segments`
| Kolumna | Typ | Opis |
|---------|-----|------|
| id | STRING PK | UUID |
| reel_id | STRING FK | → reels.id (CASCADE DELETE) |
| start_ms | INTEGER | Czas początku segmentu |
| end_ms | INTEGER | Czas końca segmentu |
| original_text | TEXT | Tekst oryginalny |
| translated_text | TEXT | Tłumaczenie (null jeśli jeszcze niezlecone) |

#### Tabela `words`
| Kolumna | Typ | Opis |
|---------|-----|------|
| id | STRING PK | UUID |
| user_id | STRING | Firebase UID użytkownika |
| term | STRING | Termin |
| definition | TEXT | Definicja (słownik) |
| language | STRING | Język terminu |
| status | ENUM | `learning` / `mastered` |
| reel_id | STRING FK | → reels.id (SET NULL) |
| segment_id | STRING FK | → caption_segments.id (SET NULL) |
| repetitions | INTEGER | Liczba powtórzeń (SM-2) |
| easiness | FLOAT | Współczynnik łatwości (SM-2, min 1.3) |
| interval_days | INTEGER | Interwał powtórki w dniach (SM-2) |
| next_review | DATETIME | Data następnej powtórki |
| created_at | DATETIME | Data zapisu |

#### Tabela `profiles`
| Kolumna | Typ | Opis |
|---------|-----|------|
| user_id | STRING PK | Firebase UID |
| username | STRING | Nazwa wyświetlana |
| bio | STRING | Opis profilu |
| avatar_initials | STRING | Inicjały do awatara |
| followers_count | INTEGER | Liczba obserwujących |
| following_count | INTEGER | Liczba obserwowanych |
| total_likes | INTEGER | Suma polubień |
| level | INTEGER | Poziom gracza |
| streak_days | INTEGER | Aktualny streak dzienny |
| created_at | DATETIME | Data rejestracji |

#### Tabela `follows`
| Kolumna | Typ | Opis |
|---------|-----|------|
| follower_id | STRING PK | Firebase UID obserwującego |
| following_id | STRING PK | Firebase UID obserwowanego |
| created_at | DATETIME | Data obserwacji |

#### Tabela `reel_likes`
| Kolumna | Typ | Opis |
|---------|-----|------|
| user_id | STRING PK | Firebase UID |
| reel_id | STRING PK | → reels.id (CASCADE DELETE) |
| created_at | DATETIME | Data polubienia |

#### Tabela `saved_reels`
| Kolumna | Typ | Opis |
|---------|-----|------|
| user_id | STRING PK | Firebase UID |
| reel_id | STRING PK | → reels.id (CASCADE DELETE) |
| created_at | DATETIME | Data zapisania |

#### Tabela `activity_logs`
| Kolumna | Typ | Opis |
|---------|-----|------|
| id | STRING PK | UUID |
| user_id | STRING | Firebase UID |
| date | DATE | Data aktywności |
| watch_time_ms | BIGINT | Czas oglądania w ms |
| reels_watched | INTEGER | Liczba obejrzanych reel'i |
| words_saved | INTEGER | Liczba zapisanych słów |

#### Tabela `user_feed_queue`
| Kolumna | Typ | Opis |
|---------|-----|------|
| id | UUID | Identyfikator |
| user_id | STRING PK | Firebase UID |
| reel_id | STRING PK | → reels.id |
| score | FLOAT | Wynik relevancji (wyższy = wyższy priorytet) |
| consumed | BOOLEAN | Czy reel został obejrzany |
| added_at | TIMESTAMP | Data dodania przez agenta AI |

### 5.2 Room (Android — cache offline)

#### `WordEntity` (tabela `words`)
id, term, definition, translation, language, status, repetitions, easiness, intervalDays, reelId, createdAt

#### `ReelEntity` (tabela `reels`)
id, youtubeId, title, channelName, thumbnailUrl, language, level, tags, durationMs, likesCount, isLiked, cachedAt

#### `CaptionEntity` (tabela `caption_segments`)
Segmenty napisów powiązane z reel'em.

#### `UserProfileEntity` (tabela `user_profiles`)
userId, username, avatarInitials, followersCount, followingCount, totalLikes, level, streakDays

#### `PracticeSessionEntity` (tabela `practice_sessions`)
id (autoGenerate), userId, knownCount, unknownCount, totalCards, completedAt

---

## 6. API REST

Prefix wszystkich endpointów: `/api/v1/`

Autoryzacja: nagłówek `X-User-Id` zawierający Firebase UID (dodawany przez `AuthInterceptor`).

### 6.1 Endpointy reeli

| Metoda | Ścieżka | Opis |
|--------|---------|------|
| `POST` | `/reels/import` | Importuje wideo z YouTube (podaj URL); asynchroniczna transkrypcja Whisper w tle |
| `POST` | `/reels/upload` | Upload pliku wideo/obrazu (multipart); asynchroniczna transkrypcja |
| `GET` | `/reels` | Lista reel'i z filtrami: `language`, `level`, `topic`, `tags`, `user_id`, `limit`, `offset` |
| `GET` | `/reels/saved` | Zapisane reele użytkownika (param: `user_id`) |
| `GET` | `/reels/user/{user_id}` | Reele wgrane przez danego użytkownika |
| `GET` | `/reels/{reel_id}` | Szczegóły reel'a wraz z segmentami napisów |
| `GET` | `/reels/{reel_id}/stream` | Streaming wideo — HTTP Range requests lub redirect do presigned URL R2 |
| `GET` | `/reels/{reel_id}/thumbnail` | Miniatura — lokalny plik lub redirect do R2 |
| `GET` | `/reels/{reel_id}/captions` | Segmenty napisów (auto-tłumaczenie brakujących tłumaczeń) |
| `POST` | `/reels/{reel_id}/transcribe` | Ręczne wyzwolenie transkrypcji Groq Whisper |
| `POST` | `/reels/{reel_id}/like` | Toggle polubienia (aktualizuje likes_count + total_likes właściciela) |
| `POST` | `/reels/{reel_id}/save` | Toggle zapisania reel'a |
| `DELETE` | `/reels/{reel_id}` | Usunięcie reel'a (tylko właściciel; kasuje pliki z dysku i R2) |

### 6.2 Endpointy słów

| Metoda | Ścieżka | Opis |
|--------|---------|------|
| `POST` | `/words` | Zapis słowa; auto-pobranie definicji ze słownika; deduplikacja per user+term+lang |
| `GET` | `/words` | Lista słów (nagłówek `X-User-Id`); parametr `due_only=true` — tylko zaległe do SM-2 |
| `GET` | `/words/lookup` | Definicja + tłumaczenie słowa bez zapisu (params: `term`, `language`, `target_lang`) |
| `GET` | `/words/{word_id}` | Szczegóły słowa |
| `POST` | `/words/{word_id}/review` | Ocena SM-2 (body: `{"quality": 0-5}`); aktualizuje repetitions/easiness/interval/next_review |
| `DELETE` | `/words/{word_id}` | Usunięcie słowa |

### 6.3 Endpointy profili

| Metoda | Ścieżka | Opis |
|--------|---------|------|
| `GET` | `/profiles/me` | Własny profil (auto-tworzony jeśli nie istnieje) |
| `PUT` | `/profiles/me` | Aktualizacja username, bio, avatar_initials |
| `GET` | `/profiles/me/stats` | Statystyki: vocabulary_mastered, streak_days, hours_watched, weekly_activity (7 dni), target_languages |
| `GET` | `/profiles/search` | Wyszukiwanie profili po username (param: `q`) |
| `GET` | `/profiles/{profile_user_id}` | Profil innego użytkownika; pole `is_following` jeśli podano `user_id` |
| `POST` | `/profiles/{profile_user_id}/follow` | Toggle obserwowania; aktualizuje followers_count i following_count |

### 6.4 Endpointy feedu

| Metoda | Ścieżka | Opis |
|--------|---------|------|
| `GET` | `/feed` | Pobiera reel'e z `user_feed_queue` posortowane po score DESC; fallback na najnowsze reel'e |
| `POST` | `/feed/consumed/{reel_id}` | Oznacza reel jako obejrzany (consumed=true) |
| `POST` | `/feed/refill` | Sprawdza liczebność kolejki; jeśli < 5 — wywołuje `POST reelang_ai:8001/trigger/{user_id}` |

### 6.5 Endpointy aktywności

| Metoda | Ścieżka | Opis |
|--------|---------|------|
| `POST` | `/activity/log` | Loguje aktywność dnia (watch_time_ms, reels_watched, words_saved); aktualizuje streak |

### 6.6 Endpointy wyszukiwania

| Metoda | Ścieżka | Opis |
|--------|---------|------|
| `GET` | `/search` | Wyszukiwanie YouTube (params: `q`, `language`, `max_results`, `page_token`) |
| `GET` | `/search/details` | Szczegóły wideo YouTube po ID (param: `ids` — oddzielone przecinkami) |

---

## 7. Agent AI (reelang_ai)

### Struktura

```
reelang_ai/
├── main.py               # FastAPI app + startup (APScheduler + initial curation)
├── config.py             # Zmienne środowiskowe (YOUTUBE_API_KEY, BACKEND_URL, interwały)
├── database.py           # SessionLocal dla PostgreSQL
├── agents/
│   └── feed_curator.py   # Logika kuracji feedu
├── recommenders/
│   └── user_profiler.py  # Budowanie profilu użytkownika z bazy danych
├── scheduler/
│   └── jobs.py           # Definicja zadania APScheduler
└── sources/
    └── youtube_source.py # Wyszukiwanie YouTube Shorts
```

### Przepływ pracy agenta

1. **APScheduler** uruchamia `run_curation_job()` co 30 minut
2. `user_profiler.get_users_needing_refill()` — zapytanie SQL: użytkownicy z < 5 nieobejrzanych reel'i w kolejce + aktywni w ostatnich 7 dniach bez kolejki
3. Dla każdego użytkownika `curate_feed_for_user()`:
   - `get_user_profile()` — buduje profil z bazy: primary_language (z watch_time_ms lub words), level, top_tags (z tagów obejrzanych reel'i)
   - `search_youtube_shorts()` — losuje zapytanie z predefiniowanych dla języka, dodaje level hint lub tagi; filtruje wideo już istniejące w bazie
   - `import_reel_to_backend()` — wywołuje `POST /api/v1/reels/import`; asynchroniczna transkrypcja w tle
   - `enqueue_reel_for_user()` — wstawia rekord do `user_feed_queue`
4. **Endpoint `/trigger/{user_id}`** pozwala backendu (lub klientowi) natychmiastowo zainicjować kurację dla konkretnego użytkownika

### Konfiguracja (zmienne środowiskowe)

| Zmienna | Domyślna | Opis |
|---------|----------|------|
| `YOUTUBE_API_KEY` | — | Klucz YouTube Data API v3 |
| `BACKEND_URL` | `http://api:8000` | URL backendu |
| `DATABASE_URL` | — | PostgreSQL |
| `SCHEDULER_INTERVAL_MINUTES` | `30` | Częstotliwość cyklu kuracji |
| `QUEUE_MIN_SIZE` | `5` | Próg poniżej którego kolejka jest uzupełniana |
| `REELS_PER_RUN` | `5` | Liczba reel'i dodawanych na jednego użytkownika |

---

## 8. Algorytm SM-2

Implementacja w `backend/app/routers/words.py` (funkcja `sm2_update`) oraz w `ui/words/PracticeScreen.kt`.

### Zasada działania

Po każdej sesji powtórkowej użytkownik ocenia słowo w skali **0–5**:
- 0–2 → odpowiedź niepoprawna (fail)
- 3–5 → odpowiedź poprawna (pass)

Algorytm aktualizuje trzy parametry:

```
jeśli quality >= 3 (pass):
    jeśli repetitions == 0: new_interval = 1
    jeśli repetitions == 1: new_interval = 6
    w przeciwnym razie:    new_interval = round(interval_days * easiness)
    new_repetitions = repetitions + 1
jeśli quality < 3 (fail):
    new_interval = 1
    new_repetitions = 0

new_easiness = easiness + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))
new_easiness = max(1.3, new_easiness)

next_review = NOW() + timedelta(days=new_interval)
status = "mastered" jeśli repetitions >= 3
```

### Wartości quality w aplikacji
- Przycisk "I know it" → `quality = 4`
- Przycisk "Don't know" → `quality = 1`

---

## 9. Testy

### 9.1 Testy integracyjne backendu

Testy używają `pytest` z `FastAPI TestClient` i SQLite in-memory zamiast PostgreSQL.

**Konfiguracja (`tests/conftest.py`):**
- `db` fixture — tworzy schemat SQLite, tworzy tabelę `user_feed_queue`, czyści po teście
- `client` fixture — nadpisuje dependency `get_db` (DI FastAPI) na testową sesję SQLite
- `mock_fetch_definition` autouse — monkeypatching słownika (brak zewnętrznych wywołań)

**Pliki testów:**

| Plik | Co testuje |
|------|-----------|
| `test_api_reels.py` | GET /reels (puste, z filtrem), GET /reels/{id} 404, upload, owner_user_id, like/unlike toggle, GET /reels/{id}/captions (puste i z segmentami) |
| `test_api_words.py` | POST /words (zapis), GET /words, GET /words/{id}, DELETE, POST /words/{id}/review (SM-2) |
| `test_api_profiles.py` | GET /profiles/me (auto-create), PUT /profiles/me, GET /profiles/search, POST /profiles/{id}/follow (toggle) |
| `test_api_feed.py` | GET /feed (fallback gdy pusta kolejka), POST /feed/consumed, GET /feed/refill |

### 9.2 Uruchomienie testów

```bash
cd backend
python -m pytest tests/ -v
```

---

## 10. Instrukcja uruchomienia

### Wymagania
- Python 3.12
- Docker + Docker Compose
- Android Studio (Flamingo+)
- JDK 17
- Konto Firebase (plik `google-services.json`)
- Klucz YouTube Data API v3
- Klucz Groq API

### Backend (Docker Compose — lokalne środowisko pełne)

```bash
# 1. Skopiuj plik środowiskowy
cp backend/.env.example backend/.env

# 2. Uzupełnij zmienne w backend/.env:
#    DATABASE_URL, GROQ_API_KEY, YOUTUBE_API_KEY
#    AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, R2_BUCKET_NAME, R2_ENDPOINT_URL

# 3. Uruchom wszystkie serwisy
cd backend
docker-compose up --build

# Dostępne adresy:
# http://localhost:8000     - backend API
# http://localhost:8001     - agent AI
# http://localhost:8000/docs - dokumentacja Swagger
```

### Backend (bez Docker)

```bash
cd backend
python -m venv .venv
# Windows:
.venv\Scripts\activate
# Linux/macOS:
source .venv/bin/activate

pip install -r requirements.txt

# Skonfiguruj DATABASE_URL w .env (PostgreSQL)
alembic upgrade head
uvicorn app.main:app --reload --port 8000
```

### Agent AI (bez Docker)

```bash
cd reelang_ai
pip install -r requirements.txt
# Ustaw DATABASE_URL, YOUTUBE_API_KEY, BACKEND_URL w .env
python -m reelang_ai.main
```

### Aplikacja Android

1. Otwórz folder `android/` w Android Studio
2. Umieść `google-services.json` (z Firebase Console) w `android/app/`
3. W `network/ApiClient.kt` zmień `BASE_URL` jeśli używasz lokalnego backendu
4. Uruchom na emulatorze (API 26+) lub fizycznym urządzeniu

---

## 11. Wdrożenie produkcyjne

### Railway

Aplikacja jest wdrożona na platformie [Railway](https://railway.app):

- **Serwis `api`** — Docker build z `backend/`; Railway uruchamia `alembic upgrade head && uvicorn ...` przy starcie
- **Serwis `reelang_ai`** — Docker build z `reelang_ai/`
- **Baza danych** — PostgreSQL 16 zarządzany przez Railway
- Produkcyjny URL API: `https://reelang-production.up.railway.app/api/v1/`

### Cloudflare R2

Wideo i miniatury przechowywane w Cloudflare R2 (kompatybilny z S3):
- `reels/<reel_id>.mp4` — pliki wideo (upload)
- `reels/<reel_id>.jpg/.png/.webp` — pliki obrazów (upload)
- `thumbnails/<reel_id>.jpg` — miniatury

Dostęp przez presigned URL generowany przez `boto3` (`get_presigned_url`) — Android jest przekierowywany do R2 bez przeciążania serwera Railway.

### Zmienne środowiskowe (produkcja Railway)

| Zmienna | Serwis |
|---------|--------|
| `DATABASE_URL` | api, reelang_ai |
| `GROQ_API_KEY` | api |
| `YOUTUBE_API_KEY` | api, reelang_ai |
| `AWS_ACCESS_KEY_ID` | api |
| `AWS_SECRET_ACCESS_KEY` | api |
| `R2_BUCKET_NAME` | api |
| `R2_ENDPOINT_URL` | api |
| `BACKEND_URL` | reelang_ai |
| `SCHEDULER_INTERVAL_MINUTES` | reelang_ai |
