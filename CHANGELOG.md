# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Branching model (`main` / `develop` / `test` / `feature/*` / `release/*`), pull request template and this changelog.

### Changed

- Merged duplicated mobile/desktop session indicator and language switcher components; language switcher now reads from the shared i18next instance instead of separate local state, fixing a desktop/mobile language display mismatch.
- Extracted shared image-processing helpers (`ImageProcessingUtil`) used by both OCR processing and template preview.
- Unified `LoginRateLimiter` and `UploadRateLimiter` on the shared `RateLimiter` class.
- Centralized the `application/pdf` content-type constant in `FileSignatureValidator`.
- Extracted the duplicated nav-item visibility filter into a shared `useVisibleNavItems` hook.

### Fixed

- Operators could read, view segment images of, and resolve other operators' documents by guessing the ID; access is now scoped to the uploader (admins unaffected).
- Unmatched routes, wrong HTTP methods, missing parameters, and invalid path variable types now return proper 404/405/400 error codes instead of a generic 500.
- Template preview no longer crashes with a 500 when a segment is missing required coordinates; it now returns 400 with a clear message.

### Removed

- Unused error codes (`USER_HAS_LINKED_DOCUMENTS`, `INVALID_DOCUMENT_STATUS`) and their message keys.
- Unused repository methods (`TemplateRepository.findByName`, `UserRepository.countByRole`, `SegmentImageRepository.findByDocumentId(Long)`, unpaged `AuditLogRepository.findById`/`findAll`/`findByDocumentId(Long)`).
- Unused `OcrService.tesseractFactory` field and always-false `.disabled()` check in `CustomUserDetailsService`.
- Empty Maven Initializr placeholder tags in `pom.xml`.
- Stale `email` field from test payloads (`CreateUserRequest` has no such field).
- Unused frontend assets (`sheet.tsx`, `App.css`, `hero.png`, `react.svg`, `icons.svg`), dead dark-mode CSS block, unused sidebar/chart CSS variables, and 9 unused i18n keys.