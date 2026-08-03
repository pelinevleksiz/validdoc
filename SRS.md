# Software Requirements Specification (SRS)

**Project Name:** validdoc

---

## 1. Functional Requirements

### 1.1 User Authentication & Role-Based Access
- Authentication is **JWT-based and stateless**; two roles are defined: `admin`, `operator`. Token lifetime is **10 minutes**, with no refresh mechanism.
- Accounts are created only by an admin; public self-registration is not supported. The system automatically creates one default admin account on first startup.
- Passwords are stored using **BCrypt**; failed login attempts are rate-limited per source IP.

### 1.2 Document Upload & Management
- PDF (**multi-page supported**), PNG, and JPEG formats are accepted; files are processed in memory without being written to disk.
- Every upload is required to reference a **template**; template-free validation is not supported.
- An uploaded document's page count must **exactly match** the referenced template's page count; a mismatched file is rejected before processing.
- The accepted file type is determined from the file's actual content (signature bytes), not from the client-supplied `Content-Type` header alone.
- Upload requests are rate-limited per user to prevent a single account from overwhelming the processing pipeline.
- Multiple files can be uploaded in a single batch against the same template; each file is tracked and reported as an independent job.

### 1.3 Template-Based Segmentation & Rule-Based Validation
- An admin defines **page- and coordinate-based segments** on a template; each segment is assigned one or more rules from the system's fixed catalogs.
- The rule catalog has two groups: *structural* (letters/digits/length/date/signature-stamp) and *validated format* (Turkish ID number and VKN, both checksum-validated; phone; email).
- Each segment is evaluated as **filled-valid / filled-invalid / empty / pending-review**; the last is triggered when OCR confidence falls below a configurable threshold, regardless of whether the extracted text otherwise satisfied its rules.
- **Templates cannot be modified once saved**; a correction is made by creating a new template.
- An admin can **preview** segment coordinates against a sample document before saving.

### 1.4 Workflow & Approval Management
- The upload request returns immediately with `202 Accepted`; document processing runs asynchronously in the background.
- Document status is derived **deterministically** from segment results: all valid → `VALIDATED`, all empty → `REJECTED_EMPTY`, mixed → `REJECTED_INVALID`.
- `PENDING_REVIEW` is triggered either by an engine failure (corrupt file, page mismatch) or by any segment falling below the OCR confidence threshold. In the latter case, an operator **or admin** resolves each pending segment with a one-time, irreversible decision, after which the document's status is recomputed from the final segment outcomes.
- Every automatic and manual segment outcome is written to an **audit log**.

### 1.5 Multi-Language Support (Turkish / English)
- API error and feedback messages are served in TR/EN based on the `Accept-Language` header.
- The OCR scanning language is set independently of the UI language, via a separate parameter at upload time.

### 1.6 Web Client (Frontend)
- A single-page web client covers the full workflow for both roles: authentication, template creation (segment drawing + rule assignment on a canvas rendering of a sample document), document upload, the pending-review queue, document listing/detail, user management, validation settings, and audit logs.
- Template creation accepts a **sample document** (PNG, JPEG, or PDF) as the canvas source; for a multi-page PDF, segments are drawn independently per page and the page navigator lets the admin switch pages before drawing.
- The document upload screen accepts **PDF, PNG, or JPEG**, supports multi-file batches, shows each file's detected page count next to it, and blocks submission for any file whose page count doesn't match the selected template.
- A document that lands in `PENDING_REVIEW` can be resolved **inline**, within the same upload flow, without navigating to a separate review screen; the dedicated review queue remains available for documents uploaded earlier.
- The entire UI (not just API error messages) is available in Turkish and English via client-side i18n, including backend error codes, which are mapped to localized strings.
- The UI is responsive down to a mobile breakpoint (below 768px), collapsing the sidebar into a hamburger menu.

---

## 2. Technical & Architectural Requirements

### 2.1 Backend Architecture
The application is built with Spring Boot 4.x and Java 21; it is packaged as a **stateless** container and is horizontally scalable. Login and upload rate limiters are held in-memory per instance, so they are not shared across replicas in a multi-instance deployment.

### 2.2 OCR Engine Integration
Tesseract (Tess4J) is integrated locally for OCR; segment coordinates are cropped and read directly from the page image.

### 2.3 Deployment & Containerization
The full stack (PostgreSQL, backend, frontend) runs via a single `docker-compose.yml`. The frontend is built as a static production bundle and served by an nginx container, separate from the backend container; the two communicate over HTTP using a build-time-configured API base URL, not a shared Docker network hostname reachable from the browser. The database port is published only to the local machine, services restart automatically unless deliberately stopped, and the backend exposes a container health check so that readiness is observable rather than assumed.

The database schema is owned by versioned migrations rather than generated from the entity model at runtime, so it is reproducible across environments and reviewable in version control.

### 2.4 Database & Persistence
- PostgreSQL and Spring Data JPA are used at the data layer; uploaded files are processed in memory only and are never persisted.
- Once processing completes, only the result (status, segment report, timestamps) is persisted; it is automatically erased once the **retention period** elapses.
- For a segment awaiting manual review, its cropped image is additionally persisted (encrypted with AES-256-GCM) and is deleted immediately once the segment is resolved, independent of the retention period.

---

## 3. Non-Functional Requirements

### 3.1 Security & Compliance
- Personal data extracted from documents is **masked and stored encrypted with AES-256-GCM**; it is deleted after a configurable retention period.
- Every user action is recorded in an immutable **audit log**.
- Secrets are supplied only through environment variables and are never committed. The application refuses to start when a required secret is missing or still holds one of the placeholder values published in the tracked example file, so a forgotten substitution cannot reach a running system unnoticed.

### 3.2 Performance & Scalability
- The application is designed to be replicated as a stateless container; note that the in-memory rate limiters (§2.1) are the one exception to this and would need a shared store (e.g. Redis) before running multiple replicas behind a load balancer.
- End-to-end processing of a standard single-page document completes in **under 3 seconds**; the upload request itself returns immediately, with processing carried out asynchronously in the background.
- An authentication-free **health-check** endpoint is provided, and is also what the container runtime polls to decide whether an instance is serving.
- Integration tests run against a disposable database instance created per test run, so they neither depend on nor disturb any environment's data.