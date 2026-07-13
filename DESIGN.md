# Software Design Document (SDD) - validdoc (MVP)

## 1. System Architecture

The system follows a classic **Layered (Tiered) Monolithic Architecture** implemented via Spring Boot 3.x. Communication between layers is strictly unidirectional and decoupled using Data Transfer Objects (DTOs) to isolate database entities from the presentation layer.

*   **Presentation Layer (`@RestController`):** Exposes stateless REST endpoints. Responsible for HTTP request validation, parsing `MultipartFile` payloads, and returning unified JSON structures.
*   **Business Logic Layer (`@Service`):** Contains the core orchestration logic. It encapsulates the asynchronous lifecycle execution, regular expression compilation for data scanning, and the Tesseract execution framework.
*   **Data Access Layer (`@Repository`):** Built upon Spring Data JPA. It abstracts SQL operations into safe Java interfaces, leveraging Hibernate as the underlying Object-Relational Mapping (ORM) framework.

---

## 2. System Architecture & Data Flow Diagram

To comply with KVKK/GDPR and prevent local storage bottlenecks, files are processed strictly in-memory (RAM) via Java standard input streams.

```mermaid
graph TD
    Client[Client / Postman] -->|POST /api/documents/upload with JWT| Filter[JwtAuthenticationFilter]
    Filter -->|Invalid/Expired Token| AuthError[HTTP 401 Unauthorized]
    Filter -->|Valid Token| Controller[DocumentController]
    
    Controller -->|1. Generate Doc ID & Save Initial Metadata| DB[(PostgreSQL)]
    Controller -->|2. Return HTTP 202 Accepted| Client
    Controller -->|3. Delegate Task Asynchronously| Service[DocumentService]
    
    Service -->|Open InputStream Stream| OCR[OcrService Tess4j]
    OCR -->|Return Extracted Raw Text| Validation[ValidationService]
    
    Validation -->|Compile Regex & Calculate Score| Eval{Confidence >= 80%}
    Eval -->|Yes| Approved[Set Status: APPROVED]
    Eval -->|No| Review[Set Status: PENDING_REVIEW]
    
    Approved -->|Update Rows & Append Log| DB
    Review -->|Update Rows & Append Log| DB
```

The transactional execution inside `ValidationService` is marked with `@Transactional` to ensure that database metadata updates and immutable audit log writes succeed or fail as a single atomic unit.

---

## 3. Class Design & Package Structure

The application uses strict domain-driven sub-packages under the root `com.validdoc` package to prevent circular dependencies.

```mermaid
classDiagram
    class User {
        +Long id
        +String username
        +String password
        +UserRole role
    }
    class DocumentMetadata {
        +Long id
        +DocumentStatus status
        +Double confidenceScore
        +LocalDateTime uploadedAt
        +LocalDateTime processedAt
        +Long operatorId
    }
    class AuditLog {
        +Long id
        +String action
        +String performedBy
        +LocalDateTime timestamp
    }
    class UserRole {
        <<enumeration>>
        ADMIN
        OPERATOR
    }
    class DocumentStatus {
        <<enumeration>>
        PENDING_REVIEW
        APPROVED
        INVALID
    }
    User --> UserRole
    DocumentMetadata --> DocumentStatus
```

### 3.1 Directory Tree

```text
com.validdoc
│
├── config
│   ├── SecurityConfig.java (Configures BCrypt, CORS, and stateless Filter Chain)
│   ├── AsyncConfig.java (Configures ThreadPoolTaskExecutor limits)
│   └── TesseractConfig.java (Bean instantiation of Tesseract instances)
│
├── controller
│   ├── AuthController.java (Handles registration, authentication, and JWT issue)
│   └── DocumentController.java (Handles binary streams and operator review actions)
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
│   │   └── DocumentStatus.java
│   ├── User.java
│   ├── DocumentMetadata.java
│   └── AuditLog.java
│
├── repository
│   ├── UserRepository.java
│   ├── DocumentRepository.java
│   └── AuditLogRepository.java
│
└── service
    ├── OcrService.java (BufferedImage conversion and Tess4j bindings)
    ├── ValidationService.java (Regular expression matching engine)
    └── DocumentService.java (State tracking and persistence orchestrator)
```

---

## 4. Database Schema (ERD Model)

The PostgreSQL schema uses specialized native types and automatic key generators (`GenerationType.IDENTITY`).

### 4.1 `users`

| Column | Type | Constraints |
|---|---|---|
| `id` | `BigInt` | Primary Key, Auto-Increment |
| `username` | `VarChar(50)` | Unique, Indexed, Not Null |
| `password` | `VarChar(255)` | BCrypt Hashed (60-char), Not Null |
| `role` | `VarChar(20)` | Enum Mapped as String, Not Null |

### 4.2 `document_metadata`

| Column | Type | Constraints |
|---|---|---|
| `id` | `BigInt` | Primary Key, Auto-Increment |
| `status` | `VarChar(30)` | Enum Mapped as String (Default: PENDING_REVIEW) |
| `confidence_score` | `Double` | Nullable until OCR validation concludes |
| `uploaded_at` | `Timestamp` | UTC Metrics, Not Null |
| `processed_at` | `Timestamp` | Nullable (Set after validation concludes) |
| `operator_id` | `BigInt` | Foreign Key -> `users(id)`, Nullable |

### 4.3 `audit_logs`

> **Note:** This table is strictly append-only. Delete and Update queries are restricted at the repository configuration layer to preserve corporate auditing trail integrity.

| Column | Type | Constraints |
|---|---|---|
| `id` | `BigInt` | Primary Key, Auto-Increment |
| `action` | `VarChar(100)` | E.g., "DOCUMENT_SIZE_REJECTED", "MANUAL_APPROVE" |
| `performed_by` | `VarChar(50)` | String capture of context (Username or "SYSTEM") |
| `timestamp` | `Timestamp` | UTC Metrics, Not Null |

---

## 5. Core Algorithmic Decisions

### 5.1 In-Memory Document Processing & Leak Prevention

When a binary stream reaches `DocumentController`, it is processed inside a `try-with-resources` block to ensure the underlying stream closes automatically:

```java
try (InputStream is = file.getInputStream()) {
    BufferedImage image = ImageIO.read(is);
    String extractedText = ocrService.doOcr(image);
    // ... validation steps
}
```

Converting to `BufferedImage` forces the JVM to manage pixel data entirely within heap allocation structures. Once the reference scope closes, the graphic buffer becomes eligible for immediate Garbage Collection (GC) sweeps, avoiding persistent overhead or memory leaks.

### 5.2 Thread Pool Allocation Strategy for Async OCR

To prevent a sudden influx of large uploads from freezing the server's main web execution thread (Tomcat engine), Java handles OCR processing via an isolated `ThreadPoolTaskExecutor`:

*   **Core Pool Size:** 4 threads (Optimized for multi-core CPUs scaling text parsing tasks).
*   **Max Pool Size:** 8 threads (Upper safety bound during peak corporate processing hours).
*   **Queue Capacity:** 500 tasks. If the queue saturates, subsequent requests receive an immediate `HTTP 429 Too Many Requests` status, protecting the application from memory crash failures.

---

## 6. API Endpoints (Contract Design)

To prevent communication gaps during development, the core REST interface is strictly bound to the following endpoints:

| Method | Endpoint | Auth Role | Description | Request Body / Param | Response (Success) |
|---|---|---|---|---|---|
| `POST` | `/api/auth/login` | Public | Generates JWT Bearer Token | `JSON {username, password}` | `200 OK {token, role}` |
| `POST` | `/api/documents/upload` | `OPERATOR`, `ADMIN` | Accepts file, triggers async OCR | `form-data {file: MultipartFile}` | `202 Accepted {documentId, status}` |
| `GET` | `/api/documents/queue` | `OPERATOR`, `ADMIN` | Fetches `PENDING_REVIEW` docs | None | `200 OK [DocumentMetadata]` |
| `POST` | `/api/documents/{id}/verify` | `OPERATOR` | Manual status override | `JSON {status: APPROVED/INVALID}` | `200 OK {message: "Updated"}` |

---

## 7. Security Architecture (JWT Middleware)

Spring Security treats the application as a stateless system. The integration topology follows this sequential validation filter chain:

```mermaid
graph LR
    Req[Incoming HTTP Request] -->|Extract Token| Filter[JwtAuthenticationFilter]
    Filter -->|Invalid/Expired| AuthErr[401 Unauthorized Response]
    Filter -->|Valid Token -> Extract Claims| Context[SecurityContextHolder]
    Context -->|Enforce Method Security e.g., @PreAuthorize| Endpoint[Target Controller Endpoint]
```

---

## 8. Global Exception & Failure Handling Strategy

To avoid generic internal server errors (`HTTP 500`) and handle runtime anomalies safely, the application implements a centralized `@ControllerAdvice` handler mapping specific exceptions to structured error responses:

*   **`MaxUploadSizeExceededException`:** Returns `HTTP 413 Payload Too Large` with JSON: `{"error": "File size exceeds the maximum limit of 5MB"}`.
*   **`TesseractException` (OCR Engine Failure):** Caught gracefully. Automatically sets the target document's status to `PENDING_REVIEW` with a `0.0` confidence score, routing it straight to the human operator queue rather than dropping the request.
*   **`EntityNotFoundException`:** Returns `HTTP 404 Not Found` when an operator attempts to verify a non-existent document ID.
