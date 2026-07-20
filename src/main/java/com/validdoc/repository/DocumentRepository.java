package com.validdoc.repository;

import com.validdoc.model.DocumentMetadata;
import com.validdoc.model.enums.DocumentStatus;
import com.validdoc.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentMetadata, Long> {

    List<DocumentMetadata> findByUploadedByOrderByUploadedAtDesc(User uploadedBy);

    Page<DocumentMetadata> findByStatus(DocumentStatus status, Pageable pageable);

    List<DocumentMetadata> findByPurgeAtLessThanEqualAndSegmentResultsIsNotNull(LocalDateTime dateTime);

    Page<DocumentMetadata> findAllByOrderByUploadedAtDesc(Pageable pageable);
}