# Software Design Document (SDD) - validdoc (MVP)

1. System Architecture

The system follows a classic Layered Monolithic Architecture implemented via Spring Boot 4.x, packaged as a stateless container image (see Dockerfile, §1.1) so it can scale horizontally by running additional replicas. Communication between layers is strictly unidirectional and decoupled using Data Transfer Objects (DTOs) to isolate database entities from the presentation layer.


Presentation Layer (@RestController): Exposes stateless REST endpoints. Responsible for HTTP request validation, parsing MultipartFile payloads, and returning unified JSON structures.
Business Logic Layer (@Service): Contains the core orchestration logic. It encapsulates the asynchronous lifecycle execution, PDF rasterization, regular expression compilation for data scanning, OpenCV coordinate/pixel density analysis for signature/stamp detection, the Tesseract execution framework, and rejection notifications.
Data Access Layer (@Repository): Built upon Spring Data JPA. It abstracts SQL operations into safe Java interfaces, leveraging Hibernate as the underlying Object-Relational Mapping (ORM) framework with database-level encryption for sensitive extracted-data fields.


1.1 Container Readiness

The application holds no local session or file state (per §5.1, all documents are processed strictly in-memory). A single-stage Dockerfile builds the Spring Boot fat JAR and runs it as a non-root user; horizontal scaling is achieved by running multiple container replicas behind a load balancer, with PostgreSQL as the sole shared state.


2. System Architecture & Data Flow Diagram

To comply with KVKK/GDPR and prevent local storage bottlenecks, files are processed strictly in-memory (RAM) via Java standard input streams. Uploaded documents are instantly processed and destroyed from RAM, leaving only metadata (and masked/encrypted extracted fields, subject to retention purge — see §4.2 and §5.3).

#mermaid-rol-r10 { font-family: "Anthropic Sans", system-ui, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; font-size: 16px; fill: rgb(229, 229, 229); }
#mermaid-rol-r10 .edge-animation-slow { stroke-dashoffset: 900; animation: 50s linear 0s infinite normal none running dash; stroke-linecap: round; stroke-dasharray: 9, 5 !important; }
#mermaid-rol-r10 .edge-animation-fast { stroke-dashoffset: 900; animation: 20s linear 0s infinite normal none running dash; stroke-linecap: round; stroke-dasharray: 9, 5 !important; }
#mermaid-rol-r10 .error-icon { fill: rgb(204, 120, 92); }
#mermaid-rol-r10 .error-text { fill: rgb(51, 135, 163); stroke: rgb(51, 135, 163); }
#mermaid-rol-r10 .edge-thickness-normal { stroke-width: 1px; }
#mermaid-rol-r10 .edge-thickness-thick { stroke-width: 3.5px; }
#mermaid-rol-r10 .edge-pattern-solid { stroke-dasharray: 0; }
#mermaid-rol-r10 .edge-thickness-invisible { stroke-width: 0; fill: none; }
#mermaid-rol-r10 .edge-pattern-dashed { stroke-dasharray: 3; }
#mermaid-rol-r10 .edge-pattern-dotted { stroke-dasharray: 2; }
#mermaid-rol-r10 .marker { fill: rgb(161, 161, 161); stroke: rgb(161, 161, 161); }
#mermaid-rol-r10 .marker.cross { stroke: rgb(161, 161, 161); }
#mermaid-rol-r10 svg { font-family: "Anthropic Sans", system-ui, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; font-size: 16px; }
#mermaid-rol-r10 p { margin: 0px; }
#mermaid-rol-r10 .label { font-family: "Anthropic Sans", system-ui, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; color: rgb(229, 229, 229); }
#mermaid-rol-r10 .cluster-label text { fill: rgb(51, 135, 163); }
#mermaid-rol-r10 .cluster-label span { color: rgb(51, 135, 163); }
#mermaid-rol-r10 .cluster-label span p { background-color: transparent; }
#mermaid-rol-r10 .label text, #mermaid-rol-r10 span { fill: rgb(229, 229, 229); color: rgb(229, 229, 229); }
#mermaid-rol-r10 .node rect, #mermaid-rol-r10 .node circle, #mermaid-rol-r10 .node ellipse, #mermaid-rol-r10 .node polygon, #mermaid-rol-r10 .node path { fill: transparent; stroke: rgb(161, 161, 161); stroke-width: 1px; }
#mermaid-rol-r10 .rough-node .label text, #mermaid-rol-r10 .node .label text, #mermaid-rol-r10 .image-shape .label, #mermaid-rol-r10 .icon-shape .label { text-anchor: middle; }
#mermaid-rol-r10 .node .katex path { fill: rgb(0, 0, 0); stroke: rgb(0, 0, 0); stroke-width: 1px; }
#mermaid-rol-r10 .rough-node .label, #mermaid-rol-r10 .node .label, #mermaid-rol-r10 .image-shape .label, #mermaid-rol-r10 .icon-shape .label { text-align: center; }
#mermaid-rol-r10 .node.clickable { cursor: pointer; }
#mermaid-rol-r10 .root .anchor path { stroke-width: 0; stroke: rgb(161, 161, 161); fill: rgb(161, 161, 161) !important; }
#mermaid-rol-r10 .arrowheadPath { fill: rgb(11, 11, 11); }
#mermaid-rol-r10 .edgePath .path { stroke: rgb(161, 161, 161); stroke-width: 1px; }
#mermaid-rol-r10 .flowchart-link { stroke: rgb(161, 161, 161); fill: none; }
#mermaid-rol-r10 .edgeLabel { background-color: transparent; text-align: center; }
#mermaid-rol-r10 .edgeLabel p { background-color: transparent; }
#mermaid-rol-r10 .edgeLabel rect { opacity: 0.5; background-color: transparent; fill: transparent; }
#mermaid-rol-r10 .labelBkg { background-color: rgba(0, 0, 0, 0.5); }
#mermaid-rol-r10 .cluster rect { fill: rgb(204, 120, 92); stroke: rgb(138, 115, 107); stroke-width: 1px; }
#mermaid-rol-r10 .cluster text { fill: rgb(51, 135, 163); }
#mermaid-rol-r10 .cluster span { color: rgb(51, 135, 163); }
#mermaid-rol-r10 div.mermaidTooltip { position: absolute; text-align: center; max-width: 200px; padding: 2px; font-family: "Anthropic Sans", system-ui, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; font-size: 12px; background: rgb(204, 120, 92); border: 1px solid rgb(138, 115, 107); border-radius: 2px; pointer-events: none; z-index: 100; }
#mermaid-rol-r10 .flowchartTitleText { text-anchor: middle; font-size: 18px; fill: rgb(229, 229, 229); }
#mermaid-rol-r10 rect.text { fill: none; stroke-width: 0; }
#mermaid-rol-r10 .icon-shape, #mermaid-rol-r10 .image-shape { background-color: transparent; text-align: center; }
#mermaid-rol-r10 .icon-shape p, #mermaid-rol-r10 .image-shape p { background-color: transparent; padding: 2px; }
#mermaid-rol-r10 .icon-shape .label rect, #mermaid-rol-r10 .image-shape .label rect { opacity: 0.5; background-color: transparent; fill: transparent; }
#mermaid-rol-r10 .label-icon { display: inline-block; height: 1em; overflow: visible; vertical-align: -0.125em; }
#mermaid-rol-r10 .node .label-icon path { fill: currentcolor; stroke: revert; stroke-width: revert; }
#mermaid-rol-r10 .node .neo-node { stroke: rgb(161, 161, 161); }
#mermaid-rol-r10 [data-look="neo"].node rect, #mermaid-rol-r10 [data-look="neo"].cluster rect, #mermaid-rol-r10 [data-look="neo"].node polygon { stroke: url("#mermaid-rol-r10-gradient"); filter: drop-shadow(rgb(185, 185, 185) 1px 2px 2px); }
#mermaid-rol-r10 [data-look="neo"].node path { stroke: url("#mermaid-rol-r10-gradient"); stroke-width: 1px; }
#mermaid-rol-r10 [data-look="neo"].node .outer-path { filter: drop-shadow(rgb(185, 185, 185) 1px 2px 2px); }
#mermaid-rol-r10 [data-look="neo"].node .neo-line path { stroke: rgb(161, 161, 161); filter: none; }
#mermaid-rol-r10 [data-look="neo"].node circle { stroke: url("#mermaid-rol-r10-gradient"); filter: drop-shadow(rgb(185, 185, 185) 1px 2px 2px); }
#mermaid-rol-r10 [data-look="neo"].node circle .state-start { fill: rgb(0, 0, 0); }
#mermaid-rol-r10 [data-look="neo"].icon-shape .icon { fill: url("#mermaid-rol-r10-gradient"); filter: drop-shadow(rgb(185, 185, 185) 1px 2px 2px); }
#mermaid-rol-r10 [data-look="neo"].icon-shape .icon-neo path { stroke: url("#mermaid-rol-r10-gradient"); filter: drop-shadow(rgb(185, 185, 185) 1px 2px 2px); }
#mermaid-rol-r10 :root { --mermaid-font-family: "Anthropic Sans",system-ui,"Segoe UI",Roboto,Helvetica,Arial,sans-serif; }POST/api/documents/uploadwith JWTInvalid/Expired TokenValid Token1. Generate Doc ID & SaveInitial Status: PROCESSING2. Return HTTP 202Accepted3. Delegate TaskAsynchronouslyIf PDF: Rasterize to ImageIf PNG/JPEG: Use DirectlyBufferedImageReturn Extracted Text &Pixel DensitySelect Mode: Templated orTemplate-freePassFail - Physical BlankFail - Format/Logical ErrorsUncertain / ThresholdBorderUpdate Rows, StoreMasked/Encrypted Fields &Append LogUpdate Rows & AppendLogUpdate Rows, Save FormatErrors & LogUpdate Rows & AppendLogTrigger AsyncTrigger AsyncClient / Web UIJwtAuthenticationFilterHTTP 401 UnauthorizedDocumentControllerPostgreSQLDocumentServicePdfRasterService PDFBoxOcrService Tess4j / OpenCVValidationServiceConfidence = configurablethreshold & Logical RulesPass?Set Status: VALIDATEDSet Status:REJECTED_EMPTYSet Status:REJECTED_INVALIDSet Status:PENDING_REVIEWNotificationService

The transactional execution inside ValidationService is marked with @Transactional to ensure that database metadata updates and immutable audit log writes succeed or fail as a single atomic unit. NotificationService invocation happens outside this transaction (fire-and-forget) so a downstream notification failure never rolls back a validation result.


3. Class Design & Package Structure

The application uses strict domain-driven sub-packages under the root com.validdoc package to prevent circular dependencies.

#mermaid-rom-r11 { font-family: "Anthropic Sans", system-ui, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; font-size: 16px; fill: rgb(229, 229, 229); }
#mermaid-rom-r11 .edge-animation-slow { stroke-dashoffset: 900; animation: 50s linear 0s infinite normal none running dash; stroke-linecap: round; stroke-dasharray: 9, 5 !important; }
#mermaid-rom-r11 .edge-animation-fast { stroke-dashoffset: 900; animation: 20s linear 0s infinite normal none running dash; stroke-linecap: round; stroke-dasharray: 9, 5 !important; }
#mermaid-rom-r11 .error-icon { fill: rgb(204, 120, 92); }
#mermaid-rom-r11 .error-text { fill: rgb(51, 135, 163); stroke: rgb(51, 135, 163); }
#mermaid-rom-r11 .edge-thickness-normal { stroke-width: 1px; }
#mermaid-rom-r11 .edge-thickness-thick { stroke-width: 3.5px; }
#mermaid-rom-r11 .edge-pattern-solid { stroke-dasharray: 0; }
#mermaid-rom-r11 .edge-thickness-invisible { stroke-width: 0; fill: none; }
#mermaid-rom-r11 .edge-pattern-dashed { stroke-dasharray: 3; }
#mermaid-rom-r11 .edge-pattern-dotted { stroke-dasharray: 2; }
#mermaid-rom-r11 .marker { fill: rgb(161, 161, 161); stroke: rgb(161, 161, 161); }
#mermaid-rom-r11 .marker.cross { stroke: rgb(161, 161, 161); }
#mermaid-rom-r11 svg { font-family: "Anthropic Sans", system-ui, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; font-size: 16px; }
#mermaid-rom-r11 p { margin: 0px; }
#mermaid-rom-r11 g.classGroup text { fill: rgb(161, 161, 161); stroke: none; font-family: "Anthropic Sans", system-ui, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; font-size: 10px; }
#mermaid-rom-r11 g.classGroup text .title { font-weight: bolder; }
#mermaid-rom-r11 .cluster-label text { fill: rgb(51, 135, 163); }
#mermaid-rom-r11 .cluster-label span { color: rgb(51, 135, 163); }
#mermaid-rom-r11 .cluster-label span p { background-color: transparent; }
#mermaid-rom-r11 .cluster rect { fill: rgb(204, 120, 92); stroke: rgb(138, 115, 107); stroke-width: 1px; }
#mermaid-rom-r11 .cluster text { fill: rgb(51, 135, 163); }
#mermaid-rom-r11 .cluster span { color: rgb(51, 135, 163); }
#mermaid-rom-r11 .nodeLabel, #mermaid-rom-r11 .edgeLabel { color: rgb(229, 229, 229); }
#mermaid-rom-r11 .noteLabel .nodeLabel, #mermaid-rom-r11 .noteLabel .edgeLabel { color: rgb(229, 229, 229); }
#mermaid-rom-r11 .edgeLabel .label rect { fill: transparent; }
#mermaid-rom-r11 .label text { fill: rgb(229, 229, 229); }
#mermaid-rom-r11 .labelBkg { background: transparent; }
#mermaid-rom-r11 .edgeLabel .label span { background: transparent; }
#mermaid-rom-r11 .classTitle { font-weight: bolder; }
#mermaid-rom-r11 .node rect, #mermaid-rom-r11 .node circle, #mermaid-rom-r11 .node ellipse, #mermaid-rom-r11 .node polygon, #mermaid-rom-r11 .node path { fill: transparent; stroke: rgb(161, 161, 161); stroke-width: 1; }
#mermaid-rom-r11 .divider { stroke: rgb(161, 161, 161); stroke-width: 1; }
#mermaid-rom-r11 g.clickable { cursor: pointer; }
#mermaid-rom-r11 g.classGroup rect { fill: transparent; stroke: rgb(161, 161, 161); }
#mermaid-rom-r11 g.classGroup line { stroke: rgb(161, 161, 161); stroke-width: 1; }
#mermaid-rom-r11 .classLabel .box { stroke: none; stroke-width: 0; fill: transparent; opacity: 0.5; }
#mermaid-rom-r11 .classLabel .label { fill: rgb(161, 161, 161); font-size: 10px; }
#mermaid-rom-r11 .relation { stroke: rgb(161, 161, 161); stroke-width: 1; fill: none; }
#mermaid-rom-r11 .dashed-line { stroke-dasharray: 3; }
#mermaid-rom-r11 .dotted-line { stroke-dasharray: 1, 2; }
#mermaid-rom-r11 [id$="-compositionStart"], #mermaid-rom-r11 .composition { stroke-width: 1; fill: rgb(161, 161, 161) !important; stroke: rgb(161, 161, 161) !important; }
#mermaid-rom-r11 [id$="-compositionEnd"], #mermaid-rom-r11 .composition { stroke-width: 1; fill: rgb(161, 161, 161) !important; stroke: rgb(161, 161, 161) !important; }
#mermaid-rom-r11 [id$="-dependencyStart"], #mermaid-rom-r11 .dependency { stroke-width: 1; fill: rgb(161, 161, 161) !important; stroke: rgb(161, 161, 161) !important; }
#mermaid-rom-r11 [id$="-dependencyEnd"], #mermaid-rom-r11 .dependency { stroke-width: 1; fill: rgb(161, 161, 161) !important; stroke: rgb(161, 161, 161) !important; }
#mermaid-rom-r11 [id$="-extensionStart"], #mermaid-rom-r11 .extension { stroke-width: 1; fill: transparent !important; stroke: rgb(161, 161, 161) !important; }
#mermaid-rom-r11 [id$="-extensionEnd"], #mermaid-rom-r11 .extension { stroke-width: 1; fill: transparent !important; stroke: rgb(161, 161, 161) !important; }
#mermaid-rom-r11 [id$="-aggregationStart"], #mermaid-rom-r11 .aggregation { stroke-width: 1; fill: transparent !important; stroke: rgb(161, 161, 161) !important; }
#mermaid-rom-r11 [id$="-aggregationEnd"], #mermaid-rom-r11 .aggregation { stroke-width: 1; fill: transparent !important; stroke: rgb(161, 161, 161) !important; }
#mermaid-rom-r11 [id$="-lollipopStart"], #mermaid-rom-r11 .lollipop { stroke-width: 1; fill: transparent !important; stroke: rgb(161, 161, 161) !important; }
#mermaid-rom-r11 [id$="-lollipopEnd"], #mermaid-rom-r11 .lollipop { stroke-width: 1; fill: transparent !important; stroke: rgb(161, 161, 161) !important; }
#mermaid-rom-r11 .edgeTerminals { font-size: 11px; line-height: initial; }
#mermaid-rom-r11 .classTitleText { text-anchor: middle; font-size: 18px; fill: rgb(229, 229, 229); }
#mermaid-rom-r11 .edgeLabel[data-look="neo"] { background-color: transparent; text-align: center; }
#mermaid-rom-r11 .edgeLabel[data-look="neo"] p { background-color: transparent; }
#mermaid-rom-r11 .edgeLabel[data-look="neo"] rect { opacity: 0.5; background-color: transparent; fill: transparent; }
#mermaid-rom-r11 .label-icon { display: inline-block; height: 1em; overflow: visible; vertical-align: -0.125em; }
#mermaid-rom-r11 .node .label-icon path { fill: currentcolor; stroke: revert; stroke-width: revert; }
#mermaid-rom-r11 .node .neo-node { stroke: rgb(161, 161, 161); }
#mermaid-rom-r11 [data-look="neo"].node rect, #mermaid-rom-r11 [data-look="neo"].cluster rect, #mermaid-rom-r11 [data-look="neo"].node polygon { stroke: url("#mermaid-rom-r11-gradient"); filter: drop-shadow(rgb(185, 185, 185) 1px 2px 2px); }
#mermaid-rom-r11 [data-look="neo"].node path { stroke: url("#mermaid-rom-r11-gradient"); stroke-width: 1px; }
#mermaid-rom-r11 [data-look="neo"].node .outer-path { filter: drop-shadow(rgb(185, 185, 185) 1px 2px 2px); }
#mermaid-rom-r11 [data-look="neo"].node .neo-line path { stroke: rgb(161, 161, 161); filter: none; }
#mermaid-rom-r11 [data-look="neo"].node circle { stroke: url("#mermaid-rom-r11-gradient"); filter: drop-shadow(rgb(185, 185, 185) 1px 2px 2px); }
#mermaid-rom-r11 [data-look="neo"].node circle .state-start { fill: rgb(0, 0, 0); }
#mermaid-rom-r11 [data-look="neo"].icon-shape .icon { fill: url("#mermaid-rom-r11-gradient"); filter: drop-shadow(rgb(185, 185, 185) 1px 2px 2px); }
#mermaid-rom-r11 [data-look="neo"].icon-shape .icon-neo path { stroke: url("#mermaid-rom-r11-gradient"); filter: drop-shadow(rgb(185, 185, 185) 1px 2px 2px); }
#mermaid-rom-r11 :root { --mermaid-font-family: "Anthropic Sans",system-ui,"Segoe UI",Roboto,Helvetica,Arial,sans-serif; }User+Long id+String username+String password+UserRole roleDocumentMetadata+Long id+DocumentStatus status+ValidationMode validationMode+Double confidenceScore+String validationErrorLogs+String extractedMaskedData+LocalDateTime uploadedAt+LocalDateTime processedAt+LocalDateTime purgeAt+Long operatorIdAuditLog+Long id+String action+String performedBy+LocalDateTime timestamp«enumeration»UserRoleADMINOPERATOR«enumeration»DocumentStatusPROCESSINGVALIDATEDREJECTED_EMPTYREJECTED_INVALIDPENDING_REVIEW«enumeration»ValidationModeTEMPLATEDTEMPLATE_FREE

3.1 Directory Tree

com.validdoc
│
├── config
│   ├── SecurityConfig.java (Configures BCrypt, CORS, and stateless Filter Chain)
│   ├── AsyncConfig.java (Configures ThreadPoolTaskExecutor limits for < 3s response)
│   ├── TesseractConfig.java (Bean instantiation of Tesseract instances)
│   └── ValidationProperties.java (Binds validation.confidence-threshold & retention window from application.yml)
│
├── controller
│   ├── AuthController.java (Handles registration, authentication, and JWT issue)
│   └── DocumentController.java (Handles binary streams, upload, and manual operator reviews)
│
├── dto
│   ├── request
│   │   ├── LoginRequest.java
│   │   └── VerificationRequest.java
│   └── response
│       ├── AuthResponse.java
│       └── DocumentSummaryResponse.java
│
├── model
│   ├── enums
│   │   ├── UserRole.java
│   │   ├── DocumentStatus.java
│   │   └── ValidationMode.java
│   ├── User.java
│   ├── DocumentMetadata.java (@ColumnTransformer encryption on extractedMaskedData only)
│   └── AuditLog.java
│
├── repository
│   ├── UserRepository.java
│   ├── DocumentRepository.java
│   └── AuditLogRepository.java (Immutable repository configuration)
│
├── scheduler
│   └── RetentionCleanupJob.java (Periodically purges/anonymizes rows past purgeAt)
│
└── service
    ├── PdfRasterService.java (Renders first PDF page to BufferedImage via Apache PDFBox)
    ├── OcrService.java (BufferedImage conversion, Tess4j bindings, and OpenCV cropping)
    ├── ValidationService.java (Templated & template-free matching engine, regex, pixel density checks)
    ├── DocumentService.java (State tracking, asynchronous orchestration, and persistence)
    └── NotificationService.java (Async hook fired on REJECTED_* transitions; stubbed/logged for MVP)


4. Database Schema (ERD Model)

The PostgreSQL schema uses specialized native types, automatic key generators (GenerationType.IDENTITY), and database-level column encryption for sensitive extracted personal data to satisfy KVKK/GDPR requirements.

4.1 users

ColumnTypeConstraintsidBigIntPrimary Key, Auto-IncrementusernameVarChar(50)Unique, Indexed, Not Null (plaintext login identifier — not extracted document data, not subject to §4.2 masking rules)passwordVarChar(255)BCrypt Hashed (60-char), Not NullroleVarChar(20)Enum Mapped as String (ADMIN, OPERATOR), Not Null

4.2 document_metadata

ColumnTypeConstraintsidBigIntPrimary Key, Auto-IncrementstatusVarChar(30)Enum Mapped as String (Default: PROCESSING)validation_modeVarChar(20)Enum Mapped as String (TEMPLATED, TEMPLATE_FREE), Nullable until analysis startsconfidence_scoreDoubleNullable until OCR/CV validation concludesvalidation_error_logsTextStores logical/format verification error details, Nullableextracted_masked_dataTextColumn-level encrypted JSON blob of masked personal fields (name, phone, etc.); Nullableuploaded_atTimestampUTC Metrics, Not Nullprocessed_atTimestampNullable (Set after validation concludes)purge_atTimestampNot Null; set to processed_at + retention window at write time; consumed by RetentionCleanupJoboperator_idBigIntForeign Key -> users(id), Nullable (Set only if manually reviewed)

4.3 audit_logs


Note: This table is strictly append-only. Delete and Update queries are restricted at the repository configuration layer to preserve corporate auditing trail integrity. It is exempt from the purge_at retention mechanism above because it stores only action metadata (who/what/when), never personal document content.



ColumnTypeConstraintsidBigIntPrimary Key, Auto-IncrementactionVarChar(100)E.g., "DOCUMENT_SIZE_REJECTED", "MANUAL_APPROVE", "DOCUMENT_UPLOADED"performed_byVarChar(50)String capture of context (Username or "SYSTEM")timestampTimestampUTC Metrics, Not Null


5. Core Algorithmic Decisions

5.1 In-Memory Document Processing & Leak Prevention

When a binary stream reaches DocumentController, it is processed inside a try-with-resources block to ensure the underlying stream closes automatically. PDFs are rasterized first so every downstream step operates on a uniform BufferedImage:

javatry (InputStream is = file.getInputStream()) {
    BufferedImage image = contentType.equals("application/pdf")
        ? pdfRasterService.renderFirstPage(is)   // Apache PDFBox, in-memory only
        : ImageIO.read(is);                      // PNG / JPEG path

    // OpenCV methods check pixel density at specific coordinates for signatures
    // Tesseract extracts raw text for logical & format regex verification
    String extractedText = ocrService.doOcr(image);
    // ... validation steps
}

Converting to BufferedImage forces the JVM to manage pixel data entirely within heap allocation structures. Once the reference scope closes, the graphic buffer becomes eligible for immediate Garbage Collection (GC) sweeps. A strict file size limit (e.g., maximum 5MB) is enforced via application configuration to protect system memory, and applies equally to PDF and image uploads.

5.2 Thread Pool Allocation Strategy for Async OCR

To satisfy the under-3-second response time requirement and prevent a sudden influx of uploads from freezing Tomcat's main execution threads, processing runs via an isolated ThreadPoolTaskExecutor:


Core Pool Size: 4 threads (Optimized for multi-core CPUs scaling text parsing tasks).
Max Pool Size: 8 threads (Upper safety bound during peak corporate processing hours).
Queue Capacity: 500 tasks. If the queue saturates, subsequent requests receive an immediate HTTP 429 Too Many Requests status, protecting the application from memory crash failures.


5.3 Configurable Confidence Threshold & Retention Window

Both values are externalized to application.yml and bound via ValidationProperties, so they can be tuned per environment without a code change:

yamlvalidation:
  confidence-threshold: 0.80   # SRS 1.4 — documents below this go to PENDING_REVIEW
  retention-days: 90           # SRS 2.3 / 3.1.1 — window before extracted_masked_data is purged

RetentionCleanupJob runs on a daily schedule (@Scheduled(cron = "...")), selecting all document_metadata rows where purge_at <= now(), nulling extracted_masked_data, and writing a single "RETENTION_PURGE" entry per row to audit_logs — preserving the audit trail while satisfying the erasure requirement.


6. API Endpoints (Contract Design)

MethodEndpointAuth RoleDescriptionRequest Body / ParamResponse (Success)POST/api/auth/loginPublicGenerates JWT Bearer TokenJSON {username, password}200 OK {token, role}POST/api/documents/uploadOPERATOR, ADMINAccepts file (PDF/PNG/JPEG), triggers async rasterization (if PDF), OCR & CV validationform-data {file: MultipartFile}202 Accepted {documentId, status: "PROCESSING"}GET/api/documents/queueOPERATOR, ADMINFetches PENDING_REVIEW docsNone200 OK [DocumentMetadata]POST/api/documents/{id}/verifyOPERATORManual status overrideJSON {status: VALIDATED/REJECTED_EMPTY/REJECTED_INVALID}200 OK {message: "Updated"}


7. Security Architecture (JWT Middleware)

Spring Security treats the application as a stateless system. The integration topology follows this sequential validation filter chain:

#mermaid-roq-r12 { font-family: "Anthropic Sans", system-ui, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; font-size: 16px; fill: rgb(229, 229, 229); }
#mermaid-roq-r12 .edge-animation-slow { stroke-dashoffset: 900; animation: 50s linear 0s infinite normal none running dash; stroke-linecap: round; stroke-dasharray: 9, 5 !important; }
#mermaid-roq-r12 .edge-animation-fast { stroke-dashoffset: 900; animation: 20s linear 0s infinite normal none running dash; stroke-linecap: round; stroke-dasharray: 9, 5 !important; }
#mermaid-roq-r12 .error-icon { fill: rgb(204, 120, 92); }
#mermaid-roq-r12 .error-text { fill: rgb(51, 135, 163); stroke: rgb(51, 135, 163); }
#mermaid-roq-r12 .edge-thickness-normal { stroke-width: 1px; }
#mermaid-roq-r12 .edge-thickness-thick { stroke-width: 3.5px; }
#mermaid-roq-r12 .edge-pattern-solid { stroke-dasharray: 0; }
#mermaid-roq-r12 .edge-thickness-invisible { stroke-width: 0; fill: none; }
#mermaid-roq-r12 .edge-pattern-dashed { stroke-dasharray: 3; }
#mermaid-roq-r12 .edge-pattern-dotted { stroke-dasharray: 2; }
#mermaid-roq-r12 .marker { fill: rgb(161, 161, 161); stroke: rgb(161, 161, 161); }
#mermaid-roq-r12 .marker.cross { stroke: rgb(161, 161, 161); }
#mermaid-roq-r12 svg { font-family: "Anthropic Sans", system-ui, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; font-size: 16px; }
#mermaid-roq-r12 p { margin: 0px; }
#mermaid-roq-r12 .label { font-family: "Anthropic Sans", system-ui, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; color: rgb(229, 229, 229); }
#mermaid-roq-r12 .cluster-label text { fill: rgb(51, 135, 163); }
#mermaid-roq-r12 .cluster-label span { color: rgb(51, 135, 163); }
#mermaid-roq-r12 .cluster-label span p { background-color: transparent; }
#mermaid-roq-r12 .label text, #mermaid-roq-r12 span { fill: rgb(229, 229, 229); color: rgb(229, 229, 229); }
#mermaid-roq-r12 .node rect, #mermaid-roq-r12 .node circle, #mermaid-roq-r12 .node ellipse, #mermaid-roq-r12 .node polygon, #mermaid-roq-r12 .node path { fill: transparent; stroke: rgb(161, 161, 161); stroke-width: 1px; }
#mermaid-roq-r12 .rough-node .label text, #mermaid-roq-r12 .node .label text, #mermaid-roq-r12 .image-shape .label, #mermaid-roq-r12 .icon-shape .label { text-anchor: middle; }
#mermaid-roq-r12 .node .katex path { fill: rgb(0, 0, 0); stroke: rgb(0, 0, 0); stroke-width: 1px; }
#mermaid-roq-r12 .rough-node .label, #mermaid-roq-r12 .node .label, #mermaid-roq-r12 .image-shape .label, #mermaid-roq-r12 .icon-shape .label { text-align: center; }
#mermaid-roq-r12 .node.clickable { cursor: pointer; }
#mermaid-roq-r12 .root .anchor path { stroke-width: 0; stroke: rgb(161, 161, 161); fill: rgb(161, 161, 161) !important; }
#mermaid-roq-r12 .arrowheadPath { fill: rgb(11, 11, 11); }
#mermaid-roq-r12 .edgePath .path { stroke: rgb(161, 161, 161); stroke-width: 1px; }
#mermaid-roq-r12 .flowchart-link { stroke: rgb(161, 161, 161); fill: none; }
#mermaid-roq-r12 .edgeLabel { background-color: transparent; text-align: center; }
#mermaid-roq-r12 .edgeLabel p { background-color: transparent; }
#mermaid-roq-r12 .edgeLabel rect { opacity: 0.5; background-color: transparent; fill: transparent; }
#mermaid-roq-r12 .labelBkg { background-color: rgba(0, 0, 0, 0.5); }
#mermaid-roq-r12 .cluster rect { fill: rgb(204, 120, 92); stroke: rgb(138, 115, 107); stroke-width: 1px; }
#mermaid-roq-r12 .cluster text { fill: rgb(51, 135, 163); }
#mermaid-roq-r12 .cluster span { color: rgb(51, 135, 163); }
#mermaid-roq-r12 div.mermaidTooltip { position: absolute; text-align: center; max-width: 200px; padding: 2px; font-family: "Anthropic Sans", system-ui, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; font-size: 12px; background: rgb(204, 120, 92); border: 1px solid rgb(138, 115, 107); border-radius: 2px; pointer-events: none; z-index: 100; }
#mermaid-roq-r12 .flowchartTitleText { text-anchor: middle; font-size: 18px; fill: rgb(229, 229, 229); }
#mermaid-roq-r12 rect.text { fill: none; stroke-width: 0; }
#mermaid-roq-r12 .icon-shape, #mermaid-roq-r12 .image-shape { background-color: transparent; text-align: center; }
#mermaid-roq-r12 .icon-shape p, #mermaid-roq-r12 .image-shape p { background-color: transparent; padding: 2px; }
#mermaid-roq-r12 .icon-shape .label rect, #mermaid-roq-r12 .image-shape .label rect { opacity: 0.5; background-color: transparent; fill: transparent; }
#mermaid-roq-r12 .label-icon { display: inline-block; height: 1em; overflow: visible; vertical-align: -0.125em; }
#mermaid-roq-r12 .node .label-icon path { fill: currentcolor; stroke: revert; stroke-width: revert; }
#mermaid-roq-r12 .node .neo-node { stroke: rgb(161, 161, 161); }
#mermaid-roq-r12 [data-look="neo"].node rect, #mermaid-roq-r12 [data-look="neo"].cluster rect, #mermaid-roq-r12 [data-look="neo"].node polygon { stroke: url("#mermaid-roq-r12-gradient"); filter: drop-shadow(rgb(185, 185, 185) 1px 2px 2px); }
#mermaid-roq-r12 [data-look="neo"].node path { stroke: url("#mermaid-roq-r12-gradient"); stroke-width: 1px; }
#mermaid-roq-r12 [data-look="neo"].node .outer-path { filter: drop-shadow(rgb(185, 185, 185) 1px 2px 2px); }
#mermaid-roq-r12 [data-look="neo"].node .neo-line path { stroke: rgb(161, 161, 161); filter: none; }
#mermaid-roq-r12 [data-look="neo"].node circle { stroke: url("#mermaid-roq-r12-gradient"); filter: drop-shadow(rgb(185, 185, 185) 1px 2px 2px); }
#mermaid-roq-r12 [data-look="neo"].node circle .state-start { fill: rgb(0, 0, 0); }
#mermaid-roq-r12 [data-look="neo"].icon-shape .icon { fill: url("#mermaid-roq-r12-gradient"); filter: drop-shadow(rgb(185, 185, 185) 1px 2px 2px); }
#mermaid-roq-r12 [data-look="neo"].icon-shape .icon-neo path { stroke: url("#mermaid-roq-r12-gradient"); filter: drop-shadow(rgb(185, 185, 185) 1px 2px 2px); }
#mermaid-roq-r12 :root { --mermaid-font-family: "Anthropic Sans",system-ui,"Segoe UI",Roboto,Helvetica,Arial,sans-serif; }Extract TokenInvalid/ExpiredValid Token - Extract ClaimsEnforce Method Securitye.g., @PreAuthorizeIncoming HTTP RequestJwtAuthenticationFilter401 Unauthorized ResponseSecurityContextHolderTarget Controller Endpoint


8. Global Exception & Failure Handling Strategy

To avoid generic internal server errors (HTTP 500) and handle runtime anomalies safely, the application implements a centralized @ControllerAdvice handler mapping specific exceptions to structured error responses:


MaxUploadSizeExceededException: Returns HTTP 413 Payload Too Large with JSON: {"error": "File size exceeds the maximum limit of 5MB"}.
PdfRasterizationException (corrupted/unreadable PDF): Caught gracefully. Sets the target document's status to PENDING_REVIEW with a 0.0 confidence score, logs the exception, and routes it straight to the human operator queue — mirroring the Tesseract/OpenCV failure path below.
TesseractException / OpenCVException (Engine Failures): Caught gracefully. Automatically sets the target document's status to PENDING_REVIEW with a 0.0 confidence score and logs the exception, routing it straight to the human operator queue.
EntityNotFoundException: Returns HTTP 404 Not Found when an operator attempts to verify a non-existent document ID.
