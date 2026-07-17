package com.validdoc.service;

import com.validdoc.dto.internal.ValidationResult;
import com.validdoc.dto.ocr.OcrDocumentResult;
import com.validdoc.exception.OpenCVException;
import com.validdoc.exception.PdfRasterizationException;
import com.validdoc.exception.TemplateDefinitionException;
import com.validdoc.model.AuditLog;
import com.validdoc.model.DocumentMetadata;
import com.validdoc.model.Template;
import com.validdoc.model.enums.DocumentStatus;
import com.validdoc.repository.AuditLogRepository;
import com.validdoc.repository.DocumentRepository;
import com.validdoc.repository.TemplateRepository;
import jakarta.persistence.EntityNotFoundException;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDateTime;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final String ENGINE_ERROR_AUDIT_ACTION = "ENGINE_ERROR_PENDING_REVIEW";

    private final DocumentRepository documentRepository;
    private final TemplateRepository templateRepository;
    private final AuditLogRepository auditLogRepository;
    private final PdfRasterService pdfRasterService;
    private final OcrService ocrService;
    private final ValidationService validationService;
    private final NotificationService notificationService;
    private final ValidationSettingsService validationSettingsService;

    public DocumentService(DocumentRepository documentRepository,
                           TemplateRepository templateRepository,
                           AuditLogRepository auditLogRepository,
                           PdfRasterService pdfRasterService,
                           OcrService ocrService,
                           ValidationService validationService,
                           NotificationService notificationService,
                           ValidationSettingsService validationSettingsService) {
        this.documentRepository = documentRepository;
        this.templateRepository = templateRepository;
        this.auditLogRepository = auditLogRepository;
        this.pdfRasterService = pdfRasterService;
        this.ocrService = ocrService;
        this.validationService = validationService;
        this.notificationService = notificationService;
        this.validationSettingsService = validationSettingsService;
    }

    @Async
    @Transactional
    public void processDocument(Long documentId, byte[] fileBytes, String contentType, Long templateId) {
        DocumentMetadata document = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("DocumentMetadata bulunamadi, id=" + documentId));

        try {
            Template template = resolveTemplate(templateId);

            BufferedImage image = PDF_CONTENT_TYPE.equals(contentType)
                    ? pdfRasterService.renderFirstPage(new ByteArrayInputStream(fileBytes))
                    : ImageIO.read(new ByteArrayInputStream(fileBytes));

            if (image == null) {
                throw new IOException("Goruntu formati desteklenmiyor veya bozuk");
            }

            OcrDocumentResult ocrResult = ocrService.process(image, template);
            ValidationResult result = validationService.validate(ocrResult, template);

            applyValidationResult(document, result);
            finalizeDocument(document, "AUTO_" + document.getStatus().name());
        } catch (PdfRasterizationException | TesseractException | OpenCVException | TemplateDefinitionException e) {
            log.error("Belge isleme motoru hatasi, documentId={}", documentId, e);
            applyEngineFailure(document);
            finalizeDocument(document, ENGINE_ERROR_AUDIT_ACTION);
        } catch (IOException e) {
            log.error("Goruntu okunamadi, documentId={}", documentId, e);
            applyEngineFailure(document);
            finalizeDocument(document, ENGINE_ERROR_AUDIT_ACTION);
        } catch (Exception e) {
            log.error("Beklenmeyen hata, documentId={}", documentId, e);
            applyEngineFailure(document);
            finalizeDocument(document, ENGINE_ERROR_AUDIT_ACTION);
        }

        if (document.getStatus() == DocumentStatus.REJECTED_EMPTY
                || document.getStatus() == DocumentStatus.REJECTED_INVALID) {
            notificationService.notifyRejection(document);
        }
    }

    private Template resolveTemplate(Long templateId) {
        if (templateId == null) {
            return null;
        }
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("Template bulunamadi, id=" + templateId));
    }

    private void applyValidationResult(DocumentMetadata document, ValidationResult result) {
        document.setValidationMode(result.getValidationMode());
        document.setConfidenceScore(result.getConfidenceScore());
        document.setValidationErrorLogs(result.getValidationErrorLogs());
        document.setExtractedMaskedData(result.getExtractedMaskedData());
        document.setStatus(result.getStatus());
        document.setProcessedAt(LocalDateTime.now());

        if (isTerminalStatus(result.getStatus())) {
            document.setPurgeAt(document.getProcessedAt().plusDays(validationSettingsService.getRetentionDays()));
        }
    }

    private void applyEngineFailure(DocumentMetadata document) {
        document.setStatus(DocumentStatus.PENDING_REVIEW);
        document.setConfidenceScore(0.0);
        document.setProcessedAt(LocalDateTime.now());
    }

    private boolean isTerminalStatus(DocumentStatus status) {
        return status == DocumentStatus.VALIDATED
                || status == DocumentStatus.REJECTED_EMPTY
                || status == DocumentStatus.REJECTED_INVALID;
    }

    private void finalizeDocument(DocumentMetadata document, String auditAction) {
        documentRepository.save(document);
        auditLogRepository.save(new AuditLog(document.getId(), auditAction, "SYSTEM"));
    }
}