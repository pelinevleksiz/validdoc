ALTER TABLE segment_images
    ADD CONSTRAINT segment_images_document_id_fk FOREIGN KEY (document_id) REFERENCES document_metadata (id) ON DELETE CASCADE,
    ADD CONSTRAINT segment_images_segment_id_fk FOREIGN KEY (segment_id) REFERENCES template_segments (id);

ALTER TABLE template_segments
ALTER COLUMN label TYPE VARCHAR(30);