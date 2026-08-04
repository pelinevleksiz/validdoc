package com.validdoc.service;

import com.validdoc.dto.internal.SegmentReading;
import com.validdoc.dto.internal.SegmentResultEntry;
import com.validdoc.dto.internal.ValidationResult;
import com.validdoc.model.SegmentRule;
import com.validdoc.model.TemplateSegment;
import com.validdoc.model.enums.DocumentStatus;
import com.validdoc.model.enums.SegmentOutcome;
import com.validdoc.model.enums.SegmentRuleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    private static final Pattern TC_KIMLIK_NO_PATTERN = Pattern.compile("^\\d{11}$");
    private static final Pattern VKN_PATTERN = Pattern.compile("^\\d{10}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+?\\d{7,15}$");
    private static final Pattern PHONE_SEPARATOR_PATTERN = Pattern.compile("[\\s()\\-./]");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[\\w.+-]+@[\\w-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern LETTERS_ONLY_PATTERN = Pattern.compile("^[\\p{L} ]+$");
    private static final Pattern DIGITS_ONLY_PATTERN = Pattern.compile("^\\d+$");
    private static final Pattern ALPHANUMERIC_PATTERN = Pattern.compile("^[\\p{L}\\d]+$");
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/uuuu", Locale.forLanguageTag("tr-TR"))
                    .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter DATE_FORMATTER_DOTTED =
            DateTimeFormatter.ofPattern("dd.MM.uuuu", Locale.forLanguageTag("tr-TR"))
                    .withResolverStyle(ResolverStyle.STRICT);

    private final ValidationSettingsService settings;
    private final JsonMapper jsonMapper;

    public ValidationService(ValidationSettingsService settings, JsonMapper jsonMapper) {
        this.settings = settings;
        this.jsonMapper = jsonMapper;
    }

    public ValidationResult validate(List<SegmentReading> readings) {
        List<SegmentResultEntry> entries = new ArrayList<>();

        for (SegmentReading reading : readings) {
            TemplateSegment segment = reading.getSegment();
            SegmentResultEntry entry = new SegmentResultEntry();
            entry.setSegmentId(segment.getId());
            entry.setLabel(segment.getLabel());

            if (reading.isInkSegment()) {
                boolean inked = reading.getPixelDensity() != null
                        && reading.getPixelDensity() >= settings.getInkDensityThreshold();
                entry.setOutcome(inked ? SegmentOutcome.FILLED_VALID : SegmentOutcome.EMPTY);
            } else {
                String text = reading.getExtractedText() == null ? "" : reading.getExtractedText().trim();
                if (text.isEmpty()) {
                    boolean hasUndetectedContent = reading.getPixelDensity() != null
                            && reading.getPixelDensity() >= settings.getInkDensityThreshold();
                    if (hasUndetectedContent) {
                        entry.setOutcome(SegmentOutcome.PENDING_REVIEW);
                        entry.setReason("OCR metin okuyamadı ama alanda görünür içerik tespit edildi");
                    } else {
                        entry.setOutcome(SegmentOutcome.EMPTY);
                    }
                } else {
                    List<String> failedRules = evaluateTextRules(segment, text);
                    boolean lowConfidence = reading.getOcrConfidence() != null
                            && reading.getOcrConfidence() < settings.getOcrConfidenceThreshold();

                    if (lowConfidence) {
                        entry.setOutcome(SegmentOutcome.PENDING_REVIEW);
                        double roundedConfidence = Math.round(reading.getOcrConfidence() * 10.0) / 10.0;
                        entry.setReason("OCR düşük güvenle okudu (%" + roundedConfidence + ")");
                        if (!failedRules.isEmpty()) {
                            entry.setFailedRules(failedRules);
                        }
                    } else if (failedRules.isEmpty()) {
                        entry.setOutcome(SegmentOutcome.FILLED_VALID);
                    } else {
                        entry.setOutcome(SegmentOutcome.FILLED_INVALID);
                        entry.setFailedRules(failedRules);
                    }

                    entry.setMaskedValue(maskValue(segment, text));
                    if (reading.getOcrConfidence() != null) {
                        entry.setOcrConfidence(Math.round(reading.getOcrConfidence() * 10.0) / 10.0);
                    }
                }
            }
            entries.add(entry);
        }

        DocumentStatus status = deriveStatus(entries);
        String segmentResultsJson = toJson(entries);
        log.debug("Document classified as {}, segments={}", status, segmentResultsJson);

        return new ValidationResult(status, segmentResultsJson, entries);
    }

    public DocumentStatus deriveStatus(List<SegmentResultEntry> entries) {
        if (entries.isEmpty()) {
            return DocumentStatus.REJECTED_EMPTY;
        }

        long emptyCount = entries.stream().filter(e -> e.getOutcome() == SegmentOutcome.EMPTY).count();
        long validCount = entries.stream().filter(e -> e.getOutcome() == SegmentOutcome.FILLED_VALID).count();
        long pendingCount = entries.stream().filter(e -> e.getOutcome() == SegmentOutcome.PENDING_REVIEW).count();

        if (emptyCount == entries.size()) {
            return DocumentStatus.REJECTED_EMPTY;
        }
        if (pendingCount > 0) {
            return DocumentStatus.PENDING_REVIEW;
        }
        if (validCount == entries.size()) {
            return DocumentStatus.VALIDATED;
        }
        return DocumentStatus.REJECTED_INVALID;
    }

    private List<String> evaluateTextRules(TemplateSegment segment, String text) {
        List<String> failed = new ArrayList<>();
        for (SegmentRule rule : segment.getRules()) {
            if (!matchesRule(rule, text)) {
                failed.add(rule.getRuleType().name());
            }
        }
        return failed;
    }

    private boolean matchesRule(SegmentRule rule, String text) {
        return switch (rule.getRuleType()) {
            case LETTERS_ONLY -> LETTERS_ONLY_PATTERN.matcher(text).matches();
            case DIGITS_ONLY -> DIGITS_ONLY_PATTERN.matcher(text).matches();
            case ALPHANUMERIC -> ALPHANUMERIC_PATTERN.matcher(text).matches();
            case DATE -> isValidDate(text);
            case MIN_LENGTH -> rule.getParam() != null && text.length() >= rule.getParam();
            case MAX_LENGTH -> rule.getParam() != null && text.length() <= rule.getParam();
            case TC_KIMLIK_NO -> TC_KIMLIK_NO_PATTERN.matcher(text).matches();
            case VKN -> VKN_PATTERN.matcher(text).matches();
            case PHONE -> isValidPhone(text);
            case EMAIL -> EMAIL_PATTERN.matcher(text).matches();
            case SIGNATURE_INK, STAMP_INK -> true;
        };
    }

    private boolean isValidPhone(String text) {
        String stripped = PHONE_SEPARATOR_PATTERN.matcher(text).replaceAll("");
        return PHONE_PATTERN.matcher(stripped).matches();
    }

    private boolean isValidDate(String text) {
        try {
            LocalDate.parse(text, DATE_FORMATTER);
            return true;
        } catch (DateTimeParseException e) {
            try {
                LocalDate.parse(text, DATE_FORMATTER_DOTTED);
                return true;
            } catch (DateTimeParseException e2) {
                return false;
            }
        }
    }

    private String maskValue(TemplateSegment segment, String text) {
        boolean isSensitive = segment.getRules().stream()
                .anyMatch(r -> r.getRuleType() == SegmentRuleType.TC_KIMLIK_NO || r.getRuleType() == SegmentRuleType.VKN);
        if (!isSensitive || text.length() <= 2) {
            return text;
        }
        return text.charAt(0) + "*".repeat(text.length() - 2) + text.charAt(text.length() - 1);
    }

    private String toJson(List<SegmentResultEntry> entries) {
        try {
            return jsonMapper.writeValueAsString(entries);
        } catch (JacksonException e) {
            throw new IllegalStateException("segmentResults JSON yazilamadi", e);
        }
    }
}