# Software Requirements Specification (SRS)

**Project Name:** validdoc  

---

1. Functional Requirements

1.1 User Authentication & Role-Based Access


The system must provide a secure login interface with two distinct user roles: admin and operator, utilizing a modern token-based framework.
I will implement a standard Spring Security configuration with JWT (JSON Web Token) for stateless authentication. Instead of using heavy server-side sessions, successfully authenticated users will receive a secure token. Subsequent frontend requests (like document uploads) will pass this token in the auth header. Passwords will be stored using BCrypt hashing. The username field itself is a login identifier, not extracted document content — it is stored in plaintext with a unique index and is not subject to the personal-data masking rules in 3.1.


1.2 Document Upload & Management


The system must allow users to manually upload documents (PDF, PNG, JPEG) via a web-based drag-and-drop interface.
PDF handling: Since PDF is not a raster image format, uploaded PDFs must first be rendered to an in-memory raster image (one page per document, first page only for MVP) before any OCR or computer-vision step runs. This conversion happens in the same in-memory pipeline as PNG/JPEG — no intermediate file is written to disk.
I am focusing on manual uploads to fit the 20-day timeline. Files will be processed on-the-fly without being permanently saved. PDF rasterization will be handled by an open-source Java PDF library (e.g. Apache PDFBox) so the rest of the pipeline (OpenCV + Tesseract) can treat every input uniformly as a BufferedImage.


1.3 Intelligent Document Scanning, Templating & Completeness Analysis


The system must support two analysis modes to check if required fields (signatures, names, numbers, etc.) are filled with logically valid data:

Templated validation: For fixed forms (e.g. standard application forms), specific coordinate bounding boxes (name, signature box, etc.) will be verified.
Template-free validation: For documents with varying structures (e.g. invoices or contracts), the system will dynamically detect fields like "signature", "name", or "date" using computer vision and basic text positioning.



The mode used for a given document (TEMPLATED or TEMPLATE_FREE) must be recorded alongside the document's result, so operators and auditors can see which engine path produced a given verdict.
Signature and stamp detection: The system must verify not just if the signature/stamp box is empty, but if it contains handwriting or a seal impression via pixel density and object detection.
Logical & format verification: The system must perform logical validation on extracted text. If a field is filled with invalid or nonsense data (e.g. checking if an ID field contains exactly 11 digits, if a phone number matches standard formats, or if a text field contains keyboard-mashed gibberish), the field will be marked as invalid.
I will build this engine using open-source tools for fixed forms. I will use OpenCV to crop and analyze specific coordinates for pixel density (to detect ink presence in signatures). For non-fixed forms, Tesseract OCR will help find text anchors to locate and validate empty, filled, or logically invalid zones without building complex deep learning pipelines from scratch.


1.4 Workflow & Approval Management


The system must route uncertain documents to manual verification, trigger a notification on rejection, and transition document states dynamically based on the analysis outcome.
Human-in-the-loop validation: The system must generate a confidence score based on field completeness, logical correctness, and signature visibility. Documents falling below a configurable threshold (default 80%, adjustable via application configuration without a code change) must be routed to an "operator approval queue" instead of being instantly rejected.
Advanced state mapping: The system will assign one of five distinct statuses to the document: PROCESSING during analysis, VALIDATED if all rules pass, REJECTED_EMPTY if physically blank, REJECTED_INVALID if data is present but logically/format-wise incorrect, and PENDING_REVIEW if manual verification is required or if an internal engine error occurred.
Rejection notification: On transition to REJECTED_EMPTY or REJECTED_INVALID, the system must invoke a notification hook. For MVP scope this hook is an internal, asynchronous, pluggable interface (logged/stubbed by default) rather than a full email/SMS delivery system — this keeps the requirement satisfied without expanding the 20-day timeline.
I will create a simple verification queue using the five document statuses above. If the system is unsure about a signature or a text field, the operator will be able to review the document on a dashboard and manually change its state with a single click.



2. Technical & Architectural Requirements

2.1 Backend Architecture


The core system must be built using Java with an enterprise-ready framework, and must be deployable as a stateless container.
I will use Spring Boot 4.x to build a robust monolithic architecture. This keeps development fast and simple within our 20-day limit while remaining modular for future updates. The application will ship with a Dockerfile and hold no local session/file state, so it can be horizontally scaled by running additional container replicas behind a load balancer.


2.2 OCR Engine Integration


An optical character recognition tool must be integrated to read text, extract coordinate data, and perform logical format validation.
I will integrate Tesseract OCR locally via Java wrappers (Tess4J). This keeps the application self-contained, enables cropping specific coordinates for templated validation, and avoids dealing with cloud provider APIs or external dependencies.


2.3 Database & Persistence


The system must persist user profiles, document processing metadata (status, validation mode, validation scores, format error logs), masked/encrypted copies of any personal data extracted during validation, and validation history. No physical document files or raw (unmasked) extracted personal data shall be stored permanently on the server's file system or database.
Retention & purge: Metadata rows containing masked personal data must be automatically purged (fields nulled/anonymized) after a configurable retention period, in line with KVKK/GDPR data-minimization requirements. The immutable audit_logs table (see 3.1.2) is exempt from this purge, since it stores only action metadata (who/what/when), not personal document content.
I will use PostgreSQL combined with Spring Data JPA. To avoid server storage issues and comply with data privacy, uploaded files will be handled via Spring's MultipartFile and processed strictly in-memory (RAM). To prevent memory overload from simultaneous mass uploads, I will enforce a strict file size limit (e.g. maximum 5 megabytes per file) in the application properties. Once image processing and OCR validation finish, the file is instantly destroyed from RAM, and only the execution logs (status, validation mode, validation score, masked field data, error details, timestamp, operator ID) are saved. A scheduled job will periodically purge masked personal data past its retention window.



3. Non-Functional Requirements

3.1 Security & Compliance


The system must ensure strict data privacy and security compliance.

KVKK / GDPR compliance: Personal data (names, phone numbers, etc.) extracted from document contents must be masked and stored encrypted at the database column level. The system must support data retention policies to purge this stored data after a configurable window. Login credentials (username/password) are handled separately under standard authentication security practices (see 1.1) and are not part of this masking requirement.
Immutable audit logs: Every user action (including who uploaded, processed, or manually approved a document) must be recorded as an unchangeable log for corporate auditing.



I will enforce database-level encryption for the columns that hold extracted personal data, and a scheduled purge job for those columns per the retention window. For compliance, I will create a dedicated audit_logs table where rows are strictly append-only, ensuring no one can tamper with the processing history.


3.2 Performance & Scalability


The platform must deliver quick processing turnarounds and support seamless system scaling.

Horizontal scalability: The application must be packaged as a stateless container image so additional OCR-scanning replicas can be started under heavy corporate workloads.
Response time: The end-to-end processing, layout analysis, and logical validation of a standard single-page document must take less than 3 seconds as perceived by the client (i.e., the upload call itself returns quickly; processing continues asynchronously).



I will keep the monolithic Spring Boot structure fully stateless and Docker-ready so it can scale horizontally if needed. To hit the under-3-second target for the client-facing call, I will use Spring's @Async thread pool to trigger the OCR/CV process in the background, keeping the web UI responsive and returning an immediate 202 Accepted.
