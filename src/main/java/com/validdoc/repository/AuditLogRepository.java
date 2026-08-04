package com.validdoc.repository;

import com.validdoc.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends org.springframework.data.repository.Repository<AuditLog, Long> {

    AuditLog save(AuditLog auditLog);

    Page<AuditLog> findAll(Pageable pageable);

    Page<AuditLog> findByDocumentId(Long documentId, Pageable pageable);
}