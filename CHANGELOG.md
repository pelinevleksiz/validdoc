# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Branching model (`main` / `develop` / `test` / `feature/*` / `release/*`), pull request template and this changelog.
- Audit logs now record the target user for actions like deactivation.
- Document detail page now shows each segment's cropped image (thumbnail in the list, full view in a side panel on click), colored consistently with the outcome badges used elsewhere.

### Changed

- Merged duplicated mobile/desktop session indicator and language switcher components; language switcher now reads from the shared i18next instance instead of separate local state, fixing a desktop/mobile language display mismatch.
- Extracted shared image-processing helpers (`ImageProcessingUtil`) used by both OCR processing and template preview.
- Unified `LoginRateLimiter` and `UploadRateLimiter` on the shared `RateLimiter` class.
- Centralized the `application/pdf` content-type constant in `FileSignatureValidator`.
- Extracted the duplicated nav-item visibility filter into a shared `useVisibleNavItems` hook.
- `template_segments.label` column narrowed to VARCHAR(30) to match the already-enforced DTO validation limit.
- Phone validation rule (`PHONE_TR` → `PHONE`) now accepts international numbers: separators are stripped and 7-15 digits with an optional leading `+` are required (ITU-T E.164), instead of matching only Turkish mobile format.
- `TC_KIMLIK_NO` and `VKN` rules now validate the actual MERNİS/VKN checksum algorithms instead of only checking digit count.
- All segment crops (including signature/stamp, which previously had none) are now persisted with the automatic decision, not deleted on resolve; text-field crops are compressed to grayscale JPEG, signature/stamp crops keep color. The image endpoint now serves `image/jpeg` instead of `image/png`.

### Fixed

- Operators could read, view segment images of, and resolve other operators' documents by guessing the ID; access is now scoped to the uploader (admins unaffected).
- Unmatched routes, wrong HTTP methods, missing parameters, and invalid path variable types now return proper 404/405/400 error codes instead of a generic 500.
- Template preview no longer crashes with a 500 when a segment is missing required coordinates; it now returns 400 with a clear message.
- Restored dashboard shortcut card descriptions, `SegmentImageRepository.findByDocumentId`, and the `Sheet` UI component, mistakenly removed during dead-code cleanup despite being needed (the first was in active use via a dynamic i18n key my static analysis missed; the other two are needed by upcoming work).
- `segment_images` now has proper foreign keys to `document_metadata` and `template_segments`.
- Template and username uniqueness conflicts now return distinct `TEMPLATE_NAME_TAKEN`/`USERNAME_TAKEN` codes instead of a generic `DUPLICATE_RECORD`.
- `JwtService`'s misleading 1-hour fallback default (actual configured value: 10 minutes) removed; the app now fails fast if `jwt.expiration-ms` is missing.
- Templates could be saved with a segment referencing a page number beyond the template's declared page count, causing every upload against that template to fail; this is now rejected at template creation with a clear error.
- Retention purge now deletes segment crops along with the segment results (previously only the results were cleared, leaving crops behind indefinitely).
- The segment image endpoint now sends `Cache-Control: no-store` so browsers don't retain a local copy.

### Removed

- Unused error codes (`USER_HAS_LINKED_DOCUMENTS`, `INVALID_DOCUMENT_STATUS`) and their message keys.
- Unused repository methods (`TemplateRepository.findByName`, `UserRepository.countByRole`, `SegmentImageRepository.findByDocumentId(Long)`, unpaged `AuditLogRepository.findById`/`findAll`/`findByDocumentId(Long)`).
- Unused `OcrService.tesseractFactory` field and always-false `.disabled()` check in `CustomUserDetailsService`.
- Empty Maven Initializr placeholder tags in `pom.xml`.
- Stale `email` field from test payloads (`CreateUserRequest` has no such field).
- Unused frontend assets (`sheet.tsx`, `App.css`, `hero.png`, `react.svg`, `icons.svg`), dead dark-mode CSS block, unused sidebar/chart CSS variables, and 9 unused i18n keys.