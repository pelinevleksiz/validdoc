package com.validdoc.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class SecretsValidator {

    private static final Set<String> PLACEHOLDER_VALUES = Set.of(
            "change-me-local-password",
            "replace-with-a-long-random-base64-string",
            "replace-with-a-32-byte-base64-key",
            "change-me-strong-password");

    private static final int MIN_JWT_SECRET_BYTES = 32;

    private final String jwtSecret;
    private final String encryptionSecretKey;
    private final String bootstrapAdminPassword;

    public SecretsValidator(@Value("${jwt.secret}") String jwtSecret,
                            @Value("${encryption.secret-key}") String encryptionSecretKey,
                            @Value("${app.bootstrap-admin.password}") String bootstrapAdminPassword) {
        this.jwtSecret = jwtSecret;
        this.encryptionSecretKey = encryptionSecretKey;
        this.bootstrapAdminPassword = bootstrapAdminPassword;
    }

    @PostConstruct
    public void validate() {
        rejectPlaceholder("JWT_SECRET", jwtSecret);
        rejectPlaceholder("ENCRYPTION_SECRET_KEY", encryptionSecretKey);
        rejectPlaceholder("BOOTSTRAP_ADMIN_PASSWORD", bootstrapAdminPassword);

        int jwtSecretBytes = jwtSecret.getBytes(StandardCharsets.UTF_8).length;
        if (jwtSecretBytes < MIN_JWT_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET must be at least " + MIN_JWT_SECRET_BYTES
                    + " bytes to sign HMAC-SHA256 tokens, but is " + jwtSecretBytes
                    + ". Generate a new one as described in README.md.");
        }
    }

    private void rejectPlaceholder(String name, String value) {
        if (value == null || value.isBlank() || PLACEHOLDER_VALUES.contains(value)) {
            throw new IllegalStateException(name + " is unset or still holds the placeholder value published in "
                    + ".env.example. Generate a real secret as described in README.md before starting the application.");
        }
    }
}