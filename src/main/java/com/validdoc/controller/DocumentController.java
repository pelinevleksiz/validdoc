package com.validdoc.controller;

import com.validdoc.dto.request.SegmentOverrideRequest;
import com.validdoc.dto.response.DocumentStatsResponse;
import com.validdoc.dto.response.DocumentSummaryResponse;
import com.validdoc.dto.response.PagedResponse;
import com.validdoc.exception.ApiException;
import com.validdoc.exception.ErrorCode;
import com.validdoc.model.AuditLog;
import com.validdoc.model.DocumentMetadata;
import com.validdoc.model.SegmentImage;
import com.validdoc.model.Template;
import com.validdoc.model.User;
import com.validdoc.model.enums.DocumentLanguage;
import com.validdoc.model.enums.DocumentStatus;
import com.validdoc.model.enums.UserRole;
import com.validdoc.repository.AuditLogRepository;
import com.validdoc.repository.DocumentRepository;
import com.validdoc.repository.SegmentImageRepository;
import com.validdoc.repository.TemplateRepository;
import com.validdoc.repository.UserRepository;
import com.validdoc.security.UploadRateLimiter;
import com.validdoc.service.DocumentService;
import com.validdoc.service.FileSignatureValidator;
import jakarta.validation.Valid;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.validdoc.dto.request.SegmentResolveRequest;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final TemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final SegmentImageRepository segmentImageRepository;
    private final DocumentService documentService;
    private final UploadRateLimiter uploadRateLimiter;

    public DocumentController(DocumentRepository documentRepository,
                              TemplateRepository templateRepository,
                              UserRepository userRepository,
                              AuditLogRepository auditLogRepository,
                              SegmentImageRepository segmentImageRepository,
                              DocumentService documentService,
                              UploadRateLimiter uploadRateLimiter) {
        this.documentRepository = documentRepository;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.segmentImageRepository = segmentImageRepository;
        this.documentService = documentService;
        this.uploadRateLimiter = uploadRateLimiter;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "templateId", required = false) Long templateId,
                                                      @RequestParam(value = "lang", required = false) String lang,
                                                      Authentication authentication) throws IOException {
        if (!uploadRateLimiter.tryConsume(authentication.getName())) {
            throw new ApiException(ErrorCode.TOO_MANY_UPLOAD_ATTEMPTS);
        }

        if (templateId == null) {
            throw new ApiException(ErrorCode.TEMPLATE_ID_REQUIRED);
        }

        byte[] fileBytes = file.getBytes();
        String detectedContentType = FileSignatureValidator.detectContentType(fileBytes);
        if (detectedContentType == null) {
            throw new ApiException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        User uploader = userRepository.findByUsernameAndActiveTrue(authentication.getName())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, authentication.getName()));

        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ApiException(ErrorCode.TEMPLATE_NOT_FOUND, String.valueOf(templateId)));

        int actualPageCount = detectPageCount(fileBytes, detectedContentType);
        if (actualPageCount != template.getPageCount()) {
            throw new ApiException(ErrorCode.PAGE_COUNT_MISMATCH, actualPageCount, template.getPageCount());
        }

        DocumentMetadata document = new DocumentMetadata();
        document.setFileName(file.getOriginalFilename());
        document.setUploadedBy(uploader);
        document.setStatus(DocumentStatus.PROCESSING);
        document.setLanguage(DocumentLanguage.fromParam(lang));
        document.setTemplate(template);

        document = documentRepository.save(document);
        auditLogRepository.save(new AuditLog(document.getId(), "DOCUMENT_UPLOADED", uploader.getUsername()));

        documentService.processDocument(document.getId(), fileBytes, detectedContentType, templateId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", document.getId());
        body.put("status", document.getStatus().name());
        body.put("language", document.getLanguage().name());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public ResponseEntity<PagedResponse<DocumentSummaryResponse>> list(@RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size,
                                                                       Authentication authentication) {
        User currentUser = currentUser(authentication);

        Page<DocumentMetadata> result = currentUser.getRole() == UserRole.ADMIN
                ? documentRepository.findAllByOrderByUploadedAtDesc(PageRequest.of(page, size))
                : documentRepository.findByUploadedByOrderByUploadedAtDesc(currentUser, PageRequest.of(page, size));

        List<DocumentSummaryResponse> content = result.getContent().stream().map(this::toSummary).toList();
        return ResponseEntity.ok(new PagedResponse<>(content, page, size, result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public ResponseEntity<DocumentSummaryResponse> getById(@PathVariable Long id, Authentication authentication) {
        DocumentMetadata document = loadAccessibleDocument(id, authentication);
        return ResponseEntity.ok(toSummary(document));
    }

    @GetMapping("/{id}/segments/{segmentId}/image")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public ResponseEntity<byte[]> getSegmentImage(@PathVariable Long id, @PathVariable Long segmentId,
                                                  Authentication authentication) {
        loadAccessibleDocument(id, authentication);

        SegmentImage image = segmentImageRepository.findByDocumentIdAndSegmentId(id, segmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.SEGMENT_IMAGE_NOT_FOUND, String.valueOf(segmentId)));

        byte[] imageBytes = Base64.getDecoder().decode(image.getImageDataBase64());
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_JPEG)
                .body(imageBytes);
    }

    @PostMapping("/{id}/segments/{segmentId}/resolve")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public ResponseEntity<DocumentSummaryResponse> resolveSegment(@PathVariable Long id,
                                                                  @PathVariable Long segmentId,
                                                                  @Valid @RequestBody SegmentResolveRequest request,
                                                                  Authentication authentication) {
        loadAccessibleDocument(id, authentication);

        DocumentMetadata document = documentService.resolveSegment(id, segmentId, request.getOutcome(), authentication.getName());
        return ResponseEntity.ok(toSummary(document));
    }

    @PostMapping("/{id}/segments/{segmentId}/override")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DocumentSummaryResponse> overrideSegment(@PathVariable Long id,
                                                                   @PathVariable Long segmentId,
                                                                   @Valid @RequestBody SegmentOverrideRequest request,
                                                                   Authentication authentication) {
        DocumentMetadata document = documentService.overrideSegment(
                id, segmentId, request.getOutcome(), request.getReasonCode(), request.getNote(), authentication.getName());
        return ResponseEntity.ok(toSummary(document));
    }

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public ResponseEntity<PagedResponse<DocumentSummaryResponse>> queue(@RequestParam(defaultValue = "0") int page,
                                                                        @RequestParam(defaultValue = "20") int size,
                                                                        Authentication authentication) {
        User currentUser = currentUser(authentication);
        User scopeUser = currentUser.getRole() == UserRole.ADMIN ? null : currentUser;

        Page<DocumentMetadata> result = documentRepository.findByStatusScoped(
                DocumentStatus.PENDING_REVIEW, scopeUser, PageRequest.of(page, size));
        List<DocumentSummaryResponse> content = result.getContent().stream().map(this::toSummary).toList();
        return ResponseEntity.ok(new PagedResponse<>(content, page, size, result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public ResponseEntity<DocumentStatsResponse> stats(Authentication authentication) {
        User currentUser = currentUser(authentication);
        User scopeUser = currentUser.getRole() == UserRole.ADMIN ? null : currentUser;

        ZoneId zone = ZoneId.systemDefault();
        Instant startOfToday = LocalDate.now(zone).atStartOfDay(zone).toInstant();
        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        long todayUploads = documentRepository.countUploadsSince(startOfToday, scopeUser);
        long pendingReview = documentRepository.countByStatusScoped(DocumentStatus.PENDING_REVIEW, scopeUser);

        long validated = 0;
        long rejected = 0;
        for (Object[] row : documentRepository.countTerminalStatusesSince(sevenDaysAgo, scopeUser)) {
            DocumentStatus status = (DocumentStatus) row[0];
            long count = (Long) row[1];
            if (status == DocumentStatus.VALIDATED) {
                validated = count;
            } else {
                rejected += count;
            }
        }
        long totalTerminal = validated + rejected;
        Double weeklyValidationRate = totalTerminal > 0 ? (validated * 100.0 / totalTerminal) : null;

        return ResponseEntity.ok(new DocumentStatsResponse(todayUploads, pendingReview, weeklyValidationRate));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsernameAndActiveTrue(authentication.getName())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, authentication.getName()));
    }

    private DocumentMetadata loadAccessibleDocument(Long id, Authentication authentication) {
        DocumentMetadata document = documentRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.DOCUMENT_NOT_FOUND, String.valueOf(id)));

        User currentUser = currentUser(authentication);
        boolean isOwner = document.getUploadedBy() != null
                && document.getUploadedBy().getId().equals(currentUser.getId());
        if (currentUser.getRole() != UserRole.ADMIN && !isOwner) {
            throw new ApiException(ErrorCode.DOCUMENT_NOT_FOUND, String.valueOf(id));
        }
        return document;
    }

    private int detectPageCount(byte[] fileBytes, String contentType) {
        if (!FileSignatureValidator.PDF_CONTENT_TYPE.equals(contentType)) {
            return 1;
        }
        try (PDDocument document = Loader.loadPDF(fileBytes)) {
            return document.getNumberOfPages();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.PDF_UNREADABLE);
        }
    }

    private DocumentSummaryResponse toSummary(DocumentMetadata document) {
        Long templateId = document.getTemplate() != null ? document.getTemplate().getId() : null;
        String uploadedByUsername = document.getUploadedBy() != null ? document.getUploadedBy().getUsername() : null;
        String operatorUsername = document.getOperator() != null ? document.getOperator().getUsername() : null;
        return new DocumentSummaryResponse(
                document.getId(),
                document.getFileName(),
                document.getStatus(),
                templateId,
                document.getLanguage(),
                document.getSegmentResults(),
                uploadedByUsername,
                operatorUsername,
                document.getUploadedAt(),
                document.getProcessedAt(),
                document.getFailureReason()
        );
    }
}