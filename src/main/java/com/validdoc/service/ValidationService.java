package com.validdoc.service;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import com.validdoc.config.ValidationProperties;
import com.validdoc.dto.internal.TemplateFieldDefinition;
import com.validdoc.dto.internal.ValidationResult;
import com.validdoc.dto.ocr.FieldType;
import com.validdoc.dto.ocr.OcrDocumentResult;
import com.validdoc.dto.ocr.OcrFieldResult;
import com.validdoc.exception.TemplateDefinitionException;
import com.validdoc.model.Template;
import com.validdoc.model.enums.DocumentStatus;
import com.validdoc.model.enums.ValidationMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    private static final int MIN_TEXT_LENGTH_FOR_NON_EMPTY = 5;

    private static final List<String> DEFAULT_TEMPLATE_FREE_LABELS =
            List.of("name", "signature", "date", "id_number");

    private static final Pattern ID_NUMBER_PATTERN = Pattern.compile("^\\d{11}$");
    private static final Pattern VKN_PATTERN = Pattern.compile("^\\d{10}$");
    private static final Pattern PHONE_PATTERN =
            Pattern.compile("^(\\+90|0)?\\s?5\\d{2}\\s?\\d{3}\\s?\\d{2}\\s?\\d{2}$");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("tr-TR"))
                    .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATE_FORMATTER_DOTTED =
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.forLanguageTag("tr-TR"))
                    .withResolverStyle(ResolverStyle.STRICT);

    private static final Pattern CONSONANT_RUN_PATTERN =
            Pattern.compile("(?i)[bcçdfgğhjklmnpqrsştvwxyz]{5,}");

    private final ValidationProperties properties;
    private final JsonMapper jsonMapper;

    public ValidationService(ValidationProperties properties, JsonMapper jsonMapper) {
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    public ValidationResult validate(OcrDocumentResult ocrResult, Template template) {
        ValidationMode mode = template != null ? ValidationMode.TEMPLATED : ValidationMode.TEMPLATE_FREE;

        List<String> requiredLabels = resolveRequiredLabels(mode, template);

        Map<String, OcrFieldResult> fieldsByLabel = new LinkedHashMap<>();
        for (OcrFieldResult f : ocrResult.getFields()) {
            fieldsByLabel.put(f.getLabel(), f);
        }

        boolean anyInkDetected = fieldsByLabel.values().stream()
                .filter(f -> f.getType() == FieldType.INK_ZONE)
                .anyMatch(f -> f.getPixelDensity() != null
                        && f.getPixelDensity() >= properties.getInkDensityThreshold());

        String rawText = ocrResult.getRawFullText() == null ? "" : ocrResult.getRawFullText().trim();

        if (rawText.length() < MIN_TEXT_LENGTH_FOR_NON_EMPTY && !anyInkDetected) {
            log.debug("Document classified as REJECTED_EMPTY (rawTextLength={}, anyInk={})",
                    rawText.length(), anyInkDetected);
            return new ValidationResult(DocumentStatus.REJECTED_EMPTY, mode, 0.0, null, null);
        }

        int totalRequired = requiredLabels.size();
        int presentCount = 0;
        for (String label : requiredLabels) {
            OcrFieldResult field = fieldsByLabel.get(label);
            if (field == null || field.getType() == FieldType.INK_ZONE) {
                continue;
            }
            String text = field.getExtractedText() == null ? "" : field.getExtractedText().trim();
            if (!text.isEmpty()) {
                presentCount++;
            }
        }
        double completeness = totalRequired == 0 ? 1.0 : (double) presentCount / totalRequired;

        List<String> formatErrors = new ArrayList<>();
        Map<String, String> maskedFields = new LinkedHashMap<>();
        int checkedTextFields = 0;
        for (OcrFieldResult field : fieldsByLabel.values()) {
            if (field.getType() == FieldType.INK_ZONE) {
                continue;
            }
            String text = field.getExtractedText() == null ? "" : field.getExtractedText().trim();
            if (text.isEmpty()) {
                continue;
            }
            checkedTextFields++;
            boolean valid = isFieldValid(field.getLabel(), text);
            if (!valid) {
                formatErrors.add(field.getLabel());
            } else {
                maskedFields.put(field.getLabel(), maskValue(field.getLabel(), text));
            }
        }

        List<OcrFieldResult> requiredInkZones = new ArrayList<>();
        for (String label : requiredLabels) {
            OcrFieldResult field = fieldsByLabel.get(label);
            if (field != null && field.getType() == FieldType.INK_ZONE) {
                requiredInkZones.add(field);
            }
        }
        double signatureScore;
        if (requiredInkZones.isEmpty()) {
            signatureScore = 1.0;
        } else {
            long inkedCount = requiredInkZones.stream()
                    .filter(f -> f.getPixelDensity() != null
                            && f.getPixelDensity() >= properties.getInkDensityThreshold())
                    .count();
            signatureScore = (double) inkedCount / requiredInkZones.size();
        }

        double formatCorrectness = checkedTextFields == 0
                ? 1.0
                : (double) (checkedTextFields - formatErrors.size()) / checkedTextFields;

        if (!formatErrors.isEmpty()) {
            double score = computeScore(completeness, formatCorrectness, signatureScore);
            String errorLog = buildErrorLog(formatErrors);
            String maskedJson = toJson(maskedFields);
            log.debug("Document classified as REJECTED_INVALID, failedFields={}", formatErrors);
            return new ValidationResult(DocumentStatus.REJECTED_INVALID, mode, score, errorLog, maskedJson);
        }

        double confidenceScore = computeScore(completeness, formatCorrectness, signatureScore);
        String maskedJson = toJson(maskedFields);

        double threshold = properties.getConfidenceThreshold();
        double margin = properties.getReviewMargin();

        DocumentStatus status = (confidenceScore >= threshold + margin)
                ? DocumentStatus.VALIDATED
                : DocumentStatus.PENDING_REVIEW;

        log.debug("Document classified as {}, score={}, completeness={}, format={}, signature={}",
                status, confidenceScore, completeness, formatCorrectness, signatureScore);

        return new ValidationResult(status, mode, confidenceScore, null, maskedJson);
    }

    private List<String> resolveRequiredLabels(ValidationMode mode, Template template) {
        if (mode == ValidationMode.TEMPLATED) {
            return parseTemplateLabels(template);
        }
        return DEFAULT_TEMPLATE_FREE_LABELS;
    }

    private List<String> parseTemplateLabels(Template template) {
        try {
            List<TemplateFieldDefinition> defs = jsonMapper.readValue(
                    template.getFieldDefinitions(),
                    new TypeReference<List<TemplateFieldDefinition>>() {
                    });
            List<String> labels = new ArrayList<>();
            for (TemplateFieldDefinition def : defs) {
                labels.add(def.getLabel());
            }
            return labels;
        } catch (JacksonException e) {
            throw new TemplateDefinitionException(
                    "Template field_definitions parse edilemedi, templateId=" + template.getId(), e);
        }
    }

    private double computeScore(double completeness, double formatCorrectness, double signatureScore) {
        return properties.getWeightCompleteness() * completeness
                + properties.getWeightFormat() * formatCorrectness
                + properties.getWeightSignature() * signatureScore;
    }

    private String buildErrorLog(List<String> failedFields) {
        try {
            return jsonMapper.writeValueAsString(Map.of("invalidFields", failedFields));
        } catch (JacksonException e) {
            return "invalidFields=" + failedFields;
        }
    }

    private String toJson(Map<String, String> maskedFields) {
        if (maskedFields.isEmpty()) return null;
        try {
            return jsonMapper.writeValueAsString(maskedFields);
        } catch (JacksonException e) {
            log.warn("Masked field map serialize edilemedi, null olarak donuluyor", e);
            return null;
        }
    }

    private boolean isFieldValid(String label, String text) {
        return switch (label.toLowerCase(Locale.ROOT)) {
            case "id_number", "tc_no" -> ID_NUMBER_PATTERN.matcher(text).matches();
            case "vkn" -> VKN_PATTERN.matcher(text).matches() && isValidVknChecksum(text);
            case "phone", "phone_number" -> PHONE_PATTERN.matcher(text.replaceAll("\\s+", " ")).matches();
            case "date" -> isValidDate(text) && !isDocumentDateInFuture(text);
            case "name" -> !isGibberish(text);
            default -> !isGibberish(text);
        };
    }

    private boolean isValidDate(String text) {
        return parseDate(text) != null;
    }

    private boolean isDocumentDateInFuture(String text) {
        LocalDate parsed = parseDate(text);
        return parsed != null && parsed.isAfter(LocalDate.now());
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            try {
                return LocalDate.parse(text, DATE_FORMATTER_DOTTED);
            } catch (DateTimeParseException e2) {
                return null;
            }
        }
    }

    private boolean isValidVknChecksum(String vkn) {
        if (vkn == null || vkn.length() != 10) return false;
        try {
            int sum = 0;
            for (int i = 0; i < 9; i++) {
                int digit = Character.getNumericValue(vkn.charAt(i));
                int v = (digit + 10 - (i + 1)) % 10;
                if (v != 9 && v != 0) {
                    v = (v * (int) Math.pow(2, 9 - i)) % 9;
                    if (v == 0) v = 9;
                }
                sum += v;
            }
            int lastDigit = Character.getNumericValue(vkn.charAt(9));
            int checksum = (10 - (sum % 10)) % 10;
            return lastDigit == checksum;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isGibberish(String text) {
        String letters = text.replaceAll("[^\\p{L}]", "");
        if (letters.length() < 3) return false;
        if (CONSONANT_RUN_PATTERN.matcher(letters).find()) return true;
        long vowelCount = letters.toLowerCase(Locale.forLanguageTag("tr-TR"))
                .chars()
                .filter(c -> "aeıioöuüAEIİOÖUÜ".indexOf(c) >= 0)
                .count();
        double vowelRatio = (double) vowelCount / letters.length();
        return vowelRatio < 0.15;
    }

    private String maskValue(String label, String text) {
        return switch (label.toLowerCase(Locale.ROOT)) {
            case "id_number", "tc_no", "vkn" -> maskKeepLast(text, 2);
            case "phone", "phone_number" -> maskKeepLast(text, 2);
            case "name" -> maskName(text);
            default -> maskKeepLast(text, 0);
        };
    }

    private String maskKeepLast(String text, int keepLast) {
        String digitsOnly = text.replaceAll("\\s+", "");
        int len = digitsOnly.length();
        if (keepLast <= 0 || keepLast >= len) return "*".repeat(len);
        return "*".repeat(len - keepLast) + digitsOnly.substring(len - keepLast);
    }

    private String maskName(String text) {
        String[] parts = text.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(' ');
            String part = parts[i];
            sb.append(part.isEmpty() ? "" : part.charAt(0)).append("*".repeat(Math.max(0, part.length() - 1)));
        }
        return sb.toString();
    }
}