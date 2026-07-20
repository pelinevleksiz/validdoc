package com.validdoc.service;

import com.validdoc.dto.internal.SegmentReading;
import com.validdoc.dto.internal.ValidationResult;
import com.validdoc.exception.OpenCVException;
import com.validdoc.exception.PageOutOfBoundsException;
import com.validdoc.exception.PdfRasterizationException;
import com.validdoc.exception.TemplateDefinitionException;
import com.validdoc.model.AuditLog;
import com.validdoc.model.DocumentMetadata;
import com.validdoc.model.Template;
import com.validdoc.model.TemplateSegment;
import com.validdoc.model.enums.DocumentLanguage;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final int SINGLE_IMAGE_PAGE_NUMBER = 1;
    private static final String ENGINE_ERROR_AUDIT_ACTION = "ENGINE_ERROR_PENDING_REVIEW";

    private final DocumentRepository documentRepository;
    private final TemplateRepository templateRepository;
    private final AuditLogRepository auditLogRepository;
    private final PdfRasterService pdfRasterService;
    private final OcrService ocrService;
    private final ValidationService validationService;
    private final ValidationSettingsService validationSettingsService;

    public DocumentService(DocumentRepository documentRepository,
                           TemplateRepository templateRepository,
                           AuditLogRepository auditLogRepository,
                           PdfRasterService pdfRasterService,
                           OcrService ocrService,
                           ValidationService validationService,
                           ValidationSettingsService validationSettingsService) {
        this.documentRepository = documentRepository;
        this.templateRepository = templateRepository;
        this.auditLogRepository = auditLogRepository;
        this.pdfRasterService = pdfRasterService;
        this.ocrService = ocrService;
        this.validationService = validationService;
        this.validationSettingsService = validationSettingsService;
    }

    @Async
    @Transactional
    public void processDocument(Long documentId, byte[] fileBytes, String contentType, Long templateId) {
        DocumentMetadata document = documentRepository.findById(documentId)
                .orElseThrow(() -> new EntityNotFoundException("DocumentMetadata bulunamadi, id=" + documentId));

        try {
            Template template = resolveTemplate(templateId);
            if (template == null) {
                throw new TemplateDefinitionException("Template zorunludur, template-free mod artik desteklenmiyor", null);
            }
            DocumentLanguage language = document.getLanguage() != null ? document.getLanguage() : DocumentLanguage.TUR;

            Set<Integer> requiredPages = template.getSegments().stream()
                    .map(TemplateSegment::getPage)
                    .collect(Collectors.toSet());

            Map<Integer, BufferedImage> pages = PDF_CONTENT_TYPE.equals(contentType)
                    ? pdfRasterService.renderPages(new ByteArrayInputStream(fileBytes), requiredPages)
                    : renderSingleImagePage(fileBytes, requiredPages);

            List<SegmentReading> readings = ocrService.process(pages, template, language);
            ValidationResult result = validationService.validate(readings);

            applyValidationResult(document, result);
            finalizeDocument(document, "AUTO_" + document.getStatus().name());
        } catch (PdfRasterizationException | PageOutOfBoundsException | TesseractException
                 | OpenCVException | TemplateDefinitionException e) {
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
    }

    private Map<Integer, BufferedImage> renderSingleImagePage(byte[] fileBytes, Set<Integer> requiredPages) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(fileBytes));
        if (image == null) {
            throw new IOException("Goruntu formati desteklenmiyor veya bozuk");
        }
        for (Integer requiredPage : requiredPages) {
            if (requiredPage == null || requiredPage != SINGLE_IMAGE_PAGE_NUMBER) {
                throw new PageOutOfBoundsException(
                        "Tek sayfalik resim yuklendi, ancak template " + requiredPage + ". sayfayi referans veriyor", null);
            }
        }
        return Map.of(SINGLE_IMAGE_PAGE_NUMBER, image);
    }

    private Template resolveTemplate(Long templateId) {
        if (templateId == null) {
            return null;
        }
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new EntityNotFoundException("Template bulunamadi, id=" + templateId));
    }

    private void applyValidationResult(DocumentMetadata document, ValidationResult result) {
        document.setSegmentResults(result.getSegmentResultsJson());
        document.setStatus(result.getStatus());
        document.setProcessedAt(LocalDateTime.now());

        if (isTerminalStatus(result.getStatus())) {
            document.setPurgeAt(document.getProcessedAt().plusDays(validationSettingsService.getRetentionDays()));
        }
    }

    private void applyEngineFailure(DocumentMetadata document) {
        document.setStatus(DocumentStatus.PENDING_REVIEW);
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