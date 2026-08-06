# Test Case Specification — validdoc

**Standard reference:** IEEE 829-2008. Each test case carries a unique ID; the ID prefix denotes the functional area (see §0, ID Scheme). This document is the test-side reference for the Requirements Traceability Matrix to be built in Item 25.

**Verification note:** every entry below was transcribed directly from the current source files (`ApiIntegrationTest.java`, `SmokeTest.java`, `ValiddocApplicationTests.java`, and the four frontend `*.test.tsx` files), read in full for this revision — no test name, precondition, or expected result was written from memory.

## 0. ID Scheme

| Prefix | Area |
|---|---|
| TC-AUTH | Authentication |
| TC-USER | User management |
| TC-TPL | Template / segment / rule |
| TC-UPL | Document upload and validation engine |
| TC-REV | Review, resolution, manual override |
| TC-DOC | Document listing / access |
| TC-AUD | Audit log |
| TC-SET | Validation settings |
| TC-SEC | Framework / security level |
| TC-RET | Retention automation |
| TC-FE | Frontend component tests |
| TC-SMOKE | Application smoke tests |
| TC-CTX | Application context test |
| TC-MAN | Manual accuracy campaign (Item 21) |

**Source files:**
- Backend: `src/test/java/com/validdoc/ApiIntegrationTest.java` (65 cases, `@Order(1)`–`@Order(66)`, order 26 intentionally unused), `SmokeTest.java` (4 cases), `ValiddocApplicationTests.java` (1 case)
- Frontend: `frontend/src/pages/Login.test.tsx`, `Upload.test.tsx`, `TemplateNew.test.tsx`, `DocumentDetail.test.tsx`

---

## 1. Authentication (TC-AUTH)

| ID | Test method | Precondition | Expected result |
|---|---|---|---|
| TC-AUTH-001 | `setupAdminUserAndLogIn` | A new admin user has been persisted directly via the repository | `POST /api/auth/login` with correct credentials returns 200 with a non-null token |
| TC-AUTH-002 | `loginRateLimiterBlocksAfterFiveAttempts` | — | After 4 failed login attempts from the same remote address, the 5th attempt returns 429 + `TOO_MANY_LOGIN_ATTEMPTS` |
| TC-AUTH-003 | `changingOwnPasswordWithWrongCurrentPasswordFails` | Admin is authenticated | `PUT /api/users/me/password` with the wrong current password returns 401 + `BAD_CREDENTIALS` |
| TC-AUTH-004 | `changingOwnPasswordSucceedsAndOldPasswordNoLongerWorks` | Admin is authenticated | Password change returns 204; login with the old password returns 401; login with the new password returns 200 with a token |

## 2. User Management (TC-USER)

| ID | Test method | Precondition | Expected result |
|---|---|---|---|
| TC-USER-001 | `adminCanCreateOperatorUser` | Admin is authenticated | `POST /api/users` returns 201 with `role: OPERATOR` |
| TC-USER-002 | `creatingDuplicateUsernameFails` | The username already exists (active) | Returns 409 + `USERNAME_TAKEN` |
| TC-USER-003 | `operatorCanLogInAndCannotCreateUsers` | Operator credentials exist | Operator login succeeds; the operator's own `POST /api/users` returns 403 |
| TC-USER-004 | `userListIsPaginatedForAdmin` | Admin is authenticated | `GET /api/users?page=0&size=5` returns a paginated array with `page`, `size`, and `totalElements` |
| TC-USER-005 | `userListIsForbiddenForOperator` | Operator is authenticated | `GET /api/users` returns 403 |
| TC-USER-006 | `adminCanDeleteUserWithoutLinkedDocuments` | A newly created user with no documents | `DELETE /api/users/{id}` returns 204; a subsequent login with the deactivated user's credentials returns 401 |
| TC-USER-007 | `adminCanDeactivateUserWithLinkedDocuments` | The primary operator, who owns uploaded documents | `DELETE /api/users/{id}` returns 204 (no conflict from linked documents); login afterward returns 401 |
| TC-USER-008 | `cannotDeleteLastRemainingAdmin` (`@Transactional`) | All admins except the current one are deactivated in-test, leaving exactly one active admin | `DELETE /api/users/{id}` on the last admin returns 409 + `CANNOT_DELETE_LAST_ADMIN` |
| TC-USER-009 | `adminCanResetUserPasswordWithOwnPasswordConfirmation` | A target user exists | `PUT /api/users/{id}/password` with the wrong admin password returns 401 + `BAD_CREDENTIALS`; with the correct one returns 204; the target's old password is subsequently rejected, the new one accepted |

## 3. Template / Segment / Rule (TC-TPL)

| ID | Test method | Precondition | Expected result |
|---|---|---|---|
| TC-TPL-001 | `ruleCatalogListsAllRuleTypes` | Admin is authenticated | `GET /api/templates/rule-types` includes an entry with `type: SIGNATURE_INK` and `inkRule: true`, and an entry with `type: MIN_LENGTH` and `requiresParam: true` |
| TC-TPL-002 | `adminCanCreateValidTemplate` | Admin is authenticated | `POST /api/templates` with one valid `LETTERS_ONLY` segment returns 201 with an `id` |
| TC-TPL-003 | `templateDetailReturnsSegmentAndRule` | A template has been created (TC-TPL-002) | `GET /api/templates/{id}` returns the correct name, segment label, and rule type |
| TC-TPL-004 | `templateListIsPaginated` | Admin is authenticated | `GET /api/templates?page=0&size=5` returns the correct pagination metadata |
| TC-TPL-005 | `segmentOutsideA4BoundsIsRejected` | — | A segment at `x=2000, y=3000` on an A4 page returns 400 + `INVALID_SEGMENT_COORDINATES` |
| TC-TPL-006 | `combiningInkRuleWithOtherRuleIsRejected` | — | A segment with both `SIGNATURE_INK` and `LETTERS_ONLY` rules returns 400 + `INVALID_SEGMENT_RULE_COMBINATION` |
| TC-TPL-007 | `minLengthRuleWithoutParamIsRejected` | — | A `MIN_LENGTH` rule with no `param` returns 400 + `INVALID_RULE_PARAM` |
| TC-TPL-008 | `adminCanCreateInkOnlyTemplate` | Admin is authenticated | A template with a single `SIGNATURE_INK` segment returns 201 |
| TC-TPL-009 | `templatePreviewReturnsInkDensityWithoutPersisting` | Admin is authenticated | `POST /api/templates/preview` with an inked image returns `inkDensity` and the correct segment label; no template is persisted |
| TC-TPL-010 | `pdfMultiPageDocumentIsRasterizedFromCorrectPage` | A 2-page template with a segment on page 2 | A blank 2-page PDF resolves to `REJECTED_EMPTY`, confirming page 2 (not page 1) was rasterized and evaluated |
| TC-TPL-011 | `previewRejectsSegmentWithMissingCoordinatesInsteadOfCrashing` | — | A preview segment with `x: null` returns 400 + `PREVIEW_FAILED` (not a 500) |

## 4. Document Upload and Validation Engine (TC-UPL)

| ID | Test method | Precondition | Expected result |
|---|---|---|---|
| TC-UPL-001 | `uploadWithoutTemplateIdIsRejected` | — | Upload without a `templateId` parameter returns 400 + `TEMPLATE_ID_REQUIRED` |
| TC-UPL-002 | `uploadWithGarbageBytesIsRejectedAsUnsupportedType` | — | A file whose declared type is `image/png` but whose content is plain text returns 400 + `UNSUPPORTED_FILE_TYPE` |
| TC-UPL-003 | `uploadWithValidPngIsAcceptedAndQueuedForProcessing` | — | A valid PNG upload returns 202 with `status: PROCESSING` and an `id` |
| TC-UPL-004 | `signedImageIsValidatedEndToEnd` | An ink-only template (TC-TPL-008) | An inked image reaches `VALIDATED`; the document's `segmentResults` field is populated |
| TC-UPL-005 | `blankImageIsRejectedAsEmptyEndToEnd` | An ink-only template | A blank image reaches `REJECTED_EMPTY` |
| TC-UPL-006 | `pageMismatchRoutesToPendingReview` | A template seeded directly via the repository with a segment on a page beyond `pageCount` (bypassing the Item 11 API-level guard, to exercise the engine's own defense) | A single-page upload against this template reaches `PENDING_REVIEW` |
| TC-UPL-007 | `pdfSinglePageSignedDocumentIsValidatedEndToEnd` | An ink-only template | An inked single-page PDF reaches `VALIDATED` |
| TC-UPL-008 | `pdfSinglePageBlankDocumentIsRejectedAsEmptyEndToEnd` | An ink-only template | A blank single-page PDF reaches `REJECTED_EMPTY` |
| TC-UPL-009 | `pdfWithFewerPagesThanTemplateRequiresRoutesToPendingReview` | The inconsistent multi-page template from TC-UPL-006 | A 1-page PDF against a template expecting page 2 reaches `PENDING_REVIEW` |
| TC-UPL-010 | `multipleFilesUploadedInSuccessionAreProcessedIndependently` | An ink-only template | One signed and one blank file, uploaded sequentially, resolve independently to `VALIDATED` and `REJECTED_EMPTY` respectively |
| TC-UPL-011 | `uploadWithMismatchedPageCountIsRejectedBeforeProcessing` | An ink-only (1-page) template | A 3-page PDF is rejected with 400 + `PAGE_COUNT_MISMATCH` before any processing begins |
| TC-UPL-012 | `tcKimlikNoChecksumValidatesRealAlgorithm` | — | `ValidationService.validate()` called directly with a `TC_KIMLIK_NO` rule: a real, checksum-valid number (`10562272296`) yields `FILLED_VALID`; an invalid one (`11111111111`) and a leading-zero number (`01562272296`) both yield `FILLED_INVALID` |
| TC-UPL-013 | `vknChecksumValidatesRealAlgorithm` | — | Called directly with a `VKN` rule: a checksum-valid number (`1234567890`) yields `FILLED_VALID`; an invalid one (`1234567891`) yields `FILLED_INVALID` |
| TC-UPL-014 | `phoneRuleAcceptsInternationalNumbersAndStripsSeparators` | — | Called directly with a `PHONE` rule: `+905321234567` and `0532 123 45 67` (separators stripped) both yield `FILLED_VALID`; `123` (too short) and a 16-digit number (too long) both yield `FILLED_INVALID` |
| TC-UPL-015 | `inkSegmentImageIsPersistedAfterValidation` | A validated signed document (TC-UPL-004) | `GET /api/documents/{id}/segments/{segmentId}/image` for the signature segment returns 200 with a content type compatible with `image/jpeg` |

## 5. Review, Resolution, Manual Override (TC-REV)

| ID | Test method | Precondition | Expected result |
|---|---|---|---|
| TC-REV-001 | `reviewQueueContainsPendingReviewDocument` | A document is in `PENDING_REVIEW` (TC-UPL-006) | `GET /api/documents/queue` lists it |
| TC-REV-002 | `resolveTestTemplateAndDocumentAreSeededAsPendingReview` | — | A 2-segment template and document are created, uploaded, and then forced into `PENDING_REVIEW` with both segments pending (direct repository write, bypassing OCR); `GET` on the document confirms `status: PENDING_REVIEW` |
| TC-REV-003 | `segmentImageIsAvailableWhilePending` | TC-REV-002 | The pending segment's image returns 200 with content type starting `image/jpeg` and a non-empty body |
| TC-REV-004 | `operatorCanResolveOneOfTwoPendingSegmentsAndDocumentStaysPendingReview` | TC-REV-002 | Resolving segment A as `FILLED_VALID` returns 200; the document's status remains `PENDING_REVIEW` (segment B is still pending) |
| TC-REV-005 | `resolvingAlreadyResolvedSegmentIsRejected` | TC-REV-004 | Resolving segment A again returns 409 + `SEGMENT_ALREADY_RESOLVED` |
| TC-REV-006 | `resolvingSegmentWithPendingReviewOutcomeIsRejected` | TC-REV-002 | Resolving segment B with outcome `PENDING_REVIEW` returns 400 + `INVALID_SEGMENT_RESOLUTION_OUTCOME` |
| TC-REV-007 | `resolvingLastPendingSegmentRecomputesDocumentStatus` | TC-REV-004 | Resolving segment B as `FILLED_INVALID` returns 200 with `status: REJECTED_INVALID` (recomputed from both segment outcomes) |
| TC-REV-008 | `segmentImageRemainsAvailableAfterResolve` | TC-REV-007 | Segment B's image still returns 200 with content type `image/jpeg` and a non-empty body (not deleted on resolve) |
| TC-REV-009 | `resolvingSegmentOnDocumentNotInPendingReviewIsRejected` | The document is now in a terminal status (TC-REV-007) | Resolving segment A returns 409 + `DOCUMENT_NOT_PENDING_REVIEW` |
| TC-REV-010 | `engineFailurePendingReviewDocumentCannotHaveSegmentsResolved` | A document pending due to a page-mismatch engine failure (using the template from TC-UPL-006) | Resolving its segment returns 409 + `DOCUMENT_NOT_PENDING_REVIEW` |
| TC-REV-011 | `adminCanOverrideResolvedSegmentAndDocumentStatusRecomputes` | Segment B is already resolved (TC-REV-007) | `POST .../override` with outcome `FILLED_VALID` and `reasonCode: OCR_MISREAD` returns 200 with `status: VALIDATED` (recomputed); the audit log contains a `SEGMENT_OVERRIDDEN` entry |
| TC-REV-012 | `operatorCannotOverrideSegment` | A freshly created operator (not the deactivated primary one) | The override request returns 403 |
| TC-REV-013 | `overridingPendingSegmentIsRejected` | A fresh template/document seeded directly into `PENDING_REVIEW` for this test | The override request returns 409 + `SEGMENT_NOT_YET_RESOLVED` |
| TC-REV-014 | `overrideWithOtherReasonRequiresNote` | Segment A is resolved | `reasonCode: OTHER` with no `note` returns 400 + `OVERRIDE_NOTE_REQUIRED`; with a note, it returns 200 |
| TC-REV-015 | `overrideWithSameOutcomeIsRejected` | Segment A's current outcome is `FILLED_INVALID` (from TC-REV-014) | An override to the same outcome (`FILLED_INVALID`) returns 400 + `OVERRIDE_OUTCOME_UNCHANGED` |

## 6. Document Listing and Access (TC-DOC)

| ID | Test method | Precondition | Expected result |
|---|---|---|---|
| TC-DOC-001 | `documentListIsPaginatedAndContainsUploads` | A document has been uploaded (TC-UPL-004) | `GET /api/documents` (admin) includes it and returns correct pagination metadata |
| TC-DOC-002 | `operatorDocumentListIsScopedToOwnUploads` | A second operator with no uploads | The second operator's document list returns `totalElements: 0`; the admin's list still includes the first operator's document |
| TC-DOC-003 | `operatorAccessAndReviewQueueAreScopedToOwnUploads` | Owner, outsider, and admin accounts; a document seeded into `PENDING_REVIEW` owned by the owner | The outsider's `GET`, image request, and resolve request all return 404 + `DOCUMENT_NOT_FOUND`; the outsider's queue returns `totalElements: 0` and stats return `pendingReview: 0`; the owner and admin can access the document, its image, and see it in their own queue/stats |

## 7. Audit Log (TC-AUD)

| ID | Test method | Precondition | Expected result |
|---|---|---|---|
| TC-AUD-001 | `auditLogContainsRecentActions` | Validation settings were just updated (TC-SET-001) | `GET /api/admin/audit-logs` contains a `VALIDATION_SETTINGS_UPDATED` entry |
| TC-AUD-002 | `auditLogsAreForbiddenForOperator` | Operator is authenticated | `GET /api/admin/audit-logs` returns 403 |
| TC-AUD-003 | `auditLogRecordsCorrectTargetUserIdOnDeactivation` | A target user is created and then deactivated | The corresponding `USER_DEACTIVATED` audit entry carries the correct `targetUserId` |

## 8. Validation Settings (TC-SET)

| ID | Test method | Precondition | Expected result |
|---|---|---|---|
| TC-SET-001 | `validationSettingsCanBeReadAndUpdatedByAdmin` | Admin is authenticated | `GET` returns a `retentionDays` field; `PUT` with new values returns 200 with the updated `retentionDays` |
| TC-SET-002 | `validationSettingsAreForbiddenForOperator` | Operator is authenticated | `GET /api/admin/validation-settings` returns 403 |

## 9. Framework / Security Level (TC-SEC)

| ID | Test method | Precondition | Expected result |
|---|---|---|---|
| TC-SEC-001 | `frameworkExceptionsMapToProperErrorCodes` | Admin is authenticated | An unmatched route returns 404 + `RESOURCE_NOT_FOUND`; `DELETE /api/auth/login` returns 405 + `METHOD_NOT_ALLOWED`; a non-numeric document ID returns 400 + `INVALID_PARAMETER_TYPE`; a preview request missing the `segments` parameter returns 400 + `MISSING_REQUEST_PARAMETER` |

## 10. Retention Automation (TC-RET)

| ID | Test method | Precondition | Expected result |
|---|---|---|---|
| TC-RET-001 | `abandonedPendingReviewDocumentAutoExpiresToRejectedInvalid` | A document in `PENDING_REVIEW` with `processedAt` set 5 years in the past | After `retentionCleanupJob.expireAbandonedReviews()`, the document's status is `REJECTED_INVALID` and `purgeAt` is set |
| TC-RET-002 | `purgeJobAnonymizesSegmentResultsPastRetention` | A `VALIDATED` document with `purgeAt` in the past, and an associated segment image | After `retentionCleanupJob.purgeExpiredSegmentResults()`, `segmentResults` is null and the segment image is deleted |

## 11. Frontend Component Tests (TC-FE)

| ID | Test file / name | Expected result |
|---|---|---|
| TC-FE-001 | `Login.test.tsx` — "shows the username and password fields" | The username field, password field, and submit button render |
| TC-FE-002 | `Login.test.tsx` — "shows validation errors when submitting an empty form" | Client-side validation messages appear; `api.post` is never called |
| TC-FE-003 | `Login.test.tsx` — "saves the token and redirects to the dashboard on successful login" | `navigate` is called with `/dashboard`; `api.post` is called with the correct credentials |
| TC-FE-004 | `Login.test.tsx` — "shows the server error message on invalid credentials" | The backend's own `message` field ("Kullanıcı adı veya şifre hatalı.") is rendered directly; `navigate` is not called |
| TC-FE-005 | `Upload.test.tsx` — "shows the document upload area initially" | The dropzone text and file-select button render |
| TC-FE-006 | `Upload.test.tsx` — "shows an error for an unsupported file type and does not advance the step" | An unsupported-type error message appears; the file-select button remains (step does not advance) |
| TC-FE-007 | `Upload.test.tsx` — "moves to the template selection step when a PNG is selected" | The "selected files" heading and the file name appear |
| TC-FE-008 | `Upload.test.tsx` — "shows an error and does not add the file if the PDF cannot be read" | An "okunamadı" (could not be read) message appears; the "selected files" heading does not |
| TC-FE-009 | `TemplateNew.test.tsx` — "shows the template name field and the drop area initially" | The template name field and "upload example document" text render |
| TC-FE-010 | `TemplateNew.test.tsx` — "the template name can be typed" | Typing into the name field updates its value |
| TC-FE-011 | `TemplateNew.test.tsx` — "the save button stays disabled with no segments added" | The "save template" button is disabled |
| TC-FE-012 | `DocumentDetail.test.tsx` — "shows the segment list with thumbnails" | Both segment labels render (as an operator) |
| TC-FE-013 | `DocumentDetail.test.tsx` — "does not show the override button to an operator" | After opening a segment's detail panel, no "override" text appears |
| TC-FE-014 | `DocumentDetail.test.tsx` — "shows the override button to an admin on a resolved segment" | As an admin, the "override" text appears in the detail panel |
| TC-FE-015 | `DocumentDetail.test.tsx` — "shows a placeholder when the image fails to load" | When the image request rejects, both segments show a "no image" placeholder |
| TC-FE-016 | `DocumentDetail.test.tsx` — "an admin can complete the override flow end to end" | After confirming, selecting a new outcome, and saving, `api.post` is called with `/api/documents/42/segments/1/override` and `reasonCode: OCR_MISREAD` |

## 12. Application Smoke Tests (TC-SMOKE)

| ID | Test method | Precondition | Expected result |
|---|---|---|---|
| TC-SMOKE-001 | `healthEndpointIsPubliclyAccessible` | — | `GET /actuator/health` returns 200 and its body contains "UP"; no authentication is required |
| TC-SMOKE-002 | `protectedEndpointRejectsUnauthenticatedRequest` | — | `GET /api/templates` without a token returns 401 |
| TC-SMOKE-003 | `loginWithInvalidCredentialsReturnsLocalizedError` | — | Login with a non-existent username returns 401 with a populated `code` field |
| TC-SMOKE-004 | `uploadEndpointRejectsUnauthenticatedRequest` | — | `POST /api/documents/upload` without a token returns 401 |

## 13. Application Context (TC-CTX)

| ID | Test method | Precondition | Expected result |
|---|---|---|---|
| TC-CTX-001 | `contextLoads` | — | The full Spring application context starts without error |

## 14. Manual Accuracy Campaign (TC-MAN)

To be completed in Item 21, using `SyntheticDocumentGenerator` output and genuinely scanned documents. This section is currently empty; each campaign run will receive an ID in the format `TC-MAN-<template>-<variant>-<quality>`.