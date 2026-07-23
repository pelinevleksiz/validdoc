package com.validdoc.scheduler;

import com.validdoc.model.AuditLog;
import com.validdoc.model.DocumentMetadata;
import com.validdoc.model.enums.DocumentStatus;
import com.validdoc.repository.AuditLogRepository;
import com.validdoc.repository.DocumentRepository;
import com.validdoc.repository.SegmentImageRepository;
import com.validdoc.service.ValidationSettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class RetentionCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(RetentionCleanupJob.class);
    private static final String RETENTION_PURGE_ACTION = "RETENTION_PURGE";
    private static final String ABANDONED_REVIEW_ACTION = "RETENTION_ABANDONED_REVIEW_EXPIRED";
    private static final int ABANDONED_REVIEW_MULTIPLIER = 2;

    private final DocumentRepository documentRepository;
    private final AuditLogRepository auditLogRepository;
    private final SegmentImageRepository segmentImageRepository;
    private final ValidationSettingsService validationSettingsService;

    public RetentionCleanupJob(DocumentRepository documentRepository,
                               AuditLogRepository auditLogRepository,
                               SegmentImageRepository segmentImageRepository,
                               ValidationSettingsService validationSettingsService) {
        this.documentRepository = documentRepository;
        this.auditLogRepository = auditLogRepository;
        this.segmentImageRepository = segmentImageRepository;
        this.validationSettingsService = validationSettingsService;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredSegmentResults() {
        List<DocumentMetadata> expired = documentRepository.findByPurgeAtLessThanEqualAndSegmentResultsIsNotNull(LocalDateTime.now());

        for (DocumentMetadata document : expired) {
            document.setSegmentResults(null);
            documentRepository.save(document);
            auditLogRepository.save(new AuditLog(document.getId(), RETENTION_PURGE_ACTION, "SYSTEM"));
        }

        if (!expired.isEmpty()) {
            log.info("Retention purge tamamlandi, {} kayit anonimlestirildi", expired.size());
        }
    }

    @Scheduled(cron = "0 30 3 * * *")
    @Transactional
    public void expireAbandonedReviews() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays((long) validationSettingsService.getRetentionDays() * ABANDONED_REVIEW_MULTIPLIER);
        List<DocumentMetadata> abandoned = documentRepository.findByStatusAndProcessedAtLessThan(DocumentStatus.PENDING_REVIEW, cutoff);

        for (DocumentMetadata document : abandoned) {
            document.setStatus(DocumentStatus.REJECTED_INVALID);
            document.setPurgeAt(LocalDateTime.now().plusDays(validationSettingsService.getRetentionDays()));
            documentRepository.save(document);
            segmentImageRepository.deleteByDocumentId(document.getId());
            auditLogRepository.save(new AuditLog(document.getId(), ABANDONED_REVIEW_ACTION, "SYSTEM"));
        }

        if (!abandoned.isEmpty()) {
            log.info("Terk edilmis {} PENDING_REVIEW belgesi otomatik REJECTED_INVALID olarak isaretlendi", abandoned.size());
        }
    }
}