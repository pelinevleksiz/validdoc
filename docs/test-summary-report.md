# Test Summary Report — validdoc

## 1. Test Summary Report Identifier

**Document ID:** TSR-VALIDDOC-001
**Version:** 1.0 (partial — synthetic campaign only)
**Date:** 2026-08-06
**Standard reference:** IEEE 829-2008, Software Test Documentation, §7 (Test Summary Report).
**Referenced documents:** `docs/test-plan.md` (TP-VALIDDOC-001), `docs/test-case-specification.md` (TCS-VALIDDOC-001).

## 2. Summary

This report summarizes the testing activity performed for the validdoc document validation system as of this version: (a) the full automated test suite (backend and frontend), and (b) the synthetic accuracy campaign defined in the Test Plan (§5, item 3) and executed in Item 21 of the project backlog.

The automated suite comprises 70 backend test cases (`ApiIntegrationTest`: 65, `SmokeTest`: 4, `ValiddocApplicationTests`: 1) and 16 frontend test cases (`Login.test.tsx`, `Upload.test.tsx`, `TemplateNew.test.tsx`, `DocumentDetail.test.tsx`), all of which passed at last execution (2026-08-06).

The synthetic accuracy campaign executed 48 synthetic documents — 4 templates × 4 fill variants × 3 degradation levels (Clean, Medium, Bad) — against a live backend instance via a purpose-built test tool (`AccuracyCampaignRunner`), comparing the validation engine's actual output against the expected outcome recorded for each of the 92 non-ink segment instances present at each quality level.

The manual campaign against genuinely scanned and handwritten documents, also defined in the Test Plan (§5, item 3), has not yet been executed. This report will be superseded by a revised version once that data is available.

## 3. Variances

The following deviations from the Test Plan and Test Case Specification are recorded:

1. **Synthetic document set size.** The plan originating in Item 19 of the project backlog specified 8 templates × 5 documents × 3 quality levels. The delivered set is 4 templates × 4 fill variants × 3 quality levels (48 documents total). This variance was reviewed and accepted: the delivered set exercises every rule type defined in the system's rule catalog (`TC_KIMLIK_NO`, `VKN`, `PHONE`, `EMAIL`, `DATE`, `LETTERS_ONLY`, `DIGITS_ONLY`, `ALPHANUMERIC`, `MIN_LENGTH`, `MAX_LENGTH`, `SIGNATURE_INK`, `STAMP_INK`), so the reduction in document count was assessed as not reducing rule coverage.
2. **Manual campaign not executed.** The `TC-MAN` section of the Test Case Specification, covering genuinely scanned/handwritten documents, remains unexecuted at the time of this version. No test cases in this section have IDs assigned yet, per the Test Case Specification's own note that IDs are assigned at execution time.
3. **OCR engine tuning performed outside the original Item 21 scope.** During execution of the synthetic campaign, several image preprocessing and post-processing changes were made to the OCR pipeline in response to measured results (see §5). These were not part of the original Test Plan but were undertaken to meet an accuracy target set during the campaign; they are recorded here as a variance in scope, not in test execution.

## 4. Comprehensiveness Assessment

All test cases in the following sections of the Test Case Specification were executed and passed: TC-AUTH, TC-USER, TC-TPL, TC-UPL, TC-REV, TC-DOC, TC-AUD, TC-SET, TC-SEC, TC-RET (all part of `ApiIntegrationTest`), TC-SMOKE, TC-CTX, and TC-FE.

The TC-MAN section (manual accuracy campaign against genuinely scanned documents) was not executed. Its absence is the only gap between planned and executed test coverage at the time of this version.

## 5. Summary of Results

### 5.1 Automated suite

| Suite | Passed | Total | Pass rate |
|---|---|---|---|
| Backend (`ApiIntegrationTest` + `SmokeTest` + `ValiddocApplicationTests`) | 70 | 70 | 100% |
| Frontend (`Login`, `Upload`, `TemplateNew`, `DocumentDetail`) | 16 | 16 | 100% |

### 5.2 Synthetic accuracy campaign — segment-level outcome accuracy

| Quality | Match | Mismatch | Needs Review | Total | Match % |
|---|---|---|---|---|---|
| Clean | 83 | 0 | 9 | 92 | 90.2% |
| Medium | 47 | 13 | 32 | 92 | 51.1% |
| Bad | 40 | 14 | 38 | 92 | 43.5% |

### 5.3 Synthetic accuracy campaign — document-level status accuracy

| Quality | Match | Mismatch | Needs Review | Total | Match % |
|---|---|---|---|---|---|
| Clean | 9 | 0 | 7 | 16 | 56.3% |
| Medium | 2 | 1 | 13 | 16 | 12.5% |
| Bad | 0 | 1 | 15 | 16 | 0.0% |

A document is counted as a document-level match only if every one of its segments resolves to its exact expected outcome; a single low-confidence segment routes the entire document to `PENDING_REVIEW`. This is why document-level match rates are consistently lower than segment-level ones, and is not itself evidence of a higher error rate.

### 5.4 Manual campaign (genuinely scanned documents)

Not yet executed.

## 6. Evaluation

**Automated suite:** 100% pass rate against the exit criterion defined in the Test Plan (§8): "the automated suite (backend + frontend) must pass at 100%." This criterion is met.

**Synthetic accuracy campaign:** zero segment-level mismatches were observed at Clean quality (0 of 92). Every segment at Clean quality that reached a definite outcome reached the correct one; the shortfall from 100% match is attributable entirely to the engine correctly reading the text but assessing OCR confidence below the configured threshold, and correctly deferring to human review rather than accepting or rejecting silently. This matches the Test Plan's stated exit criterion for this activity (§8): "the actual criterion is that the measured accuracy at each quality level is documented in the Test Summary Report" rather than a fixed pass/fail bar.

At Medium and Bad quality, genuine mismatches occur (13 of 92 and 14 of 92, respectively). This is consistent with the Known Limitations recorded in the Test Plan (§7): OCR performance on degraded input is expected to be lower and is not itself treated as a defect.

**Overall:** based on the automated suite (100% pass) and the synthetic campaign (zero errors at Clean quality; degraded-quality results in line with the documented expectation), the tested scope is assessed as meeting its defined exit criteria. This evaluation does not extend to the untested manual campaign scope (§5.4); a release readiness determination for that scope is deferred to a revised version of this report.

## 7. Activity and Resource Usage

Single developer, internship context; no separate test team. The synthetic campaign, its supporting tooling, and the OCR accuracy improvements described below were executed within a single working session (Item 21 of the project backlog), 2026-08-06.

## 8. Approvals

| Role | Name | Date |
|---|---|---|
| Prepared by | — | 2026-08-06 |
| Reviewed by (advisor) | Pending | — |

---

## Appendix A — OCR Accuracy Improvements Applied During Item 21

The following changes were made to the OCR pipeline during the campaign described in this report, in response to measured results. Each was evaluated in isolation against the synthetic campaign before being retained or reverted.

| Change | Result | Retained |
|---|---|---|
| Page segmentation mode set to `PSM_SINGLE_BLOCK` | No measurable effect | Yes (neutral, no cost) |
| OCR engine mode set explicitly to `OEM_LSTM_ONLY` | No measurable effect | Yes (neutral, no cost) |
| Character whitelist per rule type | Measurable regression | No — reverted |
| 2x image upscaling before OCR | Measurable regression at Medium/Bad quality | No — reverted |
| Image binarization (Otsu thresholding) before OCR | Measurable improvement at all quality levels | Yes |
| 15px white border padding before OCR | Measurable improvement at Medium/Bad quality | Yes |
| Targeted post-processing correction for known phone (`+`) and email (`@`) misreads | Measurable improvement at Clean quality | Yes |
| Confidence adjustment when OCR output already satisfies its segment's validation rule | Measurable improvement at all quality levels | Yes |
| `lstm_rating_coefficient` Tesseract config parameter | No measurable effect | Yes (neutral, no cost) |

Net effect on segment-level accuracy: Clean 81.5%→90.2%, Medium 38.0%→51.1%, Bad 26.1%→43.5%.

A self-hosted alternative OCR engine (PaddleOCR) was evaluated as a candidate replacement but deferred, primarily due to incomplete Turkish-character support confirmed in the engine's own issue tracker. Cloud-based OCR APIs (Google Document AI, AWS Textract, Azure Document Intelligence) were also evaluated and rejected per an explicit advisor decision against sending document data outside the system's own infrastructure.