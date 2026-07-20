package com.validdoc.controller;

import com.validdoc.dto.request.ValidationSettingsUpdateRequest;
import com.validdoc.dto.response.ValidationSettingsResponse;
import com.validdoc.model.AuditLog;
import com.validdoc.model.ValidationSettings;
import com.validdoc.repository.AuditLogRepository;
import com.validdoc.service.ValidationSettingsService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/validation-settings")
public class ValidationSettingsController {

    private final ValidationSettingsService settingsService;
    private final AuditLogRepository auditLogRepository;

    public ValidationSettingsController(ValidationSettingsService settingsService,
                                        AuditLogRepository auditLogRepository) {
        this.settingsService = settingsService;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ValidationSettingsResponse> get() {
        return ResponseEntity.ok(toResponse(settingsService.getSnapshot()));
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ValidationSettingsResponse> update(@Valid @RequestBody ValidationSettingsUpdateRequest request,
                                                             Authentication authentication) {
        ValidationSettings updated = settingsService.update(
                request.getRetentionDays(),
                request.getInkDensityThreshold(),
                authentication.getName());

        auditLogRepository.save(new AuditLog("VALIDATION_SETTINGS_UPDATED", authentication.getName()));

        return ResponseEntity.ok(toResponse(updated));
    }

    private ValidationSettingsResponse toResponse(ValidationSettings settings) {
        return new ValidationSettingsResponse(
                settings.getRetentionDays(),
                settings.getInkDensityThreshold(),
                settings.getUpdatedAt(),
                settings.getUpdatedBy());
    }
}