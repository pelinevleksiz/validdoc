# Software Requirements Specification (SRS)

**Project Name:** validdoc

---

## 1. Functional Requirements

### 1.1 User Authentication & Role-Based Access

- The system must provide a secure login interface with two distinct user roles: `admin` and `operator`, using a modern token-based authentication framework (JWT). Successfully authenticated users receive a short-lived bearer token; there is no server-side session state.
- **Token lifetime:** access tokens expire after 10 minutes. This is deliberately short, given the internal, single-session nature of the tool. A user whose token has expired must log in again; there is no refresh-token mechanism in this scope.
- **Brute-force protection:** repeated failed login attempts from the same source within a short window must be throttled rather than allowed indefinitely.
- Each user account also stores a contact identifier (email). Automated rejection notifications have been removed from this system's scope (see 1.4). The email field is retained only because the account-provisioning workflow itself is out of scope for this revision, not because it currently serves a functional purpose.
- **Account provisioning:** New user accounts (`admin` or `operator`) are created by an existing `admin` through a dedicated management endpoint. There is no public self-service registration, since this is an internal corporate tool, not a consumer-facing one. To solve the bootstrap problem — an admin-only endpoint needs an admin to already exist — the system automatically seeds exactly one default `admin` account on first startup if no user accounts exist yet, using credentials supplied via environment configuration rather than hardcoded in source.
- Passwords must be stored using BCrypt hashing. The `username` field is a login identifier, not extracted document content, and is not subject to the personal-data masking rules in 3.1.

### 1.2 Document Upload & Management

- The system must allow users to manually upload documents (PDF, PNG, JPEG) via a web-based interface.
- Every upload must reference an admin-registered template (see 1.3). Template-free validation is not part of this system's scope.
- **PDF handling:** since PDF is not a raster image format, uploaded PDFs must first be rendered to in-memory raster images before any OCR or computer-vision step runs (see Multi-page support below for which pages). This conversion happens in the same in-memory pipeline as PNG/JPEG, with no intermediate file written to disk. PDF rasterization is handled by an open-source Java PDF library (Apache PDFBox), so the rest of the pipeline can treat every page uniformly as a `BufferedImage`.
- Files must be processed on-the-fly and must not be permanently saved to disk.
- **Multi-page support:** a PDF is not limited to a single page. A template's segments (see 1.3) may each be assigned to any page of a multi-page document, so an admin can register a template for a form whose signature is on page 1 and whose ID number is on page 3, for example. Only the specific pages actually referenced by the selected template's segments are rendered for a given upload — not the entire document — since which pages matter is already known from the template at upload time.
- **Page/template mismatch:** if an uploaded document does not have enough pages to satisfy a segment's assigned page number (including a single-page PNG/JPEG upload validated against a template that references any page other than 1), this is treated as a structural mismatch between the document and the selected template, not as an empty field. Such a document must be routed to manual review rather than auto-rejected as blank or invalid, since the system cannot correctly evaluate a mismatched document.

### 1.3 Template-Based Segmentation & Rule-Based Validation

- The system performs exactly one mode of document analysis: **template-based segmentation**. Every upload must reference an admin-registered template. There is no template-free, general-purpose form-understanding mode.
- **Template registry:** an `admin` registers a named template representing one fixed document layout, which may span one or more A4 pages. A template is composed of one or more **segments**. Each segment is a rectangular coordinate region (x, y, width, height) on a specific page number of that layout, given a human-readable label chosen by the admin (e.g. "Ad Soyad", "İmza", "T.C. Kimlik No"). A template may contain any number of segments across any number of pages, and a segment's position and size are unconstrained beyond staying within its page's bounds.
- **Rule catalog:** each segment is assigned one or more validation rules, selected exclusively from a fixed, system-defined catalog — the admin never types a free-text rule. The catalog includes, at minimum: letters-only, digits-only, alphanumeric, date, Turkish ID number (T.C. Kimlik No, 11-digit), Turkish tax number (VKN, checksum-validated), Turkish phone number, e-mail, minimum length, maximum length, signature ink, and stamp/seal ink. Length-bound rules (minimum/maximum length) take a single numeric parameter; this parameter is the only free input the admin ever supplies, and it is constrained to a positive integer.
- **Rule/segment compatibility:** a segment must not combine an ink-based rule (signature or stamp) with a text-format rule. Ink detection and text extraction are mutually exclusive interpretations of the same region, so this combination is rejected at template-registration time rather than silently ignored.
- **Templates are immutable once created.** There is no edit or delete endpoint for a registered template. Correcting a mistake means registering a new template under a new name. This is a deliberate design choice: it prevents the interpretation of documents already validated against a template from silently changing after the fact.
- **Per-segment evaluation:** for every segment, the system must determine exactly one of three outcomes.
  1. **Filled — valid:** the region contains extracted content, and that content satisfies every rule assigned to the segment (or, for an ink-based segment, ink density meets the configured threshold).
  2. **Filled — invalid:** the region contains extracted content, but at least one assigned rule fails. The specific failing rule(s) must be recorded alongside the outcome.
  3. **Empty:** the region contains no meaningful extracted content (or, for an ink-based segment, ink density is below the configured threshold).
- **Segment-level report:** the result returned to the operator/admin must list every segment of the template by its label, alongside its outcome and, when applicable, which specific rule(s) failed. This per-segment report, not a single aggregate score, is the primary output of document analysis.
- **Document-level status:** the document's overall status is derived deterministically from its segment outcomes; see 1.4. There is no probabilistic scoring, confidence threshold, or configurable weighting in this system.
- **Signature and stamp detection:** for segments assigned an ink-based rule, the system must verify the presence of handwriting or a seal impression via pixel density analysis, not merely whether the region is non-blank.
- **Admin preview tool:** before publishing a template, an admin must be able to submit a sample document together with a draft set of segment coordinates and receive back the raw text/ink reading for each segment, without persisting anything. This lets an admin confirm that hand-drawn segment boxes actually capture the intended region before the template is registered and put into active use.

### 1.4 Workflow & Approval Management

- The system must transition each document through a fixed set of statuses based on its segment-level analysis outcome, and route failed/incomplete cases to manual operator review.
- **Status derivation:**
  - `PROCESSING` — while analysis is in progress.
  - `VALIDATED` — every segment's outcome is Filled — valid.
  - `REJECTED_EMPTY` — every segment's outcome is Empty; the document is treated as physically blank.
  - `REJECTED_INVALID` — any other combination: at least one segment is Filled — invalid, or some but not all segments are Empty.
  - `PENDING_REVIEW` — reserved for internal engine failures (e.g. an unreadable image, a malformed template definition) that prevent analysis from completing at all. It is not used for scoring ambiguity, since this system's rule evaluation is deterministic rather than probabilistic.
- **Manual override:** an `operator` must be able to open a review queue of `PENDING_REVIEW` documents, or look up any document individually, and manually set its final status (`VALIDATED`, `REJECTED_EMPTY`, or `REJECTED_INVALID`), overriding the automatic outcome.
- Automated e-mail notification on rejection has been removed from this system's scope. The segment-level report described in 1.3 is the mechanism by which an operator learns why a document was rejected; the system does not send an outbound notification to the uploader.

### 1.5 Multi-Language Support (Turkish / English)

- **API responses:** every error/feedback message the API returns must be available in both Turkish and English, selected per request via the standard `Accept-Language` HTTP header, defaulting to Turkish when the header is absent. This must not require a code change to add a wording change; messages live in translation resource files, not hardcoded in application logic.
- **OCR scanning language:** the user must be able to indicate which language a given uploaded document is written in, independent of their own UI language preference — an operator working in a Turkish interface may still upload an English-language document. This selection lets the OCR engine focus on a single language rather than scanning for both simultaneously, reducing cross-language recognition ambiguity and improving accuracy. Unspecified or unrecognized input must fall back to Turkish rather than failing the upload.
- These are resolved as two independent signals rather than one: the `Accept-Language` header drives which language API error/feedback text is written in (Spring's built-in locale resolution plus a translation resource bundle); a separate, explicit parameter on the upload call drives which single Tesseract language model is used for that specific document's OCR, stored alongside the document's own metadata so the choice is available to the background OCR step, which runs after the original request has already returned. See SDD §5.4 for the full design, including a Tesseract thread-safety fix this feature required.

---

## 2. Technical & Architectural Requirements

### 2.1 Backend Architecture

- The core system must be built using Java with an enterprise-ready framework, and must be deployable as a stateless container.
- The system uses Spring Boot 4.x as a monolithic architecture. This keeps development fast and simple within the project's time constraints while remaining modular for future changes. The application ships with a Dockerfile and holds no local session/file state, so it can be horizontally scaled by running additional container replicas behind a load balancer.

### 2.2 OCR Engine Integration

- An optical character recognition tool must be integrated to read text within admin-defined segment coordinates and perform rule-based validation on the extracted content.
- The system integrates Tesseract OCR locally via Java wrappers (Tess4J). This keeps the application self-contained, enables cropping specific segment coordinates for evaluation, and avoids dealing with cloud provider APIs or external dependencies.

### 2.3 Database & Persistence

- The system must persist user profiles, template definitions (segments and their assigned rules), document processing metadata (status, per-segment results), masked/encrypted copies of any personal data extracted during validation, and validation history. No physical document files or raw (unmasked) extracted personal data shall be stored permanently on the server's file system or database.
- **Retention & purge:** once a document finishes processing, its metadata row must be scheduled for purge (masked fields nulled/anonymized) after a configurable retention period measured from that point, not from upload time, since a value cannot be scheduled before processing produces a result. The immutable `audit_logs` table (see 3.1.2) is exempt from this purge, since it stores only action metadata (who/what/when), not personal document content.
- Each document record must also track which user account uploaded it, for audit purposes.
- The system uses PostgreSQL combined with Spring Data JPA. Uploaded files are handled via Spring's `MultipartFile` and processed strictly in-memory to avoid server storage issues and to satisfy the data-privacy requirements in 3.1. A strict file-size limit (e.g. maximum 5 megabytes per file) is enforced in the application configuration to prevent memory overload from simultaneous mass uploads. Once image processing and rule evaluation finish, the file is discarded from memory, and only the execution record (status, per-segment results, timestamps, operator id) is persisted. A scheduled job periodically purges masked personal data past its retention window.

---

## 3. Non-Functional Requirements

### 3.1 Security & Compliance

- The system must ensure strict data privacy and security compliance.
  1. **KVKK / GDPR compliance:** personal data (names, phone numbers, ID numbers, etc.) extracted from document contents must be masked and stored encrypted at rest, at the column level. The system must support a data-retention policy to purge this stored data after a configurable window. Login credentials (`username`/`password`) are handled separately under standard authentication security practices (see 1.1) and are not part of this masking requirement. The original uploaded filename is upload metadata, not content extracted from the document, and is therefore outside the scope of this masking rule (see SDD §4.2).
  2. **Immutable audit logs:** every user action (including who uploaded, processed, or manually reviewed a document) must be recorded as an unchangeable log for corporate auditing.
- The system encrypts the columns that hold extracted personal data at rest (application-side AES-256-GCM, key externalized via configuration, not a database-native encryption function, so the key never ends up checked into any SQL or source control), and runs a scheduled purge job for those columns per the retention window. A dedicated `audit_logs` table exists where rows are strictly append-only, so no one can tamper with the processing history.

### 3.2 Performance & Scalability

- The platform must deliver quick processing turnarounds and support seamless system scaling.
  1. **Horizontal scalability:** the application must be packaged as a stateless container image so additional replicas can be started under heavy corporate workloads.
  2. **Response time:** the end-to-end processing and rule evaluation of a standard single-page document must take less than 3 seconds as perceived by the client — the upload call itself returns quickly, and processing continues asynchronously.
  3. **Operability:** the application must expose an unauthenticated health-check endpoint so a load balancer, container orchestrator, or uptime monitor can verify the instance is running without needing credentials.
- The monolithic Spring Boot structure is kept fully stateless and Docker-ready so it can scale horizontally if needed. To meet the under-3-second target for the client-facing call, the system uses Spring's `@Async` thread pool to trigger OCR/rule evaluation in the background, keeping the web UI responsive and returning an immediate `202 Accepted`. For 3.2.3, the system uses Spring Boot Actuator's built-in health endpoint, exposing only the aggregate up/down status (no internal details) publicly.