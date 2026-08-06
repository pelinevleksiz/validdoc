package com.validdoc.testdata;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Madde 21 — sentetik doğruluk kampanyasının koşucusu.
 * Bu bir JUnit testi DEĞİL, elle çalıştırılan bir araçtır. Docker Compose ile
 * ayağa kalkmış GERÇEK bir backend'e (varsayılan http://localhost:8080) karşı
 * çalışır. IntelliJ'de main() metodunun solundaki calistir okuna tikla.
 *
 * Ön koşul: `docker compose up --build` çalışıyor olmalı. target/synthetic-documents
 * yoksa bu araç SyntheticDocumentGenerator'ı kendisi önce çalıştırır.
 */
public final class AccuracyCampaignRunner {

    private static final String API_BASE_URL = System.getenv().getOrDefault("API_BASE_URL", "http://localhost:8080");
    private static final Path SYNTHETIC_ROOT = Path.of("target/synthetic-documents");
    private static final Path RESULTS_ROOT = Path.of("target/campaign-results");
    private static final String RUN_ID = String.valueOf(System.currentTimeMillis());

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    private String adminToken;
    private String currentBoundary;

    public static void main(String[] args) throws Exception {
        new AccuracyCampaignRunner().run();
    }

    private record DocumentExpectation(String templateName, String variant, String quality, String fileName,
                                       String expectedDocumentStatus, List<SegmentExpectation> segments) {}

    private record SegmentExpectation(String label, String ruleType, String writtenValue, String expectedOutcome) {}

    private record SegmentComparison(String label, String expectedOutcome, String actualOutcome, String verdict,
                                     Double ocrConfidence, String pendingReason) {}

    private record DocumentResult(DocumentExpectation expectation, String actualDocumentStatus,
                                  String documentVerdict, List<SegmentComparison> segments) {}

    private void run() throws Exception {
        System.out.println("API taban adresi: " + API_BASE_URL);
        Files.createDirectories(RESULTS_ROOT);

        ensureSyntheticDocumentsExist();
        login();

        Map<String, Long> templateIdByFolderName = createAllTemplates();
        List<DocumentExpectation> expectations = readManifest();

        List<DocumentResult> results = new ArrayList<>();
        int index = 0;
        for (DocumentExpectation expectation : expectations) {
            index++;
            System.out.println("[" + index + "/" + expectations.size() + "] " + expectation.fileName());
            String folderKey = sanitize(expectation.templateName());
            Long templateId = templateIdByFolderName.get(folderKey);
            if (templateId == null) {
                System.out.println("  UYARI: sablon bulunamadi (anahtar=" + folderKey + "), atlaniyor.");
                continue;
            }
            try {
                results.add(processDocumentWithRetry(expectation, templateId));
            } catch (Exception e) {
                System.out.println("  HATA: " + e.getMessage());
            }
            Thread.sleep(2000);
        }

        writeResultsCsv(results);
        writeSummaryReport(results);

        System.out.println("Kampanya tamamlandi: " + RESULTS_ROOT.toAbsolutePath());
    }

    private void ensureSyntheticDocumentsExist() throws Exception {
        Path manifestPath = SYNTHETIC_ROOT.resolve("manifest.csv");
        if (Files.exists(manifestPath)) {
            return;
        }
        System.out.println("target/synthetic-documents bulunamadi, SyntheticDocumentGenerator once calistiriliyor...");
        SyntheticDocumentGenerator.main(new String[0]);
        if (!Files.exists(manifestPath)) {
            throw new IllegalStateException(
                    "SyntheticDocumentGenerator calisti ama manifest.csv hala yok, bir sorun var.");
        }
        System.out.println("Sentetik belgeler uretildi, kampanyaya devam ediliyor.");
    }

    private void login() throws IOException, InterruptedException {
        String username = readCredential("BOOTSTRAP_ADMIN_USERNAME", "admin");
        String password = readCredential("BOOTSTRAP_ADMIN_PASSWORD", null);
        if (password == null) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD bulunamadi (ne ortam degiskeninde ne .env dosyasinda). "
                            + "Once '. .\\load-env.ps1' calistir ya da proje kokunde .env dosyasinin oldugundan emin ol.");
        }

        String body = "{\"username\":\"" + escapeJson(username) + "\",\"password\":\"" + escapeJson(password) + "\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + "/api/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Giris basarisiz, HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode json = jsonMapper.readTree(response.body());
        adminToken = json.get("token").asText();
        System.out.println("Admin girisi basarili.");
    }

    private String readCredential(String key, String fallbackDefault) {
        String fromEnv = System.getenv(key);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        Path envFile = Path.of(".env");
        if (Files.exists(envFile)) {
            try {
                for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty() || trimmed.startsWith("#") || !trimmed.contains("=")) {
                        continue;
                    }
                    String[] parts = trimmed.split("=", 2);
                    if (parts[0].trim().equals(key)) {
                        return parts[1].trim();
                    }
                }
            } catch (IOException e) {
                System.out.println("UYARI: .env okunamadi: " + e.getMessage());
            }
        }
        return fallbackDefault;
    }

    /**
     * Her sablon klasoru icin template.json'i gercek POST /api/templates ile olusturur.
     * Donen haritanin anahtari: KLASOR ADI (sanitize edilmis, orn. "kimlik-formu") —
     * bu, hem template.json'in hem manifest.csv'nin geldigi ortak, degismeyen kimlik.
     * template.json'daki "name" alani "(sentetik)" ekiyle bitiyor, manifest.csv'deki
     * templateName sutunu ise eksiz duz isim — ikisi de birbiriyle DOGRUDAN eslesmiyor,
     * bu yuzden klasor adi ortak referans noktasi olarak kullaniliyor.
     */
    private Map<String, Long> createAllTemplates() throws IOException, InterruptedException {
        Map<String, Long> result = new LinkedHashMap<>();
        if (!Files.isDirectory(SYNTHETIC_ROOT)) {
            throw new IllegalStateException(
                    "target/synthetic-documents bulunamadi. Once SyntheticDocumentGenerator'i calistir.");
        }
        try (var dirs = Files.list(SYNTHETIC_ROOT)) {
            for (Path templateDir : dirs.filter(Files::isDirectory).toList()) {
                Path templateJsonPath = templateDir.resolve("template.json");
                if (!Files.exists(templateJsonPath)) {
                    continue;
                }
                String folderKey = templateDir.getFileName().toString();

                String rawJson = Files.readString(templateJsonPath, StandardCharsets.UTF_8);
                JsonNode parsed = jsonMapper.readTree(rawJson);
                String templateJsonName = parsed.get("name").asText();
                String uniqueName = templateJsonName + " " + RUN_ID;
                String requestBody = rawJson.replaceFirst(
                        "\"name\"\\s*:\\s*\"" + Pattern.quote(templateJsonName) + "\"",
                        "\"name\": \"" + escapeJson(uniqueName) + "\"");

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_BASE_URL + "/api/templates"))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + adminToken)
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 201) {
                    System.out.println("UYARI: sablon olusturulamadi (" + templateJsonName + "), HTTP "
                            + response.statusCode() + ": " + response.body());
                    continue;
                }
                Long id = jsonMapper.readTree(response.body()).get("id").asLong();
                result.put(folderKey, id);
                System.out.println("Sablon olusturuldu: " + templateJsonName
                        + " (id=" + id + ", manifest-anahtari=" + folderKey + ")");
            }
        }
        return result;
    }

    private List<DocumentExpectation> readManifest() throws IOException {
        Path manifestPath = SYNTHETIC_ROOT.resolve("manifest.csv");
        List<String> lines = Files.readAllLines(manifestPath, StandardCharsets.UTF_8);

        Map<String, List<SegmentExpectation>> segmentsByKey = new LinkedHashMap<>();
        Map<String, String[]> headerByKey = new LinkedHashMap<>();

        for (int i = 1; i < lines.size(); i++) {
            String[] cols = parseCsvLine(lines.get(i));
            if (cols.length < 9) {
                continue;
            }
            String templateName = cols[0];
            String variant = cols[1];
            String quality = cols[2];
            String fileName = cols[3];
            String expectedDocumentStatus = cols[4];
            String segmentLabel = cols[5];
            String ruleType = cols[6];
            String writtenValue = cols[7];
            String expectedSegmentOutcome = cols[8];

            String key = templateName + "|" + variant + "|" + quality + "|" + fileName;
            segmentsByKey.computeIfAbsent(key, k -> new ArrayList<>())
                    .add(new SegmentExpectation(segmentLabel, ruleType, writtenValue, expectedSegmentOutcome));
            headerByKey.put(key, new String[]{templateName, variant, quality, fileName, expectedDocumentStatus});
        }

        List<DocumentExpectation> result = new ArrayList<>();
        for (var entry : headerByKey.entrySet()) {
            String[] h = entry.getValue();
            result.add(new DocumentExpectation(h[0], h[1], h[2], h[3], h[4], segmentsByKey.get(entry.getKey())));
        }
        return result;
    }

    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else if (c == '"') {
                    inQuotes = false;
                } else {
                    current.append(c);
                }
            } else {
                if (c == '"') {
                    inQuotes = true;
                } else if (c == ',') {
                    fields.add(current.toString());
                    current.setLength(0);
                } else {
                    current.append(c);
                }
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    private DocumentResult processDocument(DocumentExpectation expectation, Long templateId)
            throws IOException, InterruptedException {
        Path templateDir = SYNTHETIC_ROOT.resolve(sanitize(expectation.templateName()));
        Path filePath = templateDir.resolve(expectation.fileName());
        if (!Files.exists(filePath)) {
            throw new IllegalStateException("Dosya bulunamadi: " + filePath);
        }

        String contentType = contentTypeFor(expectation.fileName());
        byte[] multipartBody = buildMultipartBody("file", filePath, contentType,
                Map.of("templateId", String.valueOf(templateId)));
        String boundary = currentBoundary;

        HttpRequest uploadRequest = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE_URL + "/api/documents/upload"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody))
                .build();
        HttpResponse<String> uploadResponse = httpClient.send(uploadRequest, HttpResponse.BodyHandlers.ofString());
        if (uploadResponse.statusCode() == 429) {
            throw new RateLimitedException("Yukleme reddedildi, HTTP 429: " + uploadResponse.body());
        }
        if (uploadResponse.statusCode() != 202) {
            throw new IllegalStateException("Yukleme reddedildi, HTTP " + uploadResponse.statusCode()
                    + ": " + uploadResponse.body());
        }
        long documentId = jsonMapper.readTree(uploadResponse.body()).get("id").asLong();

        JsonNode finalDocument = pollForFinalStatus(documentId);
        String actualStatus = finalDocument.get("status").asText();

        Map<String, String> actualOutcomeByLabel = new LinkedHashMap<>();
        JsonNode segmentResultsNode = finalDocument.get("segmentResults");
        if (segmentResultsNode != null && !segmentResultsNode.isNull()) {
            JsonNode segmentsArrayForOutcome = jsonMapper.readTree(segmentResultsNode.asText());
            for (JsonNode segmentNode : segmentsArrayForOutcome) {
                actualOutcomeByLabel.put(segmentNode.get("label").asText(), segmentNode.get("outcome").asText());
            }
        }

        Map<String, JsonNode> segmentNodeByLabel = new LinkedHashMap<>();
        if (segmentResultsNode != null && !segmentResultsNode.isNull()) {
            JsonNode segmentsArray = jsonMapper.readTree(segmentResultsNode.asText());
            for (JsonNode segmentNode : segmentsArray) {
                segmentNodeByLabel.put(segmentNode.get("label").asText(), segmentNode);
            }
        }

        List<SegmentComparison> comparisons = new ArrayList<>();
        for (SegmentExpectation segmentExpectation : expectation.segments()) {
            String actualOutcome = actualOutcomeByLabel.getOrDefault(segmentExpectation.label(), "(sonuc yok)");
            String verdict = verdictFor(segmentExpectation.expectedOutcome(), actualOutcome);

            JsonNode segmentNode = segmentNodeByLabel.get(segmentExpectation.label());
            Double ocrConfidence = null;
            String pendingReason = null;
            if (segmentNode != null) {
                JsonNode confidenceNode = segmentNode.get("ocrConfidence");
                if (confidenceNode != null && !confidenceNode.isNull()) {
                    ocrConfidence = confidenceNode.asDouble();
                }
                JsonNode reasonNode = segmentNode.get("reason");
                if (reasonNode != null && !reasonNode.isNull()) {
                    pendingReason = reasonNode.asText();
                }
            }

            comparisons.add(new SegmentComparison(segmentExpectation.label(), segmentExpectation.expectedOutcome(),
                    actualOutcome, verdict, ocrConfidence, pendingReason));
        }
        String documentVerdict = verdictFor(expectation.expectedDocumentStatus(), actualStatus);
        return new DocumentResult(expectation, actualStatus, documentVerdict, comparisons);
    }

    private DocumentResult processDocumentWithRetry(DocumentExpectation expectation, Long templateId)
            throws IOException, InterruptedException {
        int maxAttempts = 5;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return processDocument(expectation, templateId);
            } catch (RateLimitedException e) {
                if (attempt == maxAttempts) {
                    throw e;
                }
                System.out.println("  429 alindi, 65 saniye beklenip tekrar denenecek (" + attempt + "/" + maxAttempts + ")");
                Thread.sleep(65_000);
            }
        }
        throw new IllegalStateException("Beklenmeyen durum: retry dongusu sonuca ulasmadan bitti");
    }

    private static final class RateLimitedException extends RuntimeException {
        RateLimitedException(String message) {
            super(message);
        }
    }

    private String verdictFor(String expected, String actual) {
        if (actual.equals(expected)) {
            return "MATCH";
        }
        if ("PENDING_REVIEW".equals(actual)) {
            return "NEEDS_REVIEW";
        }
        return "MISMATCH";
    }

    private JsonNode pollForFinalStatus(long documentId) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < 30; attempt++) {
            Thread.sleep(500);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL + "/api/documents/" + documentId))
                    .header("Authorization", "Bearer " + adminToken)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = jsonMapper.readTree(response.body());
            String status = json.get("status").asText();
            if (!"PROCESSING".equals(status)) {
                return json;
            }
        }
        throw new IllegalStateException("Belge " + documentId + " zaman asimi icinde PROCESSING durumundan cikmadi");
    }

    private byte[] buildMultipartBody(String fieldName, Path filePath, String contentType,
                                      Map<String, String> formFields) throws IOException {
        currentBoundary = "----ValiddocCampaign" + System.nanoTime();
        var baos = new java.io.ByteArrayOutputStream();

        for (var entry : formFields.entrySet()) {
            writeMultipartField(baos, currentBoundary, entry.getKey(), entry.getValue());
        }

        String header = "--" + currentBoundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\""
                + filePath.getFileName() + "\"\r\n"
                + "Content-Type: " + contentType + "\r\n\r\n";
        baos.write(header.getBytes(StandardCharsets.UTF_8));
        baos.write(Files.readAllBytes(filePath));
        baos.write(("\r\n--" + currentBoundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return baos.toByteArray();
    }

    private void writeMultipartField(java.io.ByteArrayOutputStream baos, String boundary, String name, String value)
            throws IOException {
        String field = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n";
        baos.write(field.getBytes(StandardCharsets.UTF_8));
    }

    private String contentTypeFor(String fileName) {
        if (fileName.endsWith(".png")) return "image/png";
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) return "image/jpeg";
        if (fileName.endsWith(".pdf")) return "application/pdf";
        return "application/octet-stream";
    }

    private String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT)
                .replace("ı", "i").replace("ğ", "g").replace("ü", "u")
                .replace("ş", "s").replace("ö", "o").replace("ç", "c")
                .replaceAll("[^a-z0-9]+", "-");
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void writeResultsCsv(List<DocumentResult> results) throws IOException {
        try (Writer writer = Files.newBufferedWriter(RESULTS_ROOT.resolve("campaign-results.csv"), StandardCharsets.UTF_8)) {
            writer.write("templateName,variant,quality,fileName,expectedDocumentStatus,actualDocumentStatus,documentVerdict,"
                    + "segmentLabel,expectedSegmentOutcome,actualSegmentOutcome,segmentVerdict,ocrConfidence,pendingReason\n");
            for (DocumentResult result : results) {
                DocumentExpectation e = result.expectation();
                for (SegmentComparison segment : result.segments()) {
                    writer.write(String.join(",", csvSafe(e.templateName()), csvSafe(e.variant()), csvSafe(e.quality()),
                            csvSafe(e.fileName()), csvSafe(e.expectedDocumentStatus()), csvSafe(result.actualDocumentStatus()),
                            csvSafe(result.documentVerdict()), csvSafe(segment.label()), csvSafe(segment.expectedOutcome()),
                            csvSafe(segment.actualOutcome()), csvSafe(segment.verdict()),
                            csvSafe(segment.ocrConfidence() == null ? "" : String.valueOf(segment.ocrConfidence())),
                            csvSafe(segment.pendingReason())));
                    writer.write("\n");
                }
            }
        }
    }

    private String csvSafe(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private void writeSummaryReport(List<DocumentResult> results) throws IOException {
        Map<String, int[]> bySegmentQuality = new LinkedHashMap<>();
        Map<String, int[]> byDocumentQuality = new LinkedHashMap<>();
        Map<String, List<Double>> pendingConfidenceByQuality = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> pendingReasonCountByQuality = new LinkedHashMap<>();

        for (DocumentResult result : results) {
            String quality = result.expectation().quality();
            byDocumentQuality.computeIfAbsent(quality, k -> new int[3]);
            incrementVerdict(byDocumentQuality.get(quality), result.documentVerdict());

            for (SegmentComparison segment : result.segments()) {
                bySegmentQuality.computeIfAbsent(quality, k -> new int[3]);
                incrementVerdict(bySegmentQuality.get(quality), segment.verdict());

                if ("NEEDS_REVIEW".equals(segment.verdict())) {
                    if (segment.ocrConfidence() != null) {
                        pendingConfidenceByQuality.computeIfAbsent(quality, k -> new ArrayList<>()).add(segment.ocrConfidence());
                    }
                    String reasonBucket = bucketReason(segment.pendingReason());
                    pendingReasonCountByQuality.computeIfAbsent(quality, k -> new LinkedHashMap<>())
                            .merge(reasonBucket, 1, Integer::sum);
                }
            }
        }

        StringBuilder report = new StringBuilder();
        report.append("# Accuracy Campaign Results\n\n");
        report.append("Run ID: ").append(RUN_ID).append("\n\n");
        report.append("OCR confidence threshold in effect: 60 (see `validation.ocr-confidence-threshold` in application.properties)\n\n");
        report.append("## Document-level status accuracy by quality\n\n");
        report.append("| Quality | Match | Mismatch | Needs Review | Total | Match % |\n");
        report.append("|---|---|---|---|---|---|\n");
        appendVerdictTable(report, byDocumentQuality);

        report.append("\n## Segment-level outcome accuracy by quality\n\n");
        report.append("| Quality | Match | Mismatch | Needs Review | Total | Match % |\n");
        report.append("|---|---|---|---|---|---|\n");
        appendVerdictTable(report, bySegmentQuality);

        report.append("\n## OCR confidence distribution for NEEDS_REVIEW segments, by quality\n\n");
        report.append("(Only segments that actually returned an OCR confidence value — ink segments have none.)\n\n");
        report.append("| Quality | Count | Min | Max | Average |\n");
        report.append("|---|---|---|---|---|\n");
        for (var entry : pendingConfidenceByQuality.entrySet()) {
            List<Double> values = entry.getValue();
            double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0);
            double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0);
            double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            report.append(String.format("| %s | %d | %.1f | %.1f | %.1f |%n", entry.getKey(), values.size(), min, max, avg));
        }

        report.append("\n## Why segments went to NEEDS_REVIEW, by quality\n\n");
        report.append("| Quality | Reason | Count |\n");
        report.append("|---|---|---|\n");
        for (var qualityEntry : pendingReasonCountByQuality.entrySet()) {
            for (var reasonEntry : qualityEntry.getValue().entrySet()) {
                report.append(String.format("| %s | %s | %d |%n", qualityEntry.getKey(), reasonEntry.getKey(), reasonEntry.getValue()));
            }
        }

        report.append("\nSee `campaign-results.csv` in the same folder for the full per-segment breakdown.\n");

        Files.writeString(RESULTS_ROOT.resolve("campaign-summary.md"), report.toString(), StandardCharsets.UTF_8);
    }

    private String bucketReason(String rawReason) {
        if (rawReason == null) {
            return "(sebep kaydedilmemis / ink segment)";
        }
        if (rawReason.contains("düşük güvenle") || rawReason.contains("dusuk guvenle")) {
            return "OCR dusuk guvenle okudu";
        }
        if (rawReason.contains("görünür içerik") || rawReason.contains("gorunur icerik")) {
            return "Metin okunamadi ama gorunur icerik var";
        }
        return "Diger: " + rawReason;
    }

    private void incrementVerdict(int[] counts, String verdict) {
        switch (verdict) {
            case "MATCH" -> counts[0]++;
            case "MISMATCH" -> counts[1]++;
            case "NEEDS_REVIEW" -> counts[2]++;
            default -> {}
        }
    }

    private void appendVerdictTable(StringBuilder report, Map<String, int[]> byQuality) {
        for (var entry : byQuality.entrySet()) {
            int[] counts = entry.getValue();
            int total = counts[0] + counts[1] + counts[2];
            double matchPct = total == 0 ? 0.0 : (100.0 * counts[0] / total);
            report.append(String.format("| %s | %d | %d | %d | %d | %.1f%% |%n",
                    entry.getKey(), counts[0], counts[1], counts[2], total, matchPct));
        }
    }
}