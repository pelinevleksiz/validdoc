package com.validdoc.service;

import com.validdoc.model.DocumentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    @Async
    public void notifyRejection(DocumentMetadata document) {
        String recipient = document.getUploadedBy() != null
                ? document.getUploadedBy().getEmail()
                : "unknown";
        log.info("Rejection notification stub: documentId={}, status={}, recipient={}",
                document.getId(), document.getStatus(), recipient);
    }
}