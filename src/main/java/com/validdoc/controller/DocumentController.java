package com.validdoc.controller;

import com.validdoc.config.ValidationProperties;
import com.validdoc.dto.request.VerificationRequest;
import com.validdoc.dto.response.DocumentSummaryResponse;
import com.validdoc.model.AuditLog;
import com.validdoc.model.DocumentMetadata;
import com.validdoc.model.Template;
import com.validdoc.model.User;
import com.validdoc.model.enums.DocumentStatus;
import com.validdoc.repository.AuditLogRepository;
import com.validdoc.repository.DocumentRepository;
import com.validdoc.repository.TemplateRepository;
import com.validdoc.repository.UserRepository;
import com.validdoc.service.DocumentService;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentRepository documentRepository;
    private final TemplateRepository templateRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;
    private final DocumentService documentService;
    private final ValidationProperties validationProperties;

    public DocumentController(DocumentRepository documentRepository,
                              TemplateRepository templateRepository,
                              UserRepository userRepository,
                              AuditLogRepository auditLogRepository,
                              DocumentService documentService,
                              ValidationProperties validationProperties) {
        this.documentRepository = documentRepository;
        this.templateRepository = templateRepository;
        this.userRepository = userRepository;
        this.auditLogRepository = auditLogRepository;
        this.documentService = documentService;
        this.validationProperties = validationProperties;
    }

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                      @RequestParam(value = "templateId", required = false) Long templateId,
                                                      Authentication authentication) throws IOException {
        User uploader = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException("Kullanici bulunamadi: " + authentication.getName()));

        DocumentMetadata document = new DocumentMetadata();
        document.setFileName(file.getOriginalFilename());
        document.setUploadedBy(uploader);
        document.setStatus(DocumentStatus.PROCESSING);

        if (templateId != null) {
            Template template = templateRepository.findById(templateId)
                    .orElseThrow(() -> new EntityNotFoundException("Template bulunamadi, id=" + templateId));
            document.setTemplate(template);
        }

        document = documentRepository.save(document);
        auditLogRepository.save(new AuditLog(document.getId(), "DOCUMENT_UPLOADED", uploader.getUsername()));

        documentService.processDocument(document.getId(), file.getBytes(), file.getContentType(), templateId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("documentId", document.getId());
        body.put("status", document.getStatus().name());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
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
                                                      Authentication authentication) {
        DocumentStatus target = request.getStatus();
        if (target != DocumentStatus.VALIDATED
                && target != DocumentStatus.REJECTED_EMPTY
                && target != DocumentStatus.REJECTED_INVALID) {
            throw new IllegalArgumentException("Gecersiz manuel durum: " + target);
        }

        DocumentMetadata document = documentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("DocumentMetadata bulunamadi, id=" + id));

        User operator = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new EntityNotFoundException("Kullanici bulunamadi: " + authentication.getName()));

        document.setStatus(target);
        document.setOperator(operator);
        document.setProcessedAt(LocalDateTime.now());
        document.setPurgeAt(document.getProcessedAt().plusDays(validationProperties.getRetentionDays()));
        documentRepository.save(document);

        auditLogRepository.save(new AuditLog(document.getId(), "MANUAL_" + target.name(), operator.getUsername()));

        return ResponseEntity.ok(Map.of("message", "Updated"));
    }

    private DocumentSummaryResponse toSummary(DocumentMetadata document) {
        Long templateId = document.getTemplate() != null ? document.getTemplate().getId() : null;
        return new DocumentSummaryResponse(
                document.getId(),
                document.getStatus(),
                document.getValidationMode(),
                templateId,
                document.getConfidenceScore(),
                document.getValidationErrorLogs(),
                document.getExtractedMaskedData(),
                document.getUploadedAt(),
                document.getProcessedAt()
        );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(Map.of("error", "File size exceeds the maximum limit of 5MB"));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleEntityNotFound(EntityNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
}