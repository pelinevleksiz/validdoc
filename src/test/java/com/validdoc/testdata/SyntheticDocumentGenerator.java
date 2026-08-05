package com.validdoc.testdata;

import com.validdoc.config.DocumentGeometry;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Manuel test kampanyası (Madde 19-21) için sentetik şablon+belge üreticisi.
 * Bu bir JUnit testi DEĞİL, elle çalıştırılan bir araçtır — normal `mvn test`
 * sırasında hiç tetiklenmez, Maven onu otomatik olarak paketten dışlar
 * (src/test/java altında olduğu için). IntelliJ'de main() metodunun
 * solundaki calistir okuna tikla.
 */
public final class SyntheticDocumentGenerator {

    private static final Path OUTPUT_ROOT = Path.of("target/synthetic-documents");
    private static final Random RANDOM = new Random(42);

    public static void main(String[] args) throws Exception {
        new SyntheticDocumentGenerator().generate();
    }

    private enum Quality { CLEAN, MEDIUM, BAD }

    private enum Variant { ALL_VALID, ALL_INVALID, ALL_EMPTY, MIXED }

    private record SegmentSpec(String label, int page, double xFrac, double yFrac, double wFrac, double hFrac,
                               String ruleType, Integer param) {
        boolean isInk() {
            return ruleType.equals("SIGNATURE_INK") || ruleType.equals("STAMP_INK");
        }
    }

    private record TemplateDef(String name, int pageCount, List<SegmentSpec> segments) {}

    private record RuleValues(String validText, String invalidText) {}

    private void generate() throws Exception {
        Files.createDirectories(OUTPUT_ROOT);
        List<TemplateDef> templates = buildTemplateDefinitions();
        List<String[]> manifestRows = new ArrayList<>();
        manifestRows.add(new String[]{
                "templateName", "variant", "quality", "fileName", "expectedDocumentStatus",
                "segmentLabel", "ruleType", "writtenValue", "expectedSegmentOutcome"
        });

        for (TemplateDef template : templates) {
            writeTemplateJson(template);
            for (Variant variant : Variant.values()) {
                for (Quality quality : Quality.values()) {
                    generateOne(template, variant, quality, manifestRows);
                }
            }
        }

        writeManifest(manifestRows);
        writeReadme();

        System.out.println("Uretim tamamlandi: " + OUTPUT_ROOT.toAbsolutePath());
    }

    private List<TemplateDef> buildTemplateDefinitions() {
        List<TemplateDef> defs = new ArrayList<>();

        defs.add(new TemplateDef("Kimlik Formu", 1, List.of(
                new SegmentSpec("Ad Soyad", 1, 0.08, 0.06, 0.55, 0.05, "LETTERS_ONLY", null),
                new SegmentSpec("TC Kimlik No", 1, 0.08, 0.14, 0.35, 0.05, "TC_KIMLIK_NO", null),
                new SegmentSpec("Dogum Tarihi", 1, 0.08, 0.22, 0.30, 0.05, "DATE", null),
                new SegmentSpec("Telefon", 1, 0.08, 0.30, 0.35, 0.05, "PHONE", null),
                new SegmentSpec("E-posta", 1, 0.08, 0.38, 0.45, 0.05, "EMAIL", null),
                new SegmentSpec("Oda No", 1, 0.08, 0.46, 0.20, 0.05, "ALPHANUMERIC", null),
                new SegmentSpec("Aciklama", 1, 0.08, 0.54, 0.60, 0.05, "MIN_LENGTH", 5),
                new SegmentSpec("Kisa Kod", 1, 0.08, 0.62, 0.20, 0.05, "MAX_LENGTH", 5),
                new SegmentSpec("Miktar", 1, 0.08, 0.70, 0.25, 0.05, "DIGITS_ONLY", null),
                new SegmentSpec("Imza", 1, 0.08, 0.80, 0.25, 0.10, "SIGNATURE_INK", null)
        )));

        defs.add(new TemplateDef("Fatura Formu (Kase)", 1, List.of(
                new SegmentSpec("VKN", 1, 0.08, 0.08, 0.35, 0.05, "VKN", null),
                new SegmentSpec("Firma Adi", 1, 0.08, 0.16, 0.55, 0.05, "LETTERS_ONLY", null),
                new SegmentSpec("Fatura Tarihi", 1, 0.08, 0.24, 0.30, 0.05, "DATE", null),
                new SegmentSpec("Tutar", 1, 0.08, 0.32, 0.25, 0.05, "DIGITS_ONLY", null),
                new SegmentSpec("Kase", 1, 0.08, 0.45, 0.30, 0.12, "STAMP_INK", null)
        )));

        defs.add(new TemplateDef("Cok Sayfali Basvuru", 2, List.of(
                new SegmentSpec("Ad Soyad", 1, 0.08, 0.06, 0.55, 0.05, "LETTERS_ONLY", null),
                new SegmentSpec("TC Kimlik No", 1, 0.08, 0.14, 0.35, 0.05, "TC_KIMLIK_NO", null),
                new SegmentSpec("Telefon", 1, 0.08, 0.22, 0.35, 0.05, "PHONE", null),
                new SegmentSpec("Onay Kodu", 2, 0.08, 0.08, 0.25, 0.05, "ALPHANUMERIC", null),
                new SegmentSpec("Imza", 2, 0.08, 0.20, 0.25, 0.10, "SIGNATURE_INK", null)
        )));

        defs.add(new TemplateDef("Karma Kucuk Form", 1, List.of(
                new SegmentSpec("E-posta", 1, 0.08, 0.08, 0.45, 0.05, "EMAIL", null),
                new SegmentSpec("Telefon", 1, 0.08, 0.16, 0.35, 0.05, "PHONE", null),
                new SegmentSpec("Imza", 1, 0.08, 0.30, 0.25, 0.10, "SIGNATURE_INK", null)
        )));

        return defs;
    }

    private void generateOne(TemplateDef template, Variant variant, Quality quality, List<String[]> manifestRows)
            throws IOException {
        int width = DocumentGeometry.A4_WIDTH_PX_INT;
        int height = DocumentGeometry.A4_HEIGHT_PX_INT;

        BufferedImage[] pages = new BufferedImage[template.pageCount()];
        for (int p = 0; p < pages.length; p++) {
            pages[p] = blankPage(width, height);
        }

        int segmentIndex = 0;
        for (SegmentSpec segment : template.segments()) {
            BufferedImage page = pages[segment.page() - 1];
            Rectangle box = toPixelBox(segment, width, height);

            String chosenOutcome;
            String writtenValue;

            boolean segmentIsValid = switch (variant) {
                case ALL_VALID -> true;
                case ALL_INVALID, ALL_EMPTY -> false;
                case MIXED -> segmentIndex % 2 == 0;
            };

            if (variant == Variant.ALL_EMPTY) {
                writtenValue = "";
                chosenOutcome = "EMPTY";
            } else if (segment.isInk()) {
                if (segmentIsValid) {
                    drawInkBlob(page, box);
                    writtenValue = "(murekkep)";
                    chosenOutcome = "FILLED_VALID";
                } else {
                    writtenValue = "";
                    chosenOutcome = "EMPTY";
                }
            } else {
                RuleValues values = ruleValues(segment.ruleType(), segment.param());
                writtenValue = segmentIsValid ? values.validText() : values.invalidText();
                drawText(page, box, writtenValue, quality);
                chosenOutcome = segmentIsValid ? "FILLED_VALID" : "FILLED_INVALID";
            }

            manifestRows.add(new String[]{
                    template.name(), variant.name(), quality.name(),
                    fileBaseName(template, variant, quality) + extensionFor(template, quality),
                    expectedDocumentStatus(variant), segment.label(), segment.ruleType(), writtenValue, chosenOutcome
            });
            segmentIndex++;
        }

        for (int p = 0; p < pages.length; p++) {
            pages[p] = degrade(pages[p], quality);
        }

        Path outDir = OUTPUT_ROOT.resolve(sanitize(template.name()));
        Files.createDirectories(outDir);
        String baseName = fileBaseName(template, variant, quality);

        if (template.pageCount() > 1) {
            savePdf(pages, outDir.resolve(baseName + ".pdf"), quality);
        } else if (quality == Quality.CLEAN) {
            saveImagePng(pages[0], outDir.resolve(baseName + ".png"));
        } else {
            saveImageJpeg(pages[0], outDir.resolve(baseName + ".jpg"), jpegQualityFor(quality));
        }
    }

    private RuleValues ruleValues(String ruleType, Integer param) {
        return switch (ruleType) {
            case "LETTERS_ONLY" -> new RuleValues("Mehmet Yilmaz", "Mehmet123");
            case "DIGITS_ONLY" -> new RuleValues("123456", "12a456");
            case "ALPHANUMERIC" -> new RuleValues("Oda42", "Oda-42");
            case "DATE" -> new RuleValues("15/06/2024", "2024-06-15");
            case "TC_KIMLIK_NO" -> new RuleValues("10562272296", "11111111111");
            case "VKN" -> new RuleValues("1234567890", "1234567891");
            case "PHONE" -> new RuleValues("+905321234567", "123");
            case "EMAIL" -> new RuleValues("test@example.com", "not-an-email");
            case "MIN_LENGTH" -> new RuleValues(
                    "x".repeat(param + 5),
                    param > 0 ? "x".repeat(param - 1) : ""
            );
            case "MAX_LENGTH" -> new RuleValues(
                    "x".repeat(param),
                    "x".repeat(param + 5)
            );
            default -> throw new IllegalArgumentException("Bilinmeyen kural: " + ruleType);
        };
    }

    private BufferedImage blankPage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        g.dispose();
        return image;
    }

    private Rectangle toPixelBox(SegmentSpec segment, int width, int height) {
        int x = (int) (segment.xFrac() * width);
        int y = (int) (segment.yFrac() * height);
        int w = (int) (segment.wFrac() * width);
        int h = (int) (segment.hFrac() * height);
        return new Rectangle(x, y, w, h);
    }

    private static final String HANDWRITING_FONT_MEDIUM = "Segoe Print";
    private static final String HANDWRITING_FONT_BAD = "Segoe Script";

    private String resolveFontFamily(Quality quality) {
        if (quality == Quality.CLEAN) {
            return "SansSerif";
        }
        String preferred = quality == Quality.MEDIUM ? HANDWRITING_FONT_MEDIUM : HANDWRITING_FONT_BAD;
        List<String> available = List.of(GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames());
        if (available.contains(preferred)) {
            return preferred;
        }
        System.out.println("UYARI: '" + preferred + "' fontu sistemde bulunamadi, SansSerif'e dusuluyor. "
                + "Bu calistirmada el yazisi benzetimi devre disi.");
        return "SansSerif";
    }

    private void drawText(BufferedImage page, Rectangle box, String text, Quality quality) {
        if (text.isEmpty()) {
            return;
        }
        Graphics2D g = page.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        int fontSize = Math.max(14, (int) (box.height * 0.6));
        g.setFont(new Font(resolveFontFamily(quality), Font.PLAIN, fontSize));
        FontMetrics metrics = g.getFontMetrics();
        int baselineY = box.y + (box.height + metrics.getAscent()) / 2 - metrics.getDescent() / 2;

        if (quality == Quality.CLEAN) {
            g.drawString(text, box.x + 4, baselineY);
        } else {
            int jitterRange = quality == Quality.MEDIUM ? 2 : 5;
            int penX = box.x + 4;
            for (char c : text.toCharArray()) {
                int jitterY = RANDOM.nextInt(jitterRange * 2 + 1) - jitterRange;
                g.drawString(String.valueOf(c), penX, baselineY + jitterY);
                penX += metrics.charWidth(c);
            }
        }
        g.dispose();
    }

    private void drawInkBlob(BufferedImage page, Rectangle box) {
        Graphics2D g = page.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke(Math.max(3f, box.height * 0.06f)));
        int midY = box.y + box.height / 2;
        int steps = 6;
        int prevX = box.x + 5;
        int prevY = midY;
        for (int i = 1; i <= steps; i++) {
            int nx = box.x + (int) ((double) box.width * i / steps) - 5;
            int ny = midY + (RANDOM.nextInt(box.height / 2) - box.height / 4);
            g.drawLine(prevX, prevY, nx, ny);
            prevX = nx;
            prevY = ny;
        }
        g.dispose();
    }

    private BufferedImage degrade(BufferedImage source, Quality quality) {
        if (quality == Quality.CLEAN) {
            return source;
        }
        BufferedImage result = source;
        double angleDegrees = quality == Quality.MEDIUM ? 1.0 : 2.5;
        result = rotate(result, angleDegrees);
        result = addVignette(result, quality == Quality.MEDIUM ? 0.08 : 0.18);
        result = addRowBanding(result, quality == Quality.MEDIUM ? 4 : 10);
        result = blur(result, quality == Quality.MEDIUM ? 3 : 5);
        result = addNoise(result, quality == Quality.MEDIUM ? 10 : 25);
        return result;
    }

    private BufferedImage rotate(BufferedImage source, double angleDegrees) {
        double radians = Math.toRadians(angleDegrees);
        AffineTransform transform = AffineTransform.getRotateInstance(radians, source.getWidth() / 2.0, source.getHeight() / 2.0);
        AffineTransformOp op = new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR);
        BufferedImage rotated = new BufferedImage(source.getWidth(), source.getHeight(), source.getType());
        Graphics2D g = rotated.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, rotated.getWidth(), rotated.getHeight());
        g.dispose();
        return op.filter(source, rotated);
    }

    private BufferedImage addVignette(BufferedImage source, double strength) {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage result = new BufferedImage(width, height, source.getType());
        double centerX = width / 2.0;
        double centerY = height / 2.0;
        double maxDist = Math.sqrt(centerX * centerX + centerY * centerY);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double dist = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2)) / maxDist;
                double darken = 1.0 - (strength * dist * dist);
                int rgb = source.getRGB(x, y);
                int r = Math.clamp((long) (((rgb >> 16) & 0xFF) * darken), 0, 255);
                int g = Math.clamp((long) (((rgb >> 8) & 0xFF) * darken), 0, 255);
                int b = Math.clamp((long) ((rgb & 0xFF) * darken), 0, 255);
                result.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return result;
    }

    private BufferedImage addRowBanding(BufferedImage source, int intensity) {
        int width = source.getWidth();
        int height = source.getHeight();
        BufferedImage result = new BufferedImage(width, height, source.getType());
        for (int y = 0; y < height; y++) {
            int rowShift = (int) (Math.sin(y * 0.05) * intensity);
            for (int x = 0; x < width; x++) {
                int rgb = source.getRGB(x, y);
                int r = Math.clamp(((rgb >> 16) & 0xFF) + rowShift, 0, 255);
                int g = Math.clamp(((rgb >> 8) & 0xFF) + rowShift, 0, 255);
                int b = Math.clamp((rgb & 0xFF) + rowShift, 0, 255);
                result.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return result;
    }

    private BufferedImage blur(BufferedImage source, int kernelSize) {
        int size = kernelSize * kernelSize;
        float weight = 1.0f / size;
        float[] data = new float[size];
        Arrays.fill(data, weight);
        ConvolveOp op = new ConvolveOp(new Kernel(kernelSize, kernelSize, data), ConvolveOp.EDGE_NO_OP, null);
        BufferedImage destination = new BufferedImage(source.getWidth(), source.getHeight(), source.getType());
        op.filter(source, destination);
        return destination;
    }

    private BufferedImage addNoise(BufferedImage source, int intensity) {
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), source.getType());
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int rgb = source.getRGB(x, y);
                int r = Math.clamp(((rgb >> 16) & 0xFF) + RANDOM.nextInt(intensity * 2 + 1) - intensity, 0, 255);
                int g = Math.clamp(((rgb >> 8) & 0xFF) + RANDOM.nextInt(intensity * 2 + 1) - intensity, 0, 255);
                int b = Math.clamp((rgb & 0xFF) + RANDOM.nextInt(intensity * 2 + 1) - intensity, 0, 255);
                result.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return result;
    }

    private void saveImagePng(BufferedImage image, Path target) throws IOException {
        ImageIO.write(image, "png", target.toFile());
    }

    private void saveImageJpeg(BufferedImage image, Path target, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(Files.newOutputStream(target))) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private void savePdf(BufferedImage[] pages, Path target, Quality quality) throws IOException {
        try (PDDocument document = new PDDocument()) {
            float widthPt = DocumentGeometry.A4_WIDTH_PX_INT * 72f / DocumentGeometry.RENDER_DPI;
            float heightPt = DocumentGeometry.A4_HEIGHT_PX_INT * 72f / DocumentGeometry.RENDER_DPI;
            PDRectangle pageSize = new PDRectangle(widthPt, heightPt);

            for (BufferedImage pageImage : pages) {
                PDPage pdPage = new PDPage(pageSize);
                document.addPage(pdPage);
                byte[] jpegBytes = null;
                if (quality != Quality.CLEAN) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    saveImageJpegToStream(pageImage, baos, jpegQualityFor(quality));
                    jpegBytes = baos.toByteArray();
                }
                org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject pdImage = jpegBytes != null
                        ? org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory.createFromByteArray(document, jpegBytes)
                        : LosslessFactory.createFromImage(document, pageImage);
                try (PDPageContentStream contentStream = new PDPageContentStream(document, pdPage)) {
                    contentStream.drawImage(pdImage, 0, 0, widthPt, heightPt);
                }
            }
            document.save(target.toFile());
        }
    }

    private void saveImageJpegToStream(BufferedImage image, ByteArrayOutputStream out, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private float jpegQualityFor(Quality quality) {
        return quality == Quality.MEDIUM ? 0.85f : 0.40f;
    }

    private String fileBaseName(TemplateDef template, Variant variant, Quality quality) {
        return sanitize(template.name()) + "_" + variant.name().toLowerCase(Locale.ROOT)
                + "_" + quality.name().toLowerCase(Locale.ROOT);
    }

    private String extensionFor(TemplateDef template, Quality quality) {
        if (template.pageCount() > 1) {
            return ".pdf";
        }
        return quality == Quality.CLEAN ? ".png" : ".jpg";
    }

    private String expectedDocumentStatus(Variant variant) {
        return switch (variant) {
            case ALL_VALID -> "VALIDATED";
            case ALL_INVALID, MIXED -> "REJECTED_INVALID";
            case ALL_EMPTY -> "REJECTED_EMPTY";
        };
    }

    private String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT)
                .replace("ı", "i").replace("ğ", "g").replace("ü", "u")
                .replace("ş", "s").replace("ö", "o").replace("ç", "c")
                .replaceAll("[^a-z0-9]+", "-");
    }

    private void writeTemplateJson(TemplateDef template) throws IOException {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"name\": \"").append(template.name()).append(" (sentetik)\",\n");
        json.append("  \"pageCount\": ").append(template.pageCount()).append(",\n");
        json.append("  \"segments\": [\n");
        int width = DocumentGeometry.A4_WIDTH_PX_INT;
        int height = DocumentGeometry.A4_HEIGHT_PX_INT;
        List<SegmentSpec> segments = template.segments();
        for (int i = 0; i < segments.size(); i++) {
            SegmentSpec s = segments.get(i);
            Rectangle box = toPixelBox(s, width, height);
            json.append("    { \"label\": \"").append(s.label()).append("\", \"page\": ").append(s.page())
                    .append(", \"x\": ").append(box.x).append(", \"y\": ").append(box.y)
                    .append(", \"w\": ").append(box.width).append(", \"h\": ").append(box.height)
                    .append(", \"rules\": [ { \"type\": \"").append(s.ruleType()).append("\"");
            if (s.param() != null) {
                json.append(", \"param\": ").append(s.param());
            }
            json.append(" } ] }");
            json.append(i < segments.size() - 1 ? ",\n" : "\n");
        }
        json.append("  ]\n}\n");

        Path outDir = OUTPUT_ROOT.resolve(sanitize(template.name()));
        Files.createDirectories(outDir);
        Files.writeString(outDir.resolve("template.json"), json.toString(), StandardCharsets.UTF_8);
    }

    private void writeManifest(List<String[]> rows) throws IOException {
        try (Writer writer = Files.newBufferedWriter(OUTPUT_ROOT.resolve("manifest.csv"), StandardCharsets.UTF_8)) {
            for (String[] row : rows) {
                writer.write(String.join(",", escapeCsv(row)));
                writer.write("\n");
            }
        }
    }

    private String[] escapeCsv(String[] row) {
        String[] escaped = new String[row.length];
        for (int i = 0; i < row.length; i++) {
            String value = row[i] == null ? "" : row[i];
            if (value.contains(",") || value.contains("\"")) {
                value = "\"" + value.replace("\"", "\"\"") + "\"";
            }
            escaped[i] = value;
        }
        return escaped;
    }

    private void writeReadme() throws IOException {
        String content = """
                Bu klasor SyntheticDocumentGenerator.generate() ile uretildi.

                - Her sablon klasorunde: temiz (.png), orta ve kotu kalite (.jpg, veya cok sayfalilarda .pdf) belgeler.
                - template.json: ilgili sablonu POST /api/templates ile birebir olusturmak icin kullan.
                - manifest.csv: her belgenin her segmenti icin YAZILAN deger ve BEKLENEN sonuc.

                ONEMLI: manifest'teki beklenen sonuc, "orta" ve "kotu" kalite seviyelerinde bir GARANTI DEGIL,
                ideal/mukemmel OCR varsayimidir. Bu seviyelerin butun amaci gercek Tesseract motorunun
                dusuk kalitede nasil davrandigini olcmek (Madde 21) - PENDING_REVIEW veya farkli bir sonuc
                cikmasi bir hata degil, olculmesi gereken gercek bir veri noktasidir.

                SINIRLAMA: "orta" ve "kotu" seviyeler Segoe Print/Segoe Script sistem fontlariyla
                el yazisi BENZETIMI yapiyor, gercek el yazisi degil - her harf hep ayni sekilde ciziliyor,
                gercek insan yazisindaki gercek degiskenlik yok. Bu sentetik set hacim ve kesin ground-truth
                sagliyor; gercekci OCR zorlugu icin Madde 21'deki yazdirilip elle doldurulmus, taranmis
                3-4 gercek belge hala gerekli, bu setin yerine gecmiyor.
                """;
        Files.writeString(OUTPUT_ROOT.resolve("README.txt"), content, StandardCharsets.UTF_8);
    }
}