# Test Plan — validdoc

**Standard reference:** IEEE 829-2008 (Software Test Documentation). Note: this standard was superseded by ISO/IEC/IEEE 29119-3 in 2013; the structure here follows IEEE 829 per the academic advisor's requirement.

## 1. Introduction

This document defines the test scope, approach, and acceptance criteria for the validdoc document validation system, a Spring Boot / React application that validates uploaded documents against admin-defined templates using OCR and rule-based checks.

## 2. Test Items

- Backend REST API — Spring Boot 4.1.0, Java 21
- Frontend single-page application — React 19, TypeScript
- Database schema and migrations — PostgreSQL 16, Flyway
- OCR / image processing engine — Tesseract (via Tess4J 5.12.0), OpenCV (via the `org.openpnp:opencv` binding, version 4.9.0-0) — see §7, Known Limitations

## 3. Features to Be Tested

- Authentication, authorization, and role-based access control
- User management (creation, deactivation, password change, admin-initiated password reset)
- Template creation, segment/rule definition, preview
- Document upload and the automatic validation engine (OCR + rule matching, including TC Kimlik No / VKN checksum validation and the international phone rule)
- Review queue, segment resolution, manual override
- Audit log accuracy, including target-user attribution
- Retention period automatic cleanup (segment result anonymization and segment image purge)
- Framework-level exception handling (unmatched routes, wrong HTTP methods, missing parameters, type mismatches)
- Frontend component behavior (form validation, error display, conditional visibility by role)

## 4. Features Not to Be Tested

- Load/performance testing (out of scope — the system is designed for small-team internal use)
- Multi-instance/horizontal scaling behavior (the login and upload rate limiters are held in memory per instance; this is documented in the SRS as an accepted constraint, not validated under multi-instance load)
- Independent third-party penetration testing (known vulnerability classes — IDOR, missing framework-level exception handling — were identified and closed via code review during Items 2–18 of the release backlog; no external penetration test was commissioned)

## 5. Approach

A three-layer strategy is followed:

1. **Automated integration tests (backend)** — 70 test cases (`ApiIntegrationTest`: 65, `SmokeTest`: 4, `ValiddocApplicationTests`: 1), run with JUnit 5 against a real Spring Boot application context (`@SpringBootTest`) and an isolated Testcontainers-managed PostgreSQL 16 (`postgres:16-alpine`) instance, exercising the system end-to-end over HTTP via `MockMvc`.
2. **Automated component tests (frontend)** — 16 test cases across 4 files (`Login.test.tsx`, `Upload.test.tsx`, `TemplateNew.test.tsx`, `DocumentDetail.test.tsx`), using Vitest with jsdom and Testing Library, simulating real DOM interaction with the backend API mocked at the module boundary.
3. **Manual accuracy campaign** — using a large set of synthetically generated templates and documents (`SyntheticDocumentGenerator`, producing 4 templates × 4 fill variants × 3 degradation levels) plus a small number of genuinely scanned documents, to measure the OCR engine's real-world accuracy under varying document quality. This is an area the automated suite deliberately does not cover, since OCR accuracy is not a deterministic property of the code and varies by input.

## 6. Environment

- **Backend test execution:** Java 21, Maven, Testcontainers 2.0.5 (`testcontainers-postgresql` module). Each test run provisions its own disposable `postgres:16-alpine` container via `AbstractIntegrationTest`, with connection properties injected dynamically (`@DynamicPropertySource`) — no shared or persistent test database is used.
- **Frontend test execution:** Node.js, Vitest with the jsdom environment; browser APIs absent from jsdom (`ResizeObserver`, pointer capture, `URL.createObjectURL`, Web Workers) are polyfilled in `src/test/setup.ts`.
- **Manual campaign environment:** the full stack brought up via `docker-compose.yml` — a `postgres:16-alpine` container (bound to `127.0.0.1:5432`), the Spring Boot application container (port 8080, with an `/actuator/health` healthcheck), and an nginx-served frontend build (port 5173). Required secrets (`POSTGRES_PASSWORD`, `JWT_SECRET`, `ENCRYPTION_SECRET_KEY`, `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_PASSWORD`) are supplied via a local `.env` file, never committed to version control.

## 7. Known Limitations

- The OCR engine (Tesseract) may not perform at the expected accuracy on low-quality or handwritten input. This is not a software defect but an inherent limit of the engine; the campaign in Item 21 measures and documents this limit rather than eliminating it.
- Synthetic test documents (`SyntheticDocumentGenerator`) simulate handwriting using system handwriting fonts (Segoe Print / Segoe Script) with per-character baseline jitter, and simulate scan artifacts via vignette, row banding, blur, and noise filters. This is a deliberate approximation, not genuine handwriting; genuine human handwriting varies in ways a single font cannot reproduce. The small set of authentically scanned documents in the manual campaign partially closes this gap.
- Login and upload rate limiters are in-memory and per-instance; they are not validated under a multi-instance deployment (see §4).

## 8. Item Pass/Fail Criteria

**Entry criteria:** the relevant code change has been merged into `develop`, and the corresponding automated test(s) already exist and pass locally.

**Exit criteria:** the automated suite (70 backend + 16 frontend test cases) must pass at 100%. The manual accuracy campaign does not carry a zero-error-margin exit bar — a drop in OCR accuracy at lower quality levels is an expected and acceptable outcome, not a failing condition. The actual criterion is that the measured accuracy at each quality level is recorded in the Test Summary Report (Item 22).

## 9. Responsibilities

Single developer, internship context. All test roles — design, execution, and reporting — are carried out by the same individual; no separate QA function exists for this project.

## 10. Schedule

| Item | Description | Status |
|---|---|---|
| 19 | Synthetic test data generation | Complete |
| 20 | This document + Test Case Specification | In progress |
| 21 | Execution of the manual accuracy campaign | Pending |
| 22 | Completion of the Test Summary Report | Pending |