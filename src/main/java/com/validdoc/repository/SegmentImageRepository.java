package com.validdoc.repository;

import com.validdoc.model.SegmentImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SegmentImageRepository extends JpaRepository<SegmentImage, Long> {
    Optional<SegmentImage> findByDocumentIdAndSegmentId(Long documentId, Long segmentId);
    void deleteByDocumentIdAndSegmentId(Long documentId, Long segmentId);
    void deleteByDocumentId(Long documentId);
}