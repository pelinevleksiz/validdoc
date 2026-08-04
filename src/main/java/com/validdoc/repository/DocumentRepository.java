package com.validdoc.repository;

import com.validdoc.model.DocumentMetadata;
import com.validdoc.model.enums.DocumentStatus;
import com.validdoc.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentMetadata, Long> {

    List<DocumentMetadata> findByPurgeAtLessThanEqualAndSegmentResultsIsNotNull(Instant dateTime);

    List<DocumentMetadata> findByStatusAndProcessedAtLessThan(DocumentStatus status, Instant cutoff);

    Page<DocumentMetadata> findAllByOrderByUploadedAtDesc(Pageable pageable);

    Page<DocumentMetadata> findByUploadedByOrderByUploadedAtDesc(User uploadedBy, Pageable pageable);

    @Query("select d from DocumentMetadata d where d.status = :status and (:uploadedBy is null or d.uploadedBy = :uploadedBy)")
    Page<DocumentMetadata> findByStatusScoped(@Param("status") DocumentStatus status,
                                              @Param("uploadedBy") User uploadedBy,
                                              Pageable pageable);

    @Query("select count(d) from DocumentMetadata d where d.status = :status and (:uploadedBy is null or d.uploadedBy = :uploadedBy)")
    long countByStatusScoped(@Param("status") DocumentStatus status, @Param("uploadedBy") User uploadedBy);

    @Query("select count(d) from DocumentMetadata d where d.uploadedAt >= :from and (:uploadedBy is null or d.uploadedBy = :uploadedBy)")
    long countUploadsSince(@Param("from") Instant from, @Param("uploadedBy") User uploadedBy);

    @Query("select d.status as status, count(d) as total from DocumentMetadata d " +
            "where d.processedAt >= :from and d.status in (com.validdoc.model.enums.DocumentStatus.VALIDATED, " +
            "com.validdoc.model.enums.DocumentStatus.REJECTED_EMPTY, com.validdoc.model.enums.DocumentStatus.REJECTED_INVALID) " +
            "and (:uploadedBy is null or d.uploadedBy = :uploadedBy) group by d.status")
    List<Object[]> countTerminalStatusesSince(@Param("from") Instant from, @Param("uploadedBy") User uploadedBy);
}