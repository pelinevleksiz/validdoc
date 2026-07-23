# validdoc

A document validation platform that combines template-defined, coordinate-based segments with OCR and a fixed rule catalog to deterministically derive document status.

An admin defines segments on a template (page and coordinates) and assigns each segment one or more rules from a fixed catalog (structural: letters/digits/length/date/signature-stamp; validated format: national ID, tax number, phone, email). An operator uploads a document against a template; the system evaluates each segment as filled-valid, filled-invalid, or empty, and derives the overall document status deterministically from the aggregate result.

For detailed requirements and architecture, see [`SRS.md`](SRS.md) and [`SDD.md`](SDD.md).

## Tech Stack

- Java 21, Spring Boot 4.1.0
- PostgreSQL 16
- Tesseract OCR (Tess4J), Turkish language pack
- OpenCV, for ink-density (signature/stamp) detection
- Spring Security with stateless JWT authentication

## Prerequisites

- Docker Desktop, running
- No local Java or Maven installation required; the project ships with a Maven wrapper (`mvnw.cmd`)

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

Allow a few seconds for the application to finish starting before issuing requests.

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

## Testing

```powershell
.\mvnw.cmd clean test
```

The test suite connects to the local Postgres instance; ensure it is running (`docker compose up -d`) before executing tests.

## API Reference

| Endpoint | Role | Description |
|---|---|---|
| `POST /api/auth/login` | Public | Issues a JWT |
| `PUT /api/users/me/password` | Authenticated | Changes the caller's own password |
| `GET /api/users` | Admin | Lists users (paginated) |
| `POST /api/users` | Admin | Creates a user account |
| `DELETE /api/users/{id}` | Admin | Deletes a user (blocked for the last admin or a user with linked documents) |
| `GET /api/templates` | Operator/Admin | Lists templates (paginated) |
| `POST /api/templates` | Admin | Defines a template with segments and rules |
| `GET /api/templates/{id}` | Operator/Admin | Returns a template's full segment and rule detail |
| `GET /api/templates/rule-types` | Admin | Returns the fixed rule catalog |
| `POST /api/templates/preview` | Admin | Previews segment extraction without persisting a template |
| `POST /api/documents/upload` | Operator/Admin | Uploads a document for asynchronous processing |
| `GET /api/documents` | Operator/Admin | Lists all documents, newest first (paginated) |
| `GET /api/documents/{id}` | Operator/Admin | Returns document status and segment results |
| `GET /api/documents/{id}/segments/{segmentId}/image` | Operator/Admin | Returns a `PENDING_REVIEW` segment's stored crop image |
| `POST /api/documents/{id}/segments/{segmentId}/resolve` | Operator | Applies a one-time manual decision to a `PENDING_REVIEW` segment |
| `GET /api/documents/queue` | Operator/Admin | Lists documents in `PENDING_REVIEW` |
| `POST /api/documents/{id}/verify` | Operator | Manually approves or rejects a document |
| `GET /api/admin/audit-logs` | Admin | Returns the audit log, newest first |
| `GET/PUT /api/admin/validation-settings` | Admin | Reads or updates retention period, ink-density threshold, and OCR confidence threshold |
| `GET /actuator/health` | Public | Health check |

## Frontend Integration

CORS is configured via `app.cors.allowed-origins` in `application.properties`, currently set to `http://localhost:5173` (the Vite default). Update this value and rebuild if the frontend runs on a different origin.

## Known Limitations

- Login and upload rate limiters are held in-memory per instance and are not shared across replicas; a distributed store (e.g. Redis) is required before horizontal scaling. See `SRS.md` §2.1.
- Secrets in `.env` are intended for local development only and must be rotated before any production deployment.