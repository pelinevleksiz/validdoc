package com.validdoc.service;

import com.validdoc.dto.internal.SegmentReading;
import com.validdoc.dto.internal.ValidationResult;
import com.validdoc.model.SegmentRule;
import com.validdoc.model.TemplateSegment;
import com.validdoc.model.enums.DocumentStatus;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    private static final Pattern TC_KIMLIK_NO_PATTERN = Pattern.compile("^\\d{11}$");
    private static final Pattern VKN_PATTERN = Pattern.compile("^\\d{10}$");
    private static final Pattern PHONE_TR_PATTERN =
            Pattern.compile("^(\\+90|0)?\\s?5\\d{2}\\s?\\d{3}\\s?\\d{2}\\s?\\d{2}$");
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
        List<Map<String, Object>> segmentEntries = new ArrayList<>();
        int emptyCount = 0;
        int validCount = 0;

        for (SegmentReading reading : readings) {
            TemplateSegment segment = reading.getSegment();
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("label", segment.getLabel());

            if (reading.isInkSegment()) {
                boolean inked = reading.getPixelDensity() != null
                        && reading.getPixelDensity() >= settings.getInkDensityThreshold();
                if (inked) {
                    entry.put("outcome", "FILLED_VALID");
                    validCount++;
                } else {
                    entry.put("outcome", "EMPTY");
                    emptyCount++;
                }
            } else {
                String text = reading.getExtractedText() == null ? "" : reading.getExtractedText().trim();
                if (text.isEmpty()) {
                    entry.put("outcome", "EMPTY");
                    emptyCount++;
                } else {
                    List<String> failedRules = evaluateTextRules(segment, text);
                    if (failedRules.isEmpty()) {
                        entry.put("outcome", "FILLED_VALID");
                        validCount++;
                    } else {
                        entry.put("outcome", "FILLED_INVALID");
                        entry.put("failedRules", failedRules);
                    }
                    entry.put("maskedValue", maskValue(segment, text));
                }
            }
            segmentEntries.add(entry);
        }

        DocumentStatus status;
        if (readings.isEmpty()) {
            status = DocumentStatus.REJECTED_EMPTY;
        } else if (emptyCount == readings.size()) {
            status = DocumentStatus.REJECTED_EMPTY;
        } else if (validCount == readings.size()) {
            status = DocumentStatus.VALIDATED;
        } else {
            status = DocumentStatus.REJECTED_INVALID;
        }

        log.debug("Document classified as {}, segments={}", status, segmentEntries);

        return new ValidationResult(status, toJson(segmentEntries));
    }

    private List<String> evaluateTextRules(TemplateSegment segment, String text) {
        List<String> failed = new ArrayList<>();
        for (SegmentRule rule : segment.getRules()) {
            if (!isRuleSatisfied(rule, text)) {
                failed.add(rule.getRuleType().name());
            }
        }
        return failed;
    }

    private boolean isRuleSatisfied(SegmentRule rule, String text) {
        return switch (rule.getRuleType()) {
            case LETTERS_ONLY -> LETTERS_ONLY_PATTERN.matcher(text).matches();
            case DIGITS_ONLY -> DIGITS_ONLY_PATTERN.matcher(text).matches();
            case ALPHANUMERIC -> ALPHANUMERIC_PATTERN.matcher(text).matches();
            case DATE -> isValidDate(text);
            case MIN_LENGTH -> rule.getParam() != null && text.length() >= rule.getParam();
            case MAX_LENGTH -> rule.getParam() != null && text.length() <= rule.getParam();
            case TC_KIMLIK_NO -> TC_KIMLIK_NO_PATTERN.matcher(text).matches() && isValidTcKimlikChecksum(text);
            case VKN -> VKN_PATTERN.matcher(text).matches() && isValidVknChecksum(text);
            case PHONE_TR -> PHONE_TR_PATTERN.matcher(text.replaceAll("\\s+", " ")).matches();
            case EMAIL -> EMAIL_PATTERN.matcher(text).matches();
            case SIGNATURE_INK, STAMP_INK -> true;
        };
    }

    private boolean isValidDate(String text) {
        return parseDate(text) != null;
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text, DATE_FORMATTER);
        } catch (DateTimeParseException slashFailure) {
            try {
                return LocalDate.parse(text, DATE_FORMATTER_DOTTED);
            } catch (DateTimeParseException dottedFailure) {
                log.warn("Tarih ayristirilamadi, text=[{}], slash-hata=[{}], dotted-hata=[{}]",
                        text, slashFailure.getMessage(), dottedFailure.getMessage());
                return null;
            }
        }
    }

    private boolean isValidTcKimlikChecksum(String tc) {
        if (tc == null || tc.length() != 11 || tc.charAt(0) == '0') return false;
        try {
            int[] d = new int[11];
            for (int i = 0; i < 11; i++) {
                d[i] = Character.getNumericValue(tc.charAt(i));
            }
            int oddSum = d[0] + d[2] + d[4] + d[6] + d[8];
            int evenSum = d[1] + d[3] + d[5] + d[7];
            int digit10 = ((oddSum * 7) - evenSum) % 10;
            if (digit10 < 0) digit10 += 10;
            if (digit10 != d[9]) return false;

            int sumFirst10 = 0;
            for (int i = 0; i < 10; i++) {
                sumFirst10 += d[i];
            }
            int digit11 = sumFirst10 % 10;
            return digit11 == d[10];
        } catch (Exception e) {
            return false;
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

    private String maskValue(TemplateSegment segment, String text) {
        boolean hasIdLikeRule = segment.getRules().stream().anyMatch(r ->
                r.getRuleType() == SegmentRuleType.TC_KIMLIK_NO
                        || r.getRuleType() == SegmentRuleType.VKN
                        || r.getRuleType() == SegmentRuleType.PHONE_TR);
        if (hasIdLikeRule) {
            return maskKeepLast(text, 2);
        }
        boolean hasLettersOnlyRule = segment.getRules().stream()
                .anyMatch(r -> r.getRuleType() == SegmentRuleType.LETTERS_ONLY);
        if (hasLettersOnlyRule) {
            return maskName(text);
        }
        return maskKeepLast(text, 0);
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

    private String toJson(List<Map<String, Object>> segmentEntries) {
        try {
            return jsonMapper.writeValueAsString(segmentEntries);
        } catch (JacksonException e) {
            log.warn("Segment sonuclari serialize edilemedi, null olarak donuluyor", e);
            return null;
        }
    }
}