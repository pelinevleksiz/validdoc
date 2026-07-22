package com.validdoc.controller;

import com.validdoc.dto.request.VerificationRequest;
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
import com.validdoc.repository.AuditLogRepository;
import com.validdoc.repository.DocumentRepository;
import com.validdoc.repository.SegmentImageRepository;
import com.validdoc.repository.TemplateRepository;
import com.validdoc.repository.UserRepository;
import com.validdoc.security.UploadRateLimiter;
import com.validdoc.service.DocumentService;
import com.validdoc.service.FileSignatureValidator;
import com.validdoc.service.ValidationSettingsService;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final TemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final SegmentImageRepository segmentImageRepository;
    private final DocumentService documentService;
    private final ValidationSettingsService validationSettingsService;
    private final MessageSource messageSource;
    private final UploadRateLimiter uploadRateLimiter;

    public DocumentController(DocumentRepository documentRepository,
                              TemplateRepository templateRepository,
                              UserRepository userRepository,
                              AuditLogRepository auditLogRepository,
                              SegmentImageRepository segmentImageRepository,
                              DocumentService documentService,
                              ValidationSettingsService validationSettingsService,
                              MessageSource messageSource,
                              UploadRateLimiter uploadRateLimiter) {
        this.documentRepository = documentRepository;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.segmentImageRepository = segmentImageRepository;
        this.documentService = documentService;
        this.validationSettingsService = validationSettingsService;
        this.messageSource = messageSource;
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

        User uploader = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, authentication.getName()));

        Template template = templateRepository.findById(templateId)
                .orElseThrow(() -> new ApiException(ErrorCode.TEMPLATE_NOT_FOUND, String.valueOf(templateId)));

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
        body.put("documentId", document.getId());
        body.put("status", document.getStatus().name());
        body.put("language", document.getLanguage().name());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public ResponseEntity<PagedResponse<DocumentSummaryResponse>> list(@RequestParam(defaultValue = "0") int page,
                                                                       @RequestParam(defaultValue = "20") int size) {
        Page<DocumentMetadata> result = documentRepository.findAllByOrderByUploadedAtDesc(PageRequest.of(page, size));
        List<DocumentSummaryResponse> content = result.getContent().stream().map(this::toSummary).toList();
        return ResponseEntity.ok(new PagedResponse<>(content, page, size, result.getTotalElements(), result.getTotalPages()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public ResponseEntity<DocumentSummaryResponse> getById(@PathVariable Long id) {
        DocumentMetadata document = documentRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.DOCUMENT_NOT_FOUND, String.valueOf(id)));
        return ResponseEntity.ok(toSummary(document));
    }

    @GetMapping("/{id}/segments/{segmentId}/image")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public ResponseEntity<byte[]> getSegmentImage(@PathVariable Long id, @PathVariable Long segmentId) {
        if (!documentRepository.existsById(id)) {
            throw new ApiException(ErrorCode.DOCUMENT_NOT_FOUND, String.valueOf(id));
        }

        SegmentImage image = segmentImageRepository.findByDocumentIdAndSegmentId(id, segmentId)
                .orElseThrow(() -> new ApiException(ErrorCode.SEGMENT_IMAGE_NOT_FOUND, String.valueOf(segmentId)));

        byte[] pngBytes = Base64.getDecoder().decode(image.getImageDataBase64());
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(pngBytes);
    }

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public ResponseEntity<PagedResponse<DocumentSummaryResponse>> queue(@RequestParam(defaultValue = "0") int page,
                                                                        @RequestParam(defaultValue = "20") int size) {
        Page<DocumentMetadata> result = documentRepository.findByStatus(DocumentStatus.PENDING_REVIEW, PageRequest.of(page, size));
        List<DocumentSummaryResponse> content = result.getContent().stream().map(this::toSummary).toList();
        return ResponseEntity.ok(new PagedResponse<>(content, page, size, result.getTotalElements(), result.getTotalPages()));
    }

    @PostMapping("/{id}/verify")
    @PreAuthorize("hasRole('OPERATOR')")
    public ResponseEntity<Map<String, String>> verify(@PathVariable Long id,
                                                      @Valid @RequestBody VerificationRequest request,
                                                      Authentication authentication,
                                                      Locale locale) {
        DocumentStatus target = request.getStatus();
        if (target != DocumentStatus.VALIDATED
                && target != DocumentStatus.REJECTED_EMPTY
                && target != DocumentStatus.REJECTED_INVALID) {
            throw new ApiException(ErrorCode.INVALID_DOCUMENT_STATUS, target);
        }

        DocumentMetadata document = documentRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.DOCUMENT_NOT_FOUND, String.valueOf(id)));

        User operator = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, authentication.getName()));

        document.setStatus(target);
        document.setOperator(operator);
        document.setProcessedAt(LocalDateTime.now());
        document.setPurgeAt(document.getProcessedAt().plusDays(validationSettingsService.getRetentionDays()));
        documentRepository.save(document);

        auditLogRepository.save(new AuditLog(document.getId(), "MANUAL_" + target.name(), operator.getUsername()));

        String message = messageSource.getMessage("message.document.status_updated", null, locale);
        return ResponseEntity.ok(Map.of("message", message));
    }

    private DocumentSummaryResponse toSummary(DocumentMetadata document) {
        Long templateId = document.getTemplate() != null ? document.getTemplate().getId() : null;
        return new DocumentSummaryResponse(
                document.getId(),
                document.getStatus(),
                templateId,
                document.getLanguage(),
                document.getSegmentResults(),
                document.getUploadedAt(),
                document.getProcessedAt()
        );
    }
}