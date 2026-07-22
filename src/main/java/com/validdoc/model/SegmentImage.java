package com.validdoc.model;

import com.validdoc.security.MaskedDataEncryptionConverter;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "segment_images")
public class SegmentImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "segment_id", nullable = false)
    private Long segmentId;

    @Convert(converter = MaskedDataEncryptionConverter.class)
    @Column(name = "image_data", columnDefinition = "TEXT", nullable = false)
    private String imageDataBase64;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public SegmentImage() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }

    public Long getSegmentId() { return segmentId; }
    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }

    public String getImageDataBase64() { return imageDataBase64; }
    public void setImageDataBase64(String imageDataBase64) { this.imageDataBase64 = imageDataBase64; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}