# validdoc

A document validation platform that combines template-defined, coordinate-based segments with OCR and a fixed rule catalog to deterministically derive document status.

An admin defines segments on a template (page and coordinates) and assigns each segment one or more rules from a fixed catalog (structural: letters/digits/length/date/signature-stamp; validated format: national ID, tax number, phone, email). An operator uploads a document against a template; the system evaluates each segment as filled-valid, filled-invalid, or empty, and derives the overall document status deterministically from the aggregate result.

For detailed requirements and architecture, see [`SRS.md`](SRS.md) and [`SDD.md`](SDD.md).

## Tech Stack

**Backend**
- Java 21, Spring Boot 4.1.0
- PostgreSQL 16, with schema managed by Flyway migrations
- Tesseract OCR (Tess4J), Turkish language pack
- OpenCV, for ink-density (signature/stamp) detection
- Spring Security with stateless JWT authentication

**Frontend**
- React 19 + Vite + TypeScript, Tailwind CSS 4 + shadcn/ui
- TanStack Query, react-hook-form + zod, React Router
- `pdfjs-dist` (client-side PDF page rendering/counting), `react-konva` (segment-drawing canvas)
- i18next (TR/EN), served in production by nginx behind a multi-stage Docker build

## Prerequisites

- Docker Desktop, running
- No local Java or Maven installation required; the project ships with a Maven wrapper (`mvnw.cmd`)
- No local Node.js installation required to run the full stack (the frontend builds inside its own Docker image); Node 22+ is only needed if you want to run the frontend outside Docker via `npm run dev` (see [Frontend Development](#frontend-development))

## Getting Started

### 1. Environment Configuration

Copy the example environment file:

```powershell
Copy-Item .env.example .env
```

Replace the placeholder values in `.env` with generated secrets:

```powershell
[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(64))   # JWT_SECRET
[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(32))   # ENCRYPTION_SECRET_KEY (AES-256, must be exactly 32 bytes)
[Convert]::ToBase64String([System.Security.Cryptography.RandomNumberGenerator]::GetBytes(18))   # POSTGRES_PASSWORD / BOOTSTRAP_ADMIN_PASSWORD
```

`.env` is excluded from version control via `.gitignore` and must never be committed.

### 2. Build and Run

```powershell
.\mvnw.cmd clean package -DskipTests
docker compose up -d --build
```

This builds and starts all three services defined in `docker-compose.yml`: `postgres` (5432), `app` — the Spring Boot backend (8080), and `frontend` — a static build served by nginx (5173). `docker compose up --build` does **not** compile Java source; it only copies the jar already produced by `mvnw.cmd package` into the backend image (see [Development Workflow](#development-workflow)). The frontend image, by contrast, *does* compile from source on every `--build` (its own multi-stage Dockerfile runs `npm run build` inside the image), so no separate frontend build step is needed before `docker compose up --build`.

Allow a few seconds for the application to finish starting before issuing requests. Once up, the frontend is reachable at `http://localhost:5173` and talks to the backend directly at `http://localhost:8080` (configured via the `VITE_API_BASE_URL` build arg, baked in at frontend image build time — see `docker-compose.yml`).

### 3. First Login

On first startup, if the `users` table is empty, a bootstrap admin account is created from `BOOTSTRAP_ADMIN_USERNAME` / `BOOTSTRAP_ADMIN_PASSWORD` in `.env`.

```powershell
$body = @{ username = "admin"; password = "<BOOTSTRAP_ADMIN_PASSWORD from .env>" } | ConvertTo-Json
$login = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/login" -Method Post -ContentType "application/json" -Body $body
$token = $login.token
```

Change the bootstrap password immediately after first login, since it is stored in plain text in `.env`:

```powershell
$changeBody = @{ currentPassword = "<current password>"; newPassword = "<new password>" } | ConvertTo-Json
Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:8080/api/users/me/password" -Method Put -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json" -Body $changeBody
```

## Development Workflow

`docker compose up --build` copies the existing jar from `target/` into the image; it does not compile Java source. After any code change, rebuild before restarting the container:

```powershell
.\mvnw.cmd clean package -DskipTests
docker compose up -d --build
```

## Frontend Development

For UI iteration with hot reload (faster than rebuilding the Docker image on every change):

```powershell
cd frontend
npm install
npm run dev
```

This runs Vite's dev server directly (default `http://localhost:5173`), separate from the `frontend` Docker service — stop the Docker `frontend` container first if both would bind the same port. The dev server reads `VITE_API_BASE_URL` from `frontend/.env` if present, otherwise defaults to `http://localhost:8080`; either way the backend (`app` + `postgres`) must already be running via `docker compose up -d`.

To rebuild and test the production frontend build (the one actually served by nginx in Docker) instead of the dev server:

```powershell
docker compose up -d --build frontend
```

## Database Migrations

The schema is managed by Flyway, not by Hibernate. Migration files live in `src/main/resources/db/migration` and follow the `V<n>__<description>.sql` naming convention; Flyway applies any not-yet-run migrations automatically on startup and records them in the `flyway_schema_history` table.

Hibernate runs with `spring.jpa.hibernate.ddl-auto=validate`, so it never alters the schema itself — it only verifies that the entity model matches what the migrations produced. If they disagree, the application fails to start rather than drifting silently.

To change the schema, add a new migration file with the next version number (e.g. `V2__add_document_note.sql`) alongside the entity change. Never edit a migration that has already been applied: Flyway stores a checksum of each file and refuses to start if a previously-run migration has changed.

To rebuild the database from scratch during development (this deletes all data):

```powershell
docker compose down -v
docker compose up -d
```

## Testing

```powershell
.\mvnw.cmd clean test
```

The test suite connects to the local Postgres instance; ensure it is running (`docker compose up -d`) before executing tests.

## API Reference

| Endpoint | Role | Description |
|---|---|---|
| `POST /api/auth/login` | Public | Issues a JWT |
| `PUT /api/users/me/password` | Admin | Changes the caller's own password (admin-only by design; operators don't self-manage passwords) |
| `GET /api/users` | Admin | Lists users (paginated) |
| `POST /api/users` | Admin | Creates a user account |
| `DELETE /api/users/{id}` | Admin | Deactivates a user (soft delete, `active=false`); blocked only for the last active admin |
| `GET /api/templates` | Operator/Admin | Lists templates (paginated) |
| `POST /api/templates` | Admin | Defines a template with segments and rules |
| `DELETE /api/templates/{id}` | Admin | Deactivates a template (soft delete, `active=false`); it can no longer be selected for new uploads |
| `GET /api/templates/{id}` | Operator/Admin | Returns a template's full segment and rule detail |
| `GET /api/templates/rule-types` | Admin | Returns the fixed rule catalog |
| `POST /api/templates/preview` | Admin | Previews segment extraction without persisting a template |
| `POST /api/documents/upload` | Operator/Admin | Uploads a document for asynchronous processing |
| `GET /api/documents` | Operator/Admin | Lists documents, newest first (paginated); admins see all, operators see only their own uploads |
| `GET /api/documents/{id}` | Operator/Admin | Returns document status and segment results |
| `GET /api/documents/{id}/segments/{segmentId}/image` | Operator/Admin | Returns a `PENDING_REVIEW` segment's stored crop image |
| `POST /api/documents/{id}/segments/{segmentId}/resolve` | Operator/Admin | Applies a one-time manual decision to a `PENDING_REVIEW` segment |
| `GET /api/documents/queue` | Operator/Admin | Lists documents in `PENDING_REVIEW` |
| `GET /api/documents/stats` | Operator/Admin | Returns dashboard counters (today's uploads, pending review, weekly validation rate); scope differs by role like `GET /api/documents` |
| `GET /api/admin/audit-logs` | Admin | Returns the audit log, newest first |
| `GET/PUT /api/admin/validation-settings` | Admin | Reads or updates retention period, ink-density threshold, and OCR confidence threshold |
| `GET /actuator/health` | Public | Health check |

## Frontend Integration

CORS is configured via `app.cors.allowed-origins` in `application.properties`, defaulting to `http://localhost:5173` unless the `CORS_ALLOWED_ORIGINS` environment variable is set. This default matches both the Vite dev server's default port and the port `docker-compose.yml` maps the production nginx `frontend` service to, so no change is needed for local development — set `CORS_ALLOWED_ORIGINS` to the real frontend origin for any other deployment (see [Production Configuration](#production-configuration)).

The frontend's nginx config (`frontend/nginx.conf`) serves static assets using the container's default `mime.types` table, plus one explicit addition for the `.mjs` extension (used by the pdf.js worker script — see `SDD.md` §9.4/§9.5). Avoid replacing this with a server-wide `types {}` block instead of the current per-extension `location` block — in nginx, a `types {}` block declared at server/location level fully replaces the inherited mime-type table rather than extending it, which would silently break the `Content-Type` of every other static asset (`.css`, `.svg`, `.woff`, etc.) served by that block.

`index.html` is served with `Cache-Control: no-cache, no-store, must-revalidate`. Routes are code-split (`React.lazy`) into content-hashed chunk files that change on every build; if a browser cached `index.html` across deploys, it would keep pointing at chunk filenames the server no longer has, surfacing as "Failed to fetch dynamically imported module" errors on navigation. `/assets/` (the hashed chunks themselves) are still cached for a year, since a changed file always gets a new filename.

## Production Configuration

An `application-prod.properties` profile is available for production deployments, activated by setting the `SPRING_PROFILES_ACTIVE=prod` environment variable on the `app` service. It disables SQL statement logging (`spring.jpa.show-sql=false`) and reduces log verbosity (`logging.level.root=WARN`). It is not enabled by default, since `docker-compose.yml` is used for local development as well — the compose file passes the variable through from the environment, so adding `SPRING_PROFILES_ACTIVE=prod` to `.env` is enough to switch, with no edit to the compose file itself.

Also set `CORS_ALLOWED_ORIGINS` to the real frontend origin in production — it defaults to `http://localhost:5173`, which is only correct for local development.

## Known Limitations

- Login and upload rate limiters are held in-memory per instance and are not shared across replicas; a distributed store (e.g. Redis) is required before horizontal scaling. See `SRS.md` §2.1.
- Secrets in `.env` are intended for local development only and must be rotated before any production deployment.