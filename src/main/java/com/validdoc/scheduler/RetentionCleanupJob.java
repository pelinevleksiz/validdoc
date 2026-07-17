package com.validdoc.scheduler;

import com.validdoc.model.AuditLog;
import com.validdoc.model.DocumentMetadata;
import com.validdoc.repository.AuditLogRepository;
import com.validdoc.repository.DocumentRepository;
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

    private final DocumentRepository documentRepository;
    private final AuditLogRepository auditLogRepository;

    public RetentionCleanupJob(DocumentRepository documentRepository, AuditLogRepository auditLogRepository) {
        this.documentRepository = documentRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeExpiredMaskedData() {
        List<DocumentMetadata> expired = documentRepository.findByPurgeAtLessThanEqualAndExtractedMaskedDataIsNotNull(LocalDateTime.now());

        for (DocumentMetadata document : expired) {
            document.setExtractedMaskedData(null);
            documentRepository.save(document);
            auditLogRepository.save(new AuditLog(document.getId(), RETENTION_PURGE_ACTION, "SYSTEM"));
        }

        if (!expired.isEmpty()) {
            log.info("Retention purge tamamlandi, {} kayit anonimlestirildi", expired.size());
        }
    }
}