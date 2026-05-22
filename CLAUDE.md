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
| `reading-mobile-ui` | Vue 3 / Vite / Vant | Mobile web frontend |
| `lightweight-tts-service` | Python / FastAPI / edge-tts | TTS microservice |

### Backend Package Structure (`com.example.reading`)

Standard layered architecture:
- `controller/` — REST endpoints. All controllers return `Result<T>` wrapper.
- `service/` + `service/impl/` — Business logic interfaces and implementations.
- `service/tts/` — TTS provider abstraction (`TtsProvider` interface, `LightweightTtsProvider`, `DashScopeTtsProvider`).
- `mapper/` — MyBatis-Plus mapper interfaces (scanned via `@MapperScan`).
- `entity/` — Database entities with Lombok annotations.
- `dto/` — Request/response DTOs.
- `config/` — Spring configuration (Security, MyBatis-Plus, WebSocket, WebMvc, TTS properties).
- `repository/` — Elasticsearch repository (`EsBookRepository`).
- `utils/` — Utilities (chapter parser, WebSocket handler).
- `common/` — Shared types (`Result`).

### Key Architectural Decisions

- **Security**: Spring Security is configured to permit all requests (CSRF disabled, no authentication filter). Auth is handled at application level in `AuthController`/`AuthService`.
- **ORM**: MyBatis-Plus with XML mappers in `src/main/resources/mapper/`. Entity-table mapping uses camelCase-to-underscore convention.
- **Frontend proxy**: Both frontends proxy `/api`, `/ws`, `/files` to `http://localhost:8090` via Vite config. No `VITE_API_BASE_URL` env var is used.
- **TTS abstraction**: `TtsProvider` interface allows switching between lightweight (FastAPI) and DashScope providers via `tts.provider` config.
- **AI integration**: Uses Dify API for reading assistant and recommendations, DashScope/Qwen for other AI features. All API keys come from environment variables.

### External Services

| Service | Port | Required For |
|---|---|---|
| MySQL (`smartreader` DB) | 3306 | Core data storage |
| Redis | 6379 | Caching |
| Elasticsearch | 9200 | Book search |
| TTS service | 8091 | Text-to-speech |
| Dify / DashScope | External | AI features |

### Database

- Database name: `smartreader`, charset `utf8mb4`
- No complete init SQL exists. Supplementary scripts are in `src/main/resources/db/`.
- Execution order: `auth.sql` → `booklist.sql` → `social.sql` → `migration_user_upload.sql`

### Environment Variables

Required (see `.env.example`):
- `MYSQL_PASSWORD` — MySQL root password
- `QWEN_API_KEY` — DashScope API key
- `DIFY_CHAT_URL` — Dify chat endpoint
- `DIFY_READING_KEY` — Dify reading assistant key
- `DIFY_RECOMMEND_KEY` — Dify recommendation key

Spring Boot does NOT auto-load `.env` files. Set via IDE run config, shell, or deployment platform.

## CI

GitHub Actions (`.github/workflows/test.yml`) runs compile/build checks only — no real MySQL/Redis/ES connections, no tests, no API keys needed. Four parallel jobs: backend compile, desktop build, mobile build, TTS Python check.

## Known Issues & Quirks

### Windows Scrollbar Layout Shift
Windows 垂直滚动条（~17px）在有/无滚动内容时出现/消失，会导致 `background-size: cover` 的背景图缩放和居中容器偏移，产生"不同页面容器宽度不一"的错觉。
**当前方案**: `.page-glass-container` 使用 `position: absolute; height: calc(100vh - 48px)` 脱离文档流，将滚动隔离在容器内部，避免 body 级滚动条。不要将该类改回流式布局。
