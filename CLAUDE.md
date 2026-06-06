# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

智能阅读系统 — a multi-module reading platform with Spring Boot backend, Vue 3 desktop frontend, Vue 3 mobile frontend, and a Python TTS microservice.

## Build & Run Commands

### Backend (Spring Boot)
```powershell
.\mvnw.cmd spring-boot:run                    # Windows
./mvnw spring-boot:run                        # Linux/macOS
.\mvnw.cmd -q -DskipTests compile             # Compile only (CI check)
.\mvnw.cmd test                               # Run tests (none exist yet)
```

### Desktop Frontend (reading-ui)
```powershell
cd reading-ui
npm ci
npm run dev       # Dev server at http://localhost:5173
npm run build     # Production build
```

### Mobile Frontend (reading-mobile-ui)
```powershell
cd reading-mobile-ui
npm ci
npm run dev       # Dev server at http://localhost:5174
npm run build     # Production build
```

### TTS Service (lightweight-tts-service)
```powershell
cd lightweight-tts-service
python -m pip install -r requirements.txt
python -m uvicorn app:app --host 0.0.0.0 --port 8091
# Or via Docker: docker compose -f docker-compose.tts.yml up -d
```

## Architecture

### Monorepo Layout

| Directory | Tech | Purpose |
|---|---|---|
| `src/main` | Java 17 / Spring Boot 3.2.2 | REST API, WebSocket, business logic |
| `reading-ui` | Vue 3 / Vite / Element Plus | Desktop web frontend |
| `reading-mobile-ui` | Vue 3 / Vite / Vant / Pinia | Mobile web frontend |
| `lightweight-tts-service` | Python / FastAPI / edge-tts | TTS microservice |

### Backend Package Structure (`com.example.reading`)

Standard layered architecture:
- `controller/` — REST endpoints. All controllers return `Result<T>` wrapper.
- `service/` + `service/impl/` — Business logic interfaces and implementations.
- `service/tts/` — TTS provider abstraction (`TtsProvider` interface, `LightweightTtsProvider`).
- `mapper/` — MyBatis-Plus mapper interfaces (scanned via `@MapperScan`).
- `entity/` — Database entities with Lombok annotations.
- `dto/` — Request/response DTOs.
- `config/` — Spring configuration (Security, MyBatis-Plus, WebSocket, WebMvc, TTS properties).
- `repository/` — Elasticsearch repository (`EsBookRepository`).
- `utils/` — Utilities (chapter parser, WebSocket handler).
- `common/` — Shared types (`Result`).

### Key Architectural Decisions

- **Security**: Spring Security uses `.anyRequest().authenticated()` with explicit `.permitAll()` for login/register/public endpoints. Authentication is handled at two levels: JWT filter sets SecurityContext for valid tokens (passes through if no token), and controllers verify ownership via `authContextService.currentUserId()`. CSRF disabled, stateless sessions.
- **ORM**: MyBatis-Plus with XML mappers in `src/main/resources/mapper/`. Entity-table mapping uses camelCase-to-underscore convention (`map-underscore-to-camel-case: true`).
- **Frontend proxy**: Both frontends proxy `/api`, `/ws`, `/files` to `http://localhost:8090` via Vite config. No `VITE_API_BASE_URL` env var is used. **Important**: Nginx `proxy_set_header` resets headers — `Authorization` must be explicitly forwarded (`$http_authorization`).
- **TTS abstraction**: `TtsProvider` interface with `LightweightTtsProvider` (FastAPI + edge-tts) via `tts.provider` config.
- **AI integration**: Dify API calls use two mechanisms:
  - `DifyChat` (injected as bean via `dify-spring-boot-starter` v2.2.0) — SSE streaming chat (`DifyAiController`) + blocking chat (`BookRecommendationServiceImpl`, `ReviewController.summary`)
  - `WebClient` direct calls — `NoteAiController` calls Dify Workflow API directly (not via `DifyWorkflow` bean)
  - `DifyDataset` — Knowledge base CRUD (`DifyKnowledgeBaseService`)
  - **Dify error detection pattern**: When parsing Dify blocking responses, check `response.containsKey("code") && !response.containsKey("data")` — Dify success responses also contain a `code` field, so checking for `code` alone causes false-positive errors.
  - **Critical**: Before using `DifyWorkflow`, verify the Dify app is actually a Workflow app. If it's a Chat Assistant, use `DifyChat.send()` instead — `runWorkflow()` will fail with `not_workflow_app`.
  - All API keys come from environment variables. Shared base URL configured in `dify.url` (falls back to `DIFY_KB_URL`).

### Note Review System (SM-2 Spaced Repetition)

- `NoteReviewServiceImpl` implements SM-2 algorithm for spaced repetition review.
- **Endpoints** (all require auth, ownership checked):
  - `POST /review/add` — add note to review queue (idempotent, @Transactional)
  - `DELETE /review/remove/{noteId}` — remove note from review
  - `GET /review/reviewed-note-ids` — returns `Set<Long>` of note IDs in review (excludes orphaned reviews for deleted notes)
  - `GET /review/today` — returns up to 20 notes due for review today
  - `POST /review/rate` — submit review score (0=forget, 3=vague, 5=remember)
  - `GET /review/stats` — returns `reviewNotes`, `todayPending`, `streakDays`, `totalNotes`
  - `POST /review/summary/{bookId}` — generate AI summary of a book's notes via `DifyChat` (uses `dify.reading.api-key`; limits to 50 notes)
  - `GET /review/summary/history` — list past summaries
- **Orphan filtering**: All review queries use `inSql("note_id", "SELECT id FROM sys_note WHERE user_id = ?")` to exclude reviews for deleted notes. The subquery is centralized in `validNoteIdsSubquery(userId)`.

### Tag System

- `NoteTagService` provides tag CRUD and note-tag binding:
  - `bindTags(noteId, tagIds)` — **incremental add only** (does not remove existing tags). @Transactional.
  - `setTags(noteId, tagIds)` — **full replacement** (delete all, insert new set). @Transactional.
  - `unbindTags(noteId, tagIds)` — remove specific tag bindings.
- `TagController` endpoints: `POST /tag/bind`, `POST /tag/unbind`, `GET /tag/list`
- Current frontend callers always send a superset of existing tags when binding, so `bindTags` add-only semantics are compatible.

### Note Relations

- `NoteRelation` entity with `@TableField("note_id_1")` / `@TableField("note_id_2")` explicit column mapping.
- `NoteRelationServiceImpl.createRelation` normalizes (smaller, larger) ordering and catches `DuplicateKeyException` for concurrent creation.
- Endpoints: `POST /noteRelation/create`, `GET /noteRelation/list/{noteId}`

### Markdown Rendering (Frontend)

- Both frontends share `utils/markdown.js` with `renderMarkdown(text)` — calls `marked.parse()` then `DOMPurify.sanitize()`.
- Used in Notes.vue (note content) and Insights.vue (AI reports).
- **Do not inline** `marked.parse` + `DOMPurify.sanitize` in components — import from `utils/markdown.js`.

### Knowledge Base Sync

- `DifyKnowledgeBaseService.syncOneBookToKb` is `@Async("kbSyncExecutor")` — processes chapters **sequentially** with `Thread.sleep(chapterDelayMs)` between each.
- Tracks `hasFailures` and logs aggregate completion status per book.
- Uses `DifyDataset` bean for CRUD operations on the Dify knowledge base.
- **Do not** replace sequential processing with raw `ScheduledExecutorService` — it causes thread leaks (no lifecycle management) and loses completion tracking.

### Mobile Frontend Patterns

- **State management**: Pinia `useAuthStore()` stores user info + token. All views read `userInfo` via `computed(() => authStore.user || {})` instead of reading `localStorage` directly. Login calls `authStore.login()`, logout calls `authStore.logout()`.
- **HTTP**: Use the shared `request` instance from `src/utils/request.js` (not raw `axios`). A global 401 response interceptor in `main.js` redirects to `/login` on token expiry.
- **Image caching**: `CachedImage.vue` component wraps `<img>` with IndexedDB-backed caching via `src/utils/imageCache.js`. Use it instead of `<van-image>` for user avatars and book covers. LRU eviction (max 50) with automatic blob URL revocation via `src/utils/lruCache.js`.
- **XSS**: Markdown rendered via `marked` must be sanitized with `DOMPurify.sanitize()` before `v-html`. Use the shared `utils/markdown.js` utility.
- **New views**: `MyBooks.vue` — user's uploaded books management (CRUD + review timeline).
- **New components**: `MobileAudioPlayer.vue` — extracted audio player bar used in Read.vue.
- **Shared utilities**: `utils/markdown.js` (renderMarkdown), `utils/request.js` (axios instance with interceptors), `utils/authHeaders.js` (token header builder).

### Desktop Frontend Patterns

- **Shared utilities**: Same as mobile — `utils/markdown.js`, `utils/request.js`, `utils/authHeaders.js`.
- **Sticky headers**: `.page-glass-container` uses `position: absolute` with internal scroll. Page headers use `position: sticky; top: 0` inside the container. Filter panels use `position: sticky` with `align-items: flex-start` on the flex parent.

### External Services

| Service | Port | Required For |
|---|---|---|
| MySQL (`smartreader` DB) | 3306 | Core data storage |
| Redis | 6379 | Caching |
| Elasticsearch | 9200 | Book search |
| TTS service | 8091 | Text-to-speech |
| Dify | External | AI features |

### Database

- Database name: `smartreader`, charset `utf8mb4`
- No complete init SQL exists. Supplementary scripts are in `src/main/resources/db/`.
- Execution order: `auth.sql` → `booklist.sql` → `social.sql` → `migration_user_upload.sql`

### Elasticsearch Note Search

- `NoteEsService.searchNotes` uses `ElasticsearchOperations` with `CriteriaQuery` for multi-field keyword matching (`selectedText`, `content`, `bookTitle`).
- Tags are **batch-fetched** for search results (2 queries total, not N+1) via `noteTagService.list()` with `in("note_id", noteIds)` + `tagService.listByIds(allTagIds)`.
- Returns `null` to signal the controller should fall back to MySQL when ES is unavailable.
- `EsNoteDoc` stores `tagIds` as `List<String>` for ES filtering.

### Environment Variables

Required (see `.env.example`):
- `MYSQL_PASSWORD` — MySQL root password
- `DIFY_BASE_URL` — Dify base URL for all apps (required, defaults to `http://localhost/v1`)
- `DIFY_CHAT_URL` — Dify chat endpoint
- `DIFY_READING_KEY` — Dify reading assistant key
- `DIFY_RECOMMEND_KEY` — Dify recommendation key
- `DIFY_GUIDE_KEY` — Dify guide key
- `DIFY_NOTE_KEY` — Dify note workflow key
- `DIFY_INSIGHT_KEY` — Dify insight workflow key
- `DIFY_KB_URL` / `DIFY_KB_KEY` / `DIFY_KB_DATASET_ID` — Knowledge base

Spring Boot does NOT auto-load `.env` files. Set via IDE run config, shell, or deployment platform.

## CI

GitHub Actions (`.github/workflows/test.yml`) runs compile/build checks only — no real MySQL/Redis/ES connections, no tests, no API keys needed. Four parallel jobs: backend compile, desktop build, mobile build, TTS Python check.

## Known Issues & Quirks

### Windows Scrollbar Layout Shift
Windows 垂直滚动条（~17px）在有/无滚动内容时出现/消失，会导致 `background-size: cover` 的背景图缩放和居中容器偏移，产生"不同页面容器宽度不一"的错觉。
**当前方案**: `.page-glass-container` 使用 `position: absolute; height: calc(100vh - 48px)` 脱离文档流，将滚动隔离在容器内部，避免 body 级滚动条。不要将该类改回流式布局。
