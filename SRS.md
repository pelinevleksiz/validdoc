# Software Requirements Specification (SRS)

**Project Name:** validdoc  

---

## 1. Functional Requirements

### 1.1 User Authentication & Role-Based Access
* The system must provide a secure login interface with two distinct user roles: `admin` and `operator`, utilizing a modern token-based framework.
* *I will implement a standard spring security configuration with jwt (json web token) for stateless authentication. Instead of using heavy server-side sessions, successfully authenticated users will receive a secure token. Subsequent frontend requests (like document uploads) will pass this token in the auth header. Passwords will be stored using bcrypt hashing.*

### 1.2 Document Upload & Management
* The system must allow users to manually upload documents (pdf, png, jpeg) via a web-based drag-and-drop interface.
* *I am focusing on manual uploads to fit the 20-day timeline. Files will be processed on-the-fly without being permanently saved.*

### 1.3 Intelligent Document Scanning, Templating & Completeness Analysis
* The system must support two analysis modes to check if required fields (signatures, names, numbers etc.) are filled with logically valid data:
    1. **Templated validation:** For fixed forms (e.g. standard application forms), specific coordinate bounding boxes (name, signature box etc.) will be verified.
    2. **Template-free validation:** For documents with varying structures (e.g. invoices or contracts), the system will dynamically detect fields like "signature", "name", or "date" using computer vision and basic text positioning.
* **Signature and stamp detection:** The system must verify not just if the signature/stamp box is empty, but if it contains handwriting or a seal impression via pixel density and object detection.
* **Logical & format verification:** The system must perform logical validation on extracted text. If a field is filled with invalid or nonsense data (e.g. checking if an ID field contains exactly 11 digits, if a phone number matches standard formats, or if a text field contains keyboard-mashed gibberish), the field will be marked as invalid.
* *I will build this engine using open-source tools for fixed forms. I will use opencv to crop and analyze specific coordinates for pixel density (to detect ink presence in signatures). For non-fixed forms, tesseract ocr will help find text anchors to locate and validate empty, filled, or logically invalid zones without building complex deep learning pipelines from scratch.*

### 1.4 Workflow & Approval Management
* The system must route uncertain documents to manual verification, handle rejection communications automatically, and transition document states dynamically based on the analysis outcome.
* **Human-in-the-loop validation:** The system must generate a confidence score based on field completeness, logical correctness, and signature visibility. Documents falling below a predefined threshold (e.g. 80%) must be routed to an "operator approval queue" instead of being instantly rejected.
* **Advanced state mapping:** The system will assign one of five distinct statuses to the document: `PROCESSING` during analysis, `VALIDATED` if all rules pass, `REJECTED_EMPTY` if physically blank, `REJECTED_INVALID` if data is present but logically/format-wise incorrect, and `PENDING_REVIEW` if manual verification is required.
* *I will create a simple verification queue using basic document statuses (processing, validated, rejected_empty, rejected_invalid, pending_review). If the system is unsure about a signature or a text field, the operator will be able to review the document on a dashboard and manually change its state with a single click.*

---

## 2. Technical & Architectural Requirements

### 2.1 Backend Architecture
* The core system must be built using java with an enterprise-ready framework.
* *I will use spring boot 4.x to build a robust monolithic architecture. This keeps development fast and simple within our 20 day limit while remaining modular for future updates.*

### 2.2 OCR Engine Integration
* An optical character recognition tool must be integrated to read text, extract coordinate data, and perform logical format validation.
* *I will integrate tesseract ocr locally via java wrappers (Tess4J). This keeps the application self-contained, enables cropping specific coordinates for templated validation, and avoids dealing with cloud provider apis or external dependencies.*

### 2.3 Database & Persistence
* The system must persist user profiles, document processing metadata (status, validation scores, format error logs), and validation history. No physical document files shall be stored permanently on the server’s file system or database.
* *I will use postgresql combined with spring data jpa. To avoid server storage issues and comply with data privacy, uploaded files will be handled via spring’s MultipartFile and processed strictly in-memory (RAM). To prevent memory overload from simultaneous mass uploads, i will enforce a strict file size limit (e.g. maximum 5 megabyte per file) in the application properties. Once the image processing and OCR validation finishes, the file is instantly destroyed from ram, and only the execution logs (status, validation score, error details, timestamp, operator ID) are saved.*

---

## 3. Non-Functional Requirements

### 3.1 Security & Compliance
* The system must ensure strict data privacy and security compliance.
    1. **KVKK / GDPR compliance:** Personal data (names, phone numbers etc.) extracted from documents must be masked or encrypted within the database. The system must support data retention policies to permanently wipe documents when processed.
    2. **Immutable audit logs:** Every user action (including who uploaded, processed, or manually approved a document) must be recorded as an unchangeable log for corporate auditing.
* *I will enforce soft deletes and database-level encryption for sensitive fields. For compliance, I will create a dedicated audit_logs table where rows are strictly append-only, ensuring no one can tamper with the processing history.*

### 3.2 Performance & Scalability
* The platform must deliver quick processing turnarounds and support seamless system scaling.
    1. **Horizontal scalability:** The application architecture must allow containerization to spin up additional ocr scanning threads under heavy corporate workloads.
    2. **Response time:** The end-to-end processing, layout analysis, and logical validation of a standard single-page document must take less than 3 seconds.
* *I will keep the monolithic spring boot structure fully stateless and docker-ready so it can scale horizontally if needed. To hit the under 3-second target, I will use spring’s @async thread pool to trigger the ocr process in the background, keeping the web UI responsive.*
