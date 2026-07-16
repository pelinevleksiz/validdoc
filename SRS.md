# Software Requirements Specification (SRS)

**Project Name:** validdoc

---

## 1. Functional Requirements

### 1.1 User Authentication & Role-Based Access

- The system must provide a secure login interface with two distinct user roles: `admin` and `operator`, utilizing a modern token-based framework.
- Each user account must also store a contact identifier (email), used solely as the delivery target for the rejection notifications described in 1.4.
- **Account provisioning:** New user accounts (`admin` or `operator`) are created by an existing `admin` through a dedicated management endpoint — there is no public self-service registration, since this is an internal corporate tool, not a consumer-facing one. To solve the bootstrap problem (an admin-only endpoint needs an admin to already exist), the system automatically seeds exactly one default `admin` account on first startup if no user accounts exist yet, using credentials supplied via environment configuration rather than hardcoded in source.
- *I will implement a standard Spring Security configuration with JWT (JSON Web Token) for stateless authentication. Instead of using heavy server-side sessions, successfully authenticated users will receive a secure token. Subsequent frontend requests (like document uploads) will pass this token in the auth header. Passwords will be stored using BCrypt hashing. The `username` field itself is a login identifier, not extracted document content — it is stored in plaintext with a unique index and is not subject to the personal-data masking rules in 3.1. Account creation itself is a simple admin-gated CRUD endpoint (username/password/email/role in, BCrypt-hashed on save) plus a one-time startup seeder for the first admin — this is intentionally minimal for the 20-day MVP scope, not a full identity-management subsystem.*
### 1.2 Document Upload & Management

- The system must allow users to manually upload documents (PDF, PNG, JPEG) via a web-based drag-and-drop interface.
- **PDF handling:** Since PDF is not a raster image format, uploaded PDFs must first be rendered to an in-memory raster image (one page per document, first page only for MVP) before any OCR or computer-vision step runs. This conversion happens in the same in-memory pipeline as PNG/JPEG — no intermediate file is written to disk.
- *I am focusing on manual uploads to fit the 20-day timeline. Files will be processed on-the-fly without being permanently saved. PDF rasterization will be handled by an open-source Java PDF library (e.g. Apache PDFBox) so the rest of the pipeline (OpenCV + Tesseract) can treat every input uniformly as a `BufferedImage`.*
- **Known MVP limitation:** only the first page of a multi-page PDF is scanned. Full multi-page support (e.g. locating a signature on a later page) is deferred to a future phase.
### 1.3 Intelligent Document Scanning, Templating & Completeness Analysis

- The system must support two analysis modes to check if required fields (signatures, names, numbers, etc.) are filled with logically valid data:
  1. **Templated validation:** For fixed forms (e.g. standard application forms), specific coordinate bounding boxes (name, signature box, etc.) will be verified.
  2. **Template-free validation:** For documents with varying structures (e.g. invoices or contracts), the system will dynamically detect fields like "signature", "name", or "date" using computer vision and basic text positioning.
- The mode used for a given document (`TEMPLATED` or `TEMPLATE_FREE`) must be recorded alongside the document's result, so operators and auditors can see which engine path produced a given verdict.
- **Template registry:** Templated validation requires a stored definition of each fixed form's expected field boxes. An admin must be able to register a named template (a set of labeled field coordinates) that an upload can be validated against; if no template is selected, the document is processed in template-free mode.
- **Scoring overview (draft level):** The confidence score is a weighted combination of (a) required-field completeness, (b) format/logical correctness of extracted text, and (c) signature/stamp presence. A document with little to no extracted content and no ink presence is treated as `REJECTED_EMPTY`; a document with content but failing format/logical rules is treated as `REJECTED_INVALID`; scores that land close to the configurable threshold (within a small margin, in either direction) are treated as `PENDING_REVIEW` rather than auto-decided. Exact weighting and margin values are tuning parameters, not fixed requirements.
- **Signature and stamp detection:** The system must verify not just if the signature/stamp box is empty, but if it contains handwriting or a seal impression via pixel density and object detection.
- **Logical & format verification:** The system must perform logical validation on extracted text. If a field is filled with invalid or nonsense data (e.g. checking if an ID field contains exactly 11 digits, if a phone number matches standard formats, or if a text field contains keyboard-mashed gibberish), the field will be marked as invalid.
- *I will build this engine using open-source tools for fixed forms. I will use OpenCV to crop and analyze specific coordinates for pixel density (to detect ink presence in signatures). For non-fixed forms, Tesseract OCR will help find text anchors to locate and validate empty, filled, or logically invalid zones without building complex deep learning pipelines from scratch.*
### 1.4 Workflow & Approval Management

- The system must route uncertain documents to manual verification, trigger a notification on rejection, and transition document states dynamically based on the analysis outcome.
- **Human-in-the-loop validation:** The system must generate a confidence score based on field completeness, logical correctness, and signature visibility. Documents falling below a **configurable threshold** (default 80%, adjustable via application configuration without a code change) must be routed to an "operator approval queue" instead of being instantly rejected.
- **Advanced state mapping:** The system will assign one of five distinct statuses to the document: `PROCESSING` during analysis, `VALIDATED` if all rules pass, `REJECTED_EMPTY` if physically blank, `REJECTED_INVALID` if data is present but logically/format-wise incorrect, and `PENDING_REVIEW` if manual verification is required or if an internal engine error occurred.
- **Rejection notification:** On transition to `REJECTED_EMPTY` or `REJECTED_INVALID`, the system must invoke a notification hook. For MVP scope this hook is an internal, asynchronous, pluggable interface (logged/stubbed by default) rather than a full email/SMS delivery system — this keeps the requirement satisfied without expanding the 20-day timeline.
- *I will create a simple verification queue using the five document statuses above. If the system is unsure about a signature or a text field, the operator will be able to review the document on a dashboard and manually change its state with a single click.*
---

## 2. Technical & Architectural Requirements

### 2.1 Backend Architecture

- The core system must be built using Java with an enterprise-ready framework, and must be deployable as a stateless container.
- *I will use Spring Boot 4.x to build a robust monolithic architecture. This keeps development fast and simple within our 20-day limit while remaining modular for future updates. The application will ship with a Dockerfile and hold no local session/file state, so it can be horizontally scaled by running additional container replicas behind a load balancer.*
### 2.2 OCR Engine Integration

- An optical character recognition tool must be integrated to read text, extract coordinate data, and perform logical format validation.
- *I will integrate Tesseract OCR locally via Java wrappers (Tess4J). This keeps the application self-contained, enables cropping specific coordinates for templated validation, and avoids dealing with cloud provider APIs or external dependencies.*
### 2.3 Database & Persistence

- The system must persist user profiles, document processing metadata (status, validation mode, validation scores, format error logs), masked/encrypted copies of any personal data extracted during validation, and validation history. No physical document files or raw (unmasked) extracted personal data shall be stored permanently on the server's file system or database.
- **Retention & purge:** Once a document finishes processing, its metadata row must be scheduled for purge (masked fields nulled/anonymized) after a configurable retention period measured from that point — not from upload time, since a value can't be scheduled before processing produces a result. The immutable `audit_logs` table (see 3.1.2) is exempt from this purge, since it stores only action metadata (who/what/when), not personal document content.
- Each document record must also track which user account uploaded it, both for audit purposes and as the notification target described in 1.4.
- *I will use PostgreSQL combined with Spring Data JPA. To avoid server storage issues and comply with data privacy, uploaded files will be handled via Spring's `MultipartFile` and processed strictly in-memory (RAM). To prevent memory overload from simultaneous mass uploads, I will enforce a strict file size limit (e.g. maximum 5 megabytes per file) in the application properties. Once image processing and OCR validation finish, the file is instantly destroyed from RAM, and only the execution logs (status, validation mode, validation score, masked field data, error details, timestamp, operator ID) are saved. A scheduled job will periodically purge masked personal data past its retention window.*
---

## 3. Non-Functional Requirements

### 3.1 Security & Compliance

- The system must ensure strict data privacy and security compliance.
  1. **KVKK / GDPR compliance:** Personal data (names, phone numbers, etc.) *extracted from document contents* must be masked and stored encrypted at rest, at the column level. The system must support data retention policies to purge this stored data after a configurable window. Login credentials (`username`/`password`) are handled separately under standard authentication security practices (see 1.1) and are not part of this masking requirement. The original uploaded filename is upload metadata, not content extracted from the document, and is therefore outside the scope of this masking rule (see SDD §4.2).
  2. **Immutable audit logs:** Every user action (including who uploaded, processed, or manually approved a document) must be recorded as an unchangeable log for corporate auditing.
- *I will encrypt the columns that hold extracted personal data at rest (application-side AES-256-GCM, key externalized via configuration — not a database-native encryption function, so the key never ends up checked into any SQL or source control), and run a scheduled purge job for those columns per the retention window. For compliance, I will create a dedicated `audit_logs` table where rows are strictly append-only, ensuring no one can tamper with the processing history.*
### 3.2 Performance & Scalability

- The platform must deliver quick processing turnarounds and support seamless system scaling.
  1. **Horizontal scalability:** The application must be packaged as a stateless container image so additional OCR-scanning replicas can be started under heavy corporate workloads.
  2. **Response time:** The end-to-end processing, layout analysis, and logical validation of a standard single-page document must take less than 3 seconds as perceived by the client (i.e., the upload call itself returns quickly; processing continues asynchronously).
- *I will keep the monolithic Spring Boot structure fully stateless and Docker-ready so it can scale horizontally if needed. To hit the under-3-second target for the client-facing call, I will use Spring's `@Async` thread pool to trigger the OCR/CV process in the background, keeping the web UI responsive and returning an immediate `202 Accepted`.*