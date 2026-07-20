package com.validdoc.controller;

import com.validdoc.dto.response.AuditLogResponse;
import com.validdoc.dto.response.PagedResponse;
import com.validdoc.model.AuditLog;
import com.validdoc.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/audit-logs")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    public AuditLogController(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<PagedResponse<AuditLogResponse>> list(@RequestParam(value = "documentId", required = false) Long documentId,
                                                                @RequestParam(defaultValue = "0") int page,
                                                                @RequestParam(defaultValue = "20") int size) {
        Page<AuditLog> result = documentId != null
                ? auditLogRepository.findByDocumentId(documentId, PageRequest.of(page, size))
                : auditLogRepository.findAll(PageRequest.of(page, size));

        List<AuditLogResponse> content = result.getContent().stream()
                .map(log -> new AuditLogResponse(log.getId(), log.getDocumentId(), log.getAction(), log.getPerformedBy(), log.getTimestamp()))
                .toList();

        return ResponseEntity.ok(new PagedResponse<>(content, page, size, result.getTotalElements(), result.getTotalPages()));
    }
}