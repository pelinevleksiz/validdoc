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
- springdoc-openapi for generated, interactive API documentation
- Testcontainers for integration tests against a disposable database

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

`.env` is excluded from version control via `.gitignore` and must never be committed. Only `.env.example` is tracked, and it holds obvious placeholders rather than working values.

The application refuses to start if `JWT_SECRET`, `ENCRYPTION_SECRET_KEY` or `BOOTSTRAP_ADMIN_PASSWORD` is empty or still equal to one of those published placeholders, or if `JWT_SECRET` is shorter than the 32 bytes HMAC-SHA256 requires (`SecretsValidator`). Copying `.env.example` and forgetting to replace a value therefore fails loudly at startup instead of silently signing tokens with a key that is public in this repository.

To rotate a secret, generate a new value with the commands above, replace it in `.env`, and restart the affected service (`docker compose up -d --force-recreate app`). Rotating `JWT_SECRET` invalidates all issued tokens, so every signed-in user has to log in again. Rotating `ENCRYPTION_SECRET_KEY` is different in kind: stored segment results and segment images are encrypted with it, so previously stored rows become unreadable — only rotate it together with a purge of that data, or plan a re-encryption step.

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

Integration tests do not use the development database. Each run starts a throwaway PostgreSQL container via Testcontainers, applies the Flyway migrations to it, and discards it afterwards, so the suite never reads or writes the data you see in the running application. Docker Desktop must be running; no `docker compose up` is required.

The container is created once in `AbstractIntegrationTest` and shared by every test class, so the suite pays the startup cost only once. Test classes connect to it by extending that base class, which overrides the datasource properties at runtime.

## API Documentation

With the application running, an interactive OpenAPI 3.1 view of every endpoint is served at **http://localhost:8080/swagger-ui.html**, generated from the controllers themselves rather than maintained by hand.

Most endpoints require a JWT. Call `POST /api/auth/login` from the page, copy the `token` from the response, then press **Authorize** and paste it; subsequent calls from the page will carry the bearer token. The token lifetime is 10 minutes, after which you need to authorize again.

Both the UI and the raw schema are disabled under the `prod` profile (`springdoc.swagger-ui.enabled=false`, `springdoc.api-docs.enabled=false`), so a production deployment does not publish its API surface.

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
| `GET /swagger-ui.html` | Public | Interactive API documentation (disabled under the `prod` profile) |
| `GET /v3/api-docs` | Public | OpenAPI 3.1 schema in JSON (disabled under the `prod` profile) |

## Frontend Integration

CORS is configured via `app.cors.allowed-origins` in `application.properties`, defaulting to `http://localhost:5173` unless the `CORS_ALLOWED_ORIGINS` environment variable is set. This default matches both the Vite dev server's default port and the port `docker-compose.yml` maps the production nginx `frontend` service to, so no change is needed for local development — set `CORS_ALLOWED_ORIGINS` to the real frontend origin for any other deployment (see [Production Configuration](#production-configuration)).

The frontend's nginx config (`frontend/nginx.conf`) serves static assets using the container's default `mime.types` table, plus one explicit addition for the `.mjs` extension (used by the pdf.js worker script — see `SDD.md` §9.4/§9.5). Avoid replacing this with a server-wide `types {}` block instead of the current per-extension `location` block — in nginx, a `types {}` block declared at server/location level fully replaces the inherited mime-type table rather than extending it, which would silently break the `Content-Type` of every other static asset (`.css`, `.svg`, `.woff`, etc.) served by that block.

`index.html` is served with `Cache-Control: no-cache, no-store, must-revalidate`. Routes are code-split (`React.lazy`) into content-hashed chunk files that change on every build; if a browser cached `index.html` across deploys, it would keep pointing at chunk filenames the server no longer has, surfacing as "Failed to fetch dynamically imported module" errors on navigation. `/assets/` (the hashed chunks themselves) are still cached for a year, since a changed file always gets a new filename.

## Production Configuration

An `application-prod.properties` profile is available for production deployments, activated by setting the `SPRING_PROFILES_ACTIVE=prod` environment variable on the `app` service. It disables SQL statement logging (`spring.jpa.show-sql=false`) and reduces log verbosity (`logging.level.root=WARN`). It is not enabled by default, since `docker-compose.yml` is used for local development as well — the compose file passes the variable through from the environment, so adding `SPRING_PROFILES_ACTIVE=prod` to `.env` is enough to switch, with no edit to the compose file itself.

Also set `CORS_ALLOWED_ORIGINS` to the real frontend origin in production — it defaults to `http://localhost:5173`, which is only correct for local development.

The compose file already applies a few deployment defaults that are safe for both local and real use: the PostgreSQL port is published to `127.0.0.1` only, so the database is reachable from this machine but not from anything else on the network; every service is set to `restart: unless-stopped`; and the `app` service exposes a health check against `/actuator/health`, so `docker compose ps` reports whether the application is actually serving rather than merely started.

For a real deployment, review the following in addition: drop the `postgres` port mapping entirely (the `app` service reaches the database over the compose network, not the host), put the frontend and backend behind TLS, and move secrets out of `.env` into whatever secret store the target environment provides.

## Known Limitations

- Login and upload rate limiters are held in-memory per instance and are not shared across replicas; a distributed store (e.g. Redis) is required before horizontal scaling. See `SRS.md` §2.1.
- Secrets live in a local `.env` file rather than a managed secret store. Startup validation prevents the published placeholders from being used, but it cannot tell a weak hand-written value from a strong generated one.
- Earlier commits in this repository's history contain `.env` values that were later rotated. They no longer unlock anything, and the history has deliberately not been rewritten, since doing so would change every commit hash for no security gain.
- Application logs are unstructured and carry no request correlation id, which makes tracing a single request across the async processing pipeline harder than it needs to be.