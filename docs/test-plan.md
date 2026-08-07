# Test Plan — validdoc

## 1. Test Plan Identifier

**Document ID:** TP-VALIDDOC-001
**Version:** 1.0
**Date:** 2026-08-06
**Standard reference:** IEEE 829-2008, Software Test Documentation, §5 (Test Plan). Note: this standard was superseded by ISO/IEC/IEEE 29119-3 in 2013; the structure here follows IEEE 829 per the academic advisor's requirement.

## 2. Introduction

This document defines the test scope, approach, resources, schedule, and acceptance criteria for the validdoc document validation system, a Spring Boot / React application that validates uploaded documents against admin-defined templates using OCR and rule-based checks.

## 3. Test Items

- Backend REST API — Spring Boot 4.1.0, Java 21
- Frontend single-page application — React 19, TypeScript
- Database schema and migrations — PostgreSQL 16, Flyway
- OCR / image processing engine — Tesseract (via Tess4J 5.12.0), OpenCV (via the `org.openpnp:opencv` binding, version 4.9.0-0)

## 4. Features to Be Tested

- Authentication, authorization, and role-based access control
- User management (creation, deactivation, password change, admin-initiated password reset)
- Template creation, segment/rule definition, preview
- Document upload and the automatic validation engine (OCR + rule matching, including TC Kimlik No / VKN checksum validation and the international phone rule)
- Review queue, segment resolution, manual override
- Audit log accuracy, including target-user attribution
- Retention period automatic cleanup (segment result anonymization and segment image purge)
- Framework-level exception handling (unmatched routes, wrong HTTP methods, missing parameters, type mismatches)
- Frontend component behavior (form validation, error display, conditional visibility by role)
- OCR engine accuracy under varying document quality

## 5. Features Not to Be Tested

- Load/performance testing (out of scope — the system is designed for small-team internal use)
- Multi-instance/horizontal scaling behavior (the login and upload rate limiters are held in memory per instance; this is documented in the SRS as an accepted constraint, not validated under multi-instance load)
- Independent third-party penetration testing (known vulnerability classes — IDOR, missing framework-level exception handling — were identified and closed via code review during the project's release backlog; no external penetration test was commissioned)

## 6. Approach

A three-layer strategy is followed:

1. **Automated integration tests (backend)** — 70 test cases (`ApiIntegrationTest`: 65, `SmokeTest`: 4, `ValiddocApplicationTests`: 1), run with JUnit 5 against a real Spring Boot application context (`@SpringBootTest`) and an isolated Testcontainers-managed PostgreSQL 16 (`postgres:16-alpine`) instance, exercising the system end-to-end over HTTP via `MockMvc`.
2. **Automated component tests (frontend)** — 16 test cases across 4 files (`Login.test.tsx`, `Upload.test.tsx`, `TemplateNew.test.tsx`, `DocumentDetail.test.tsx`), using Vitest with jsdom and Testing Library, simulating real DOM interaction with the backend API mocked at the module boundary.
3. **Manual accuracy campaign** — using a large set of synthetically generated templates and documents (`SyntheticDocumentGenerator`, producing 4 templates × 4 fill variants × 3 degradation levels) plus a small number of genuinely scanned documents, to measure the OCR engine's real-world accuracy under varying document quality. This is an area the automated suite deliberately does not cover, since OCR accuracy is not a deterministic property of the code and varies by input.

Pass/fail determination for automated tests is made by the test runner (JUnit / Vitest) based on assertion outcomes. Pass/fail determination for the manual accuracy campaign is made by comparing the engine's actual output against the expected outcome recorded in the campaign manifest, using a purpose-built comparison tool (`AccuracyCampaignRunner`).

## 7. Item Pass/Fail Criteria

**Entry:** the relevant code change has been merged into `develop`, and the corresponding automated test(s) already exist and pass locally.

**Exit:** the automated suite (70 backend + 16 frontend test cases) must pass at 100%. The manual accuracy campaign does not carry a zero-error-margin exit bar — a drop in OCR accuracy at lower quality levels is an expected and acceptable outcome, not a failing condition. The actual criterion is that the measured accuracy at each quality level is recorded in the Test Summary Report.

## 8. Suspension Criteria and Resumption Requirements

Testing is suspended if:
- The backend fails to start (Spring context load failure), since no test layer can execute against a non-running application.
- The isolated Testcontainers PostgreSQL instance fails to provision, since the automated backend suite depends on it exclusively (no shared or persistent test database is used).
- For the manual accuracy campaign specifically: if the live backend (via `docker compose up`) is not in a healthy state (per its `/actuator/health` check), the campaign is suspended until health is restored.

Testing resumes once the blocking condition is resolved and verified (successful context load, healthy database container, or healthy application container, as applicable). No partial results from a suspended run are treated as final; the affected run is repeated in full.

## 9. Test Deliverables

- This Test Plan (`docs/test-plan.md`)
- Test Case Specification (`docs/test-case-specification.md`)
- Test Summary Report (`docs/test-summary-report.md`)
- Automated test source code (`ApiIntegrationTest.java`, `SmokeTest.java`, `ValiddocApplicationTests.java`, and the frontend `*.test.tsx` files), which also serves as the executable record of test cases
- Synthetic test data generator and its output (`SyntheticDocumentGenerator`, `target/synthetic-documents/manifest.csv`)
- Accuracy campaign tooling and its output (`AccuracyCampaignRunner`, `target/campaign-results/campaign-results.csv`, `target/campaign-results/campaign-summary.md`)

## 10. Testing Tasks

1. Design and implement automated test cases alongside each feature (ongoing, one per release backlog item).
2. Generate synthetic test documents covering the full rule catalog at multiple quality levels.
3. Execute the synthetic accuracy campaign against a live backend instance and record results.
4. Prepare a small set of genuinely scanned/handwritten documents and execute the manual campaign against them.
5. Compile results into the Test Summary Report.
6. Review and, where the measured accuracy falls short of expectations, iterate on the OCR pipeline (image preprocessing, post-processing, or engine configuration) and re-measure before finalizing results.

No task dependencies exist between the automated suite and the accuracy campaign; they may be executed independently and in either order. The manual campaign (task 4) depends on the synthetic campaign (task 3) only in that both use the same comparison tooling.

## 11. Environmental Needs

- **Backend test execution:** Java 21, Maven, Testcontainers 2.0.5 (`testcontainers-postgresql` module). Each test run provisions its own disposable `postgres:16-alpine` container via `AbstractIntegrationTest`, with connection properties injected dynamically (`@DynamicPropertySource`) — no shared or persistent test database is used.
- **Frontend test execution:** Node.js, Vitest with the jsdom environment; browser APIs absent from jsdom (`ResizeObserver`, pointer capture, `URL.createObjectURL`, Web Workers) are polyfilled in `src/test/setup.ts`.
- **Manual and synthetic campaign environment:** the full stack brought up via `docker-compose.yml` — a `postgres:16-alpine` container (bound to `127.0.0.1:5432`), the Spring Boot application container (port 8080, with an `/actuator/health` healthcheck), and an nginx-served frontend build (port 5173). Required secrets (`POSTGRES_PASSWORD`, `JWT_SECRET`, `ENCRYPTION_SECRET_KEY`, `BOOTSTRAP_ADMIN_USERNAME`, `BOOTSTRAP_ADMIN_PASSWORD`) are supplied via a local `.env` file, never committed to version control.

## 12. Responsibilities

Single developer (internship context); all test roles — design, execution, and reporting — are carried out by the same individual. Advisor review and approval of test deliverables occurs at defined checkpoints (see Test Summary Report, §8, Approvals).

## 13. Staffing and Training Needs

No additional staffing is required beyond the single developer. No specialized training is required; the tools used (JUnit 5, Vitest, Testcontainers, Docker Compose) are already part of the developer's existing skill set as demonstrated throughout the project's release backlog.

## 14. Schedule

| Item | Description | Status |
|---|---|---|
| 19 | Synthetic test data generation | Complete |
| 20 | This document + Test Case Specification | Complete |
| 21 | Execution of the manual accuracy campaign (synthetic portion) | Complete |
| 21 (cont.) | Execution of the manual accuracy campaign (genuine scanned documents) | Pending |
| 22 | Completion of the Test Summary Report | In progress (partial — synthetic results only) |

## 15. Risks and Contingencies

| Risk | Contingency |
|---|---|
| OCR accuracy on genuinely scanned/handwritten documents differs materially from the synthetic campaign's results | The synthetic campaign is explicitly scoped as a volume/coverage measure, not a substitute for real-world validation (see `SyntheticDocumentGenerator`'s own limitation note); the manual campaign against real documents is planned specifically to surface this gap before release |
| Single-developer staffing means no independent test review during execution | Advisor review is sought at defined checkpoints (Test Summary Report approval) as a substitute for peer review |
| Rate limiting (login/upload) interferes with high-volume automated campaign execution | `AccuracyCampaignRunner` implements automatic retry with backoff on HTTP 429 responses |

## 16. Approvals

| Role | Name | Date |
|---|---|---|
| Prepared by | — | 2026-08-06 |
| Reviewed by (advisor) | Pending | — |