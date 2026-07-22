# Software Design Document (SDD) - validdoc

## 1. System Architecture
The application follows a layered monolithic architecture (**Controller → Service → Repository**), built on Spring Boot 4.x, and is packaged as a stateless container.

```mermaid
graph TD
    A[Presentation - Controller] --> B[Business Logic - Service]
    B --> C[Data Access - Repository/JPA]
    C --> D[(PostgreSQL)]
    B --> E[OCR - Tess4J]
    B --> F[Ink Detection - OpenCV]
    B --> G[PDF Rasterize - PDFBox]
```

Sensitive fields (`segment_results`, `segment_images.image_data`) are encrypted at the JPA `AttributeConverter` level with **AES-256-GCM**; the key is supplied only via an environment variable.

### 1.1 Container Readiness
The Dockerfile is based on `eclipse-temurin:21-jre-jammy`; Tesseract 4.1.1 is installed via apt, and the `tessdata` path is fixed accordingly. Secrets (DB, JWT, encryption key) are supplied only via environment variables.

---

## 2. Data Flow Diagram
Every request first passes through JWT authentication. The upload endpoint persists an initial record and returns `202 Accepted` immediately; the rest of the pipeline (page rasterization, OCR, rule evaluation, status derivation) runs asynchronously in the background. Only the pages a template's segments actually reference are rasterized — not the whole document — and every outcome, automatic or manual, is written to the audit log alongside the final status.

```mermaid
graph TD
    Client["Client"] -->|"POST /api/documents/upload + JWT + templateId"| Filter["JwtAuthenticationFilter"]
    Filter -->|"invalid or expired token"| Auth401["401 Unauthorized"]
    Filter -->|"valid token"| Controller["DocumentController"]

    Controller -->|"1. create record, status=PROCESSING"| DB[("PostgreSQL")]
    Controller -->|"2. return 202 Accepted"| Client
    Controller -->|"3. delegate asynchronously"| Service["DocumentService"]

    Service --> Resolve["Resolve Template + collect required pages from segments"]
    Resolve -->|"PDF"| Raster["PdfRasterService: rasterize only the required pages"]
    Resolve -->|"PNG / JPEG"| Single["Treat as single page 1"]
    Raster --> OCR["OcrService: crop + read each segment"]
    Single --> OCR
    OCR --> Validate["ValidationService: evaluate each segment against its rules"]

    Validate --> Eval{"Aggregate status"}
    Eval -->|"all segments valid"| Validated["VALIDATED"]
    Eval -->|"all segments empty"| Empty["REJECTED_EMPTY"]
    Eval -->|"mixed outcome"| Invalid["REJECTED_INVALID"]
    Eval -->|"engine failure"| Review["PENDING_REVIEW"]
    Eval -->|"any segment below confidence threshold"| Review

    Validated --> Save["Save status + segment_results, write audit log"]
    Empty --> Save
    Invalid --> Save
    Review --> Save
    Save --> DB
```

---

## 3. Class Design & Package Structure

```mermaid
classDiagram
    class DocumentMetadata {
        +DocumentStatus status
        +Template template
        +String segmentResults
    }
    class Template {
        +String name
        +List~TemplateSegment~ segments
    }
    class TemplateSegment {
        +String label
        +int page
        +double x
        +double y
        +double w
        +double h
        +List~SegmentRule~ rules
    }
    class SegmentRule {
        +SegmentRuleType ruleType
        +Integer param
    }
    Template "1" --> "many" TemplateSegment
    TemplateSegment "1" --> "many" SegmentRule
    DocumentMetadata --> Template
```

**Main packages:** `controller` (REST endpoints), `service` (OCR/validation/document orchestration), `model` (JPA entities), `dto` (request/response/internal carriers), `security` (JWT and encryption), `exception` (centralized error handling), `repository` (Spring Data JPA), `config` (Tesseract/async/settings infrastructure).

---

## 4. Database Schema (ERD)

```mermaid
erDiagram
    USER ||--o{ DOCUMENT_METADATA : uploads
    TEMPLATE ||--o{ TEMPLATE_SEGMENT : has
    TEMPLATE_SEGMENT ||--o{ SEGMENT_RULE : has
    TEMPLATE ||--o{ DOCUMENT_METADATA : validates
    DOCUMENT_METADATA ||--o{ AUDIT_LOG : logs
    DOCUMENT_METADATA ||--o{ SEGMENT_IMAGES : "has (while pending)"

    USER {
        bigint id PK
        string username
        string role
    }
    TEMPLATE {
        bigint id PK
        string name
    }
    TEMPLATE_SEGMENT {
        bigint id PK
        string label
        int page
        double x
        double y
        double w
        double h
    }
    SEGMENT_RULE {
        bigint id PK
        string rule_type
        int param
    }
    DOCUMENT_METADATA {
        bigint id PK
        string status
        string language
        text segment_results
        bigint template_id FK
    }
    SEGMENT_IMAGES {
        bigint id PK
        bigint document_id FK
        bigint segment_id
        text image_data
    }
    VALIDATION_SETTINGS {
        bigint id PK
        int retention_days
        double ink_density_threshold
        double ocr_confidence_threshold
    }
```

**Note:** `templates` cannot be modified once saved; a correction is made by registering a new template. `audit_logs` is append-only and is exempt from the retention purge process. `segment_images` rows are deleted as soon as their segment is resolved, independent of the retention job.

---

## 5. Core Algorithmic Decisions

- **5.1 In-Memory Processing:** Files are never written to disk; once processing completes, the `BufferedImage` is released for GC. A maximum file size of 5MB is enforced.
- **5.2 Async Processing:** OCR and validation run in the background via an `@Async` thread pool (4-8 threads); the upload request returns `202 Accepted` immediately.
- **5.3 Admin-Configurable Settings:** `retentionDays`, `inkDensityThreshold`, and `ocrConfidenceThreshold` can all be changed at runtime (no restart required); they are stored in the `validation_settings` table.
- **5.4 Segment Evaluation:** Each segment is evaluated against its own rules as `FILLED_VALID` / `FILLED_INVALID` / `EMPTY` / `PENDING_REVIEW` (the last triggered by low OCR confidence); the result is masked and written as JSON into `segment_results`. Document status is derived deterministically from these results (see §2), and is recomputed once every `PENDING_REVIEW` segment has been manually resolved.
- **5.5 Multi-Language (TR/EN):** The `Accept-Language` header determines the API message language, while a separate `lang` parameter determines the OCR scanning language — two independent signals. Since `Tesseract` is not thread-safe, each worker thread keeps its own instance (`ThreadLocal`).
- **5.6 Upload Hardening:** the accepted file type is determined from the file's actual signature bytes (`FileSignatureValidator`) rather than the client-supplied `Content-Type` header, since the latter is trivially spoofable. Upload requests are also rate-limited per authenticated user (`UploadRateLimiter`, 20 requests per 60 seconds, in-memory) to protect the async processing pipeline from a single account's burst traffic.

---

## 6. API Endpoints

**Coordinate contract:** every segment coordinate (`x`, `y`, `w`, `h`) is expressed in **pixels at 300 DPI**, the same resolution `PdfRasterService` uses to rasterize PDF pages (see §5.1). An A4 page therefore spans **2480×3508 px** (`DocumentGeometry.A4_WIDTH_PX` / `A4_HEIGHT_PX`); any segment whose bounds fall outside this box is rejected at template-registration time with `INVALID_SEGMENT_COORDINATES`. A frontend capturing coordinates from an on-screen canvas must scale its own pixel space to this 300-DPI/A4 basis before submitting a template — not to the screen's native resolution.

**Pagination:** every list endpoint accepts `page` (0-indexed, default `0`) and `size` (default `20`) query parameters and returns a `PagedResponse` envelope (`content`, `page`, `size`, `totalElements`, `totalPages`) rather than a bare array.

| Method | Endpoint | Role | Description |
|---|---|---|---|
| GET | `/actuator/health` | Public | Provides an authentication-free liveness check |
| POST | `/api/auth/login` | Public | Issues a JWT (valid for 10 min) |
| POST | `/api/users` | ADMIN | Creates a new user |
| GET | `/api/templates` | OPERATOR/ADMIN | Lists templates (paginated) |
| POST | `/api/templates` | ADMIN | Saves a template with segments and rules (immutable) |
| GET | `/api/templates/{id}` | OPERATOR/ADMIN | Returns a template's full segment and rule detail |
| GET | `/api/templates/rule-types` | ADMIN | Returns the fixed rule catalog, flagging which rules take a param and which are ink-based |
| POST | `/api/templates/preview` | ADMIN | Provides a segment preview without saving |
| POST | `/api/documents/upload` | OPERATOR/ADMIN | Uploads a document, processes it asynchronously |
| GET | `/api/documents` | OPERATOR/ADMIN | Lists all documents, newest first (paginated) |
| GET | `/api/documents/{id}` | OPERATOR/ADMIN | Returns a document and its segment report |
| GET | `/api/documents/{id}/segments/{segmentId}/image` | OPERATOR/ADMIN | Returns a `PENDING_REVIEW` segment's stored crop image |
| POST | `/api/documents/{id}/segments/{segmentId}/resolve` | OPERATOR | Applies a one-time manual decision to a `PENDING_REVIEW` segment |
| GET | `/api/documents/queue` | OPERATOR/ADMIN | Returns the `PENDING_REVIEW` queue (paginated) |
| POST | `/api/documents/{id}/verify` | OPERATOR | Applies a manual status |
| GET/PUT | `/api/admin/validation-settings` | ADMIN | Manages retention, ink threshold, and OCR confidence threshold |
| GET | `/api/admin/audit-logs` | ADMIN | Lists audit log entries, optionally filtered by `documentId` (paginated) |

---

## 7. Security Architecture
- Every request passes through `JwtAuthenticationFilter`; an invalid or expired token results in `401`.
- Login attempts are rate-limited to **5 per minute per IP** (in-memory).
- Account creation is restricted to admins; a single admin account is seeded automatically on first startup.

---

## 8. Global Exception & Failure Handling
- Business logic errors are thrown as `ApiException` with an `ErrorCode`; `@RestControllerAdvice` returns them as a localized `{code, message}` payload (based on `Accept-Language`).
- Engine failures (OCR/PDF/OpenCV/template mismatch) never surface as HTTP errors — they are caught inside the `@Async` pipeline, the document is moved to `PENDING_REVIEW`, and the outcome is written to `audit_logs`.