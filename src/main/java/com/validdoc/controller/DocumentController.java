package com.validdoc.controller;

import com.validdoc.dto.request.VerificationRequest;
import com.validdoc.dto.response.DocumentSummaryResponse;
import com.validdoc.exception.ApiException;
import com.validdoc.exception.ErrorCode;
import com.validdoc.model.AuditLog;
import com.validdoc.model.DocumentMetadata;
import com.validdoc.model.Template;
import com.validdoc.model.User;
import com.validdoc.model.enums.DocumentLanguage;
import com.validdoc.model.enums.DocumentStatus;
import com.validdoc.repository.AuditLogRepository;
import com.validdoc.repository.DocumentRepository;
import com.validdoc.repository.TemplateRepository;
import com.validdoc.repository.UserRepository;
import com.validdoc.service.DocumentService;
import com.validdoc.service.NotificationService;
import com.validdoc.service.ValidationSettingsService;
import jakarta.validation.Valid;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
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
    private final DocumentService documentService;
    private final ValidationSettingsService validationSettingsService;
    private final NotificationService notificationService;
    private final MessageSource messageSource;

    public DocumentController(DocumentRepository documentRepository,
                              TemplateRepository templateRepository,
                              UserRepository userRepository,
                              AuditLogRepository auditLogRepository,
                              DocumentService documentService,
                              ValidationSettingsService validationSettingsService,
                              NotificationService notificationService,
                              MessageSource messageSource) {
        this.documentRepository = documentRepository;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.documentService = documentService;
        this.validationSettingsService = validationSettingsService;
        this.notificationService = notificationService;
        this.messageSource = messageSource;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "templateId", required = false) Long templateId,
                                                      @RequestParam(value = "lang", required = false) String lang,
                                                      Authentication authentication) throws IOException {
        User uploader = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new ApiException(ErrorCode.USER_NOT_FOUND, authentication.getName()));

        DocumentMetadata document = new DocumentMetadata();
        document.setFileName(file.getOriginalFilename());
        document.setUploadedBy(uploader);
        document.setStatus(DocumentStatus.PROCESSING);
        document.setLanguage(DocumentLanguage.fromParam(lang));

        if (templateId != null) {
            Template template = templateRepository.findById(templateId)
                    .orElseThrow(() -> new ApiException(ErrorCode.TEMPLATE_NOT_FOUND, String.valueOf(templateId)));
            document.setTemplate(template);
        }

        document = documentRepository.save(document);
        auditLogRepository.save(new AuditLog(document.getId(), "DOCUMENT_UPLOADED", uploader.getUsername()));

        documentService.processDocument(document.getId(), file.getBytes(), file.getContentType(), templateId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("documentId", document.getId());
        body.put("status", document.getStatus().name());
        body.put("language", document.getLanguage().name());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public ResponseEntity<DocumentSummaryResponse> getById(@PathVariable Long id) {
        DocumentMetadata document = documentRepository.findById(id)
                .orElseThrow(() -> new ApiException(ErrorCode.DOCUMENT_NOT_FOUND, String.valueOf(id)));
        return ResponseEntity.ok(toSummary(document));
    }

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public ResponseEntity<List<DocumentSummaryResponse>> queue() {
        List<DocumentSummaryResponse> response = documentRepository.findByStatus(DocumentStatus.PENDING_REVIEW)
                .stream()
                .map(this::toSummary)
                .toList();
        return ResponseEntity.ok(response);
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

        if (target == DocumentStatus.REJECTED_EMPTY || target == DocumentStatus.REJECTED_INVALID) {
            notificationService.notifyRejection(document);
        }

        String message = messageSource.getMessage("message.document.status_updated", null, locale);
        return ResponseEntity.ok(Map.of("message", message));
    }

    private DocumentSummaryResponse toSummary(DocumentMetadata document) {
        Long templateId = document.getTemplate() != null ? document.getTemplate().getId() : null;
        return new DocumentSummaryResponse(
                document.getId(),
                document.getStatus(),
                document.getValidationMode(),
                templateId,
                document.getLanguage(),
                document.getConfidenceScore(),
                document.getValidationErrorLogs(),
                document.getExtractedMaskedData(),
                document.getUploadedAt(),
                document.getProcessedAt()
        );
    }
}