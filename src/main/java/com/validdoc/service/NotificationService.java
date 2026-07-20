package com.validdoc.service;

import com.validdoc.model.DocumentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final Locale DEFAULT_EMAIL_LOCALE = Locale.forLanguageTag("tr");

    private final JavaMailSender mailSender;
    private final MessageSource messageSource;
    private final String fromAddress;

    public NotificationService(JavaMailSender mailSender,
                               MessageSource messageSource,
                               @Value("${app.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.messageSource = messageSource;
        this.fromAddress = fromAddress;
    }

    @Async
    public void notifyRejection(DocumentMetadata document) {
        String recipient = document.getUploadedBy() != null ? document.getUploadedBy().getEmail() : null;
        if (recipient == null || recipient.isBlank()) {
            log.warn("Rejection notification skipped, no recipient email, documentId={}", document.getId());
            return;
        }

        try {
            String subject = messageSource.getMessage("email.rejection.subject",
                    new Object[]{document.getId()}, DEFAULT_EMAIL_LOCALE);
            String body = messageSource.getMessage("email.rejection.body",
                    new Object[]{document.getId(), document.getStatus().name()}, DEFAULT_EMAIL_LOCALE);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(recipient);
            message.setSubject(subject);
            message.setText(body);

            mailSender.send(message);
            log.info("Rejection notification sent: documentId={}, status={}, recipient={}",
                    document.getId(), document.getStatus(), recipient);
        } catch (Exception e) {
            log.error("Rejection notification failed, documentId={}, recipient={}", document.getId(), recipient, e);
        }
    }
}